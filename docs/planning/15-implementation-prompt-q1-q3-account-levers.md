# Implementation prompt — Epic Q1-Q3: Account levers (daily battle cap, character slots)

Paste this whole file to Claude Code as the task.

## Context

Crimson Sky (`docs/planning/01-system-design-combat-engine.md` §20, `docs/planning/02-user-stories.md` Epic Q). Two independent pacing levers, both "permanent additive bonus" mechanics:

- **Q1 — daily battle cap.** A character can fight at most 5 battles/day (UTC midnight boundary), extendable by a new per-character bonus column. `AttackService` must reject an attack *before* resolving combat once the cap is hit, with a real reason code back to the client — not a silent drop.
- **Q2 — character slots.** An account starts with 3 character slots. This one is **not** a new column — `accounts.max_slots` already exists (V1 migration, default 3) and is already read into `Account.maxSlots()` and sent to the client in `LoginResponse`/`CharacterListResponse`, but `CharacterService.createCharacter`'s actual enforcement is a totally independent hardcoded `>= 3` that never reads `max_slots`. This is a real, pre-existing display/enforcement mismatch (found while grounding this prompt, documented in §20's 2026-07-20 note) — fixing it is part of Q2, not a separate task.
- **Q3 — grant paths.** Both bonuses need an atomic "increment" DAO method so a future IAP/achievement/quest source can grant them. Nothing calls these methods yet — that's fine, this story only needs the capability to exist (same "build now, wire later" precedent as `ResolvedAction.itemId` and others in this expansion).

Read `docs/planning/01-system-design-combat-engine.md` §20 for the full design reasoning before starting.

**Scope note on the client:** `AttackResponse` itself has no consumer anywhere in `core/` yet (`PacketHandlerRegistry` doesn't register a handler for it, `NetworkListener` has no `onAttackResponse`) — the combat UI (M5) hasn't been built. So Q1's new rejection packet gets defined, registered in Kryo, and sent by the server, but **does not** get wired into `PacketHandlerRegistry`/`NetworkListener` — there's nothing there yet for it to plug into, and adding a lone consumer for one combat packet while its sibling has none would be inconsistent scope creep. M5 wires up all combat packet consumption together.

## Q1 — Daily battle cap

### 1. Migration

New file `server/src/main/resources/db/migration/V12__Add_Account_Progression_Levers.sql`:

```sql
ALTER TABLE characters ADD COLUMN bonus_daily_battles INTEGER NOT NULL DEFAULT 0;
```

That's the only schema change this epic needs (Q2 reuses an existing column — see below).

### 2. `BattleHistoryDao` — new query

Add to `server/src/main/java/io/github/ydhekim/crimson_sky/server/database/dao/BattleHistoryDao.java`, alongside the existing `countWins`:

```java
/**
 * How many battles (win or lose) {@code characterId} has fought since {@code since} — the daily
 * battle cap check (system design §20). Unlike {@link #countWins}, no {@code won} filter: every
 * attempt counts against the cap, not just wins.
 */
@SqlQuery("SELECT COUNT(*) FROM battle_history WHERE character_id = :characterId AND created_at > :since")
int countBattlesSince(@Bind("characterId") long characterId, @Bind("since") Instant since);
```

### 3. `CharacterDao` — new narrow read + new grant method

Add to `server/src/main/java/io/github/ydhekim/crimson_sky/server/database/dao/CharacterDao.java`, near `getUnspentStatPoints`/`getSkillPoints` (same narrow-read style — deliberately not added to the shared `Character` record, only the cap check needs it):

```java
/**
 * The daily-battle-cap bonus for a character (system design §20). Narrow read in the style of
 * {@link #getUnspentStatPoints}: only {@code AttackService}'s cap check needs it.
 */
@SqlQuery("SELECT bonus_daily_battles FROM characters WHERE id = :characterId")
Optional<Integer> getBonusDailyBattles(@Bind("characterId") long characterId);

/**
 * Grants (or revokes, with a negative {@code delta}) daily-battle-cap bonus (system design §20/Q3).
 * An atomic increment, not a read-then-write — mirrors {@link AccountDao#addGlobalCurrency}. Nothing
 * calls this yet; it exists so a future IAP/achievement/quest grant path has somewhere to write.
 */
@SqlUpdate("UPDATE characters SET bonus_daily_battles = bonus_daily_battles + :delta WHERE id = :characterId")
void addBonusDailyBattles(@Bind("characterId") long characterId, @Bind("delta") int delta);
```

`AccountDao` is already imported/visible in this package; no new import needed for the javadoc reference, or drop the `{@link}` if it doesn't resolve across files — either is fine.

### 4. `FakeCharacterDao` — must implement both new interface methods

`server/src/test/java/io/github/ydhekim/crimson_sky/server/support/FakeCharacterDao.java` implements `CharacterDao`, so it needs both new methods or the build won't compile. Neither needs a new field on the fake's `Row` record — no existing test needs a nonzero bonus:

```java
@Override
public Optional<Integer> getBonusDailyBattles(long characterId) {
    return rows.containsKey(characterId) ? Optional.of(0) : Optional.empty();
}

@Override
public void addBonusDailyBattles(long characterId, int delta) {
    throw new UnsupportedOperationException("not exercised by combat/matchmaking tests");
}
```

(Matches the fake's existing convention: real reads for things `AttackService`/`CharacterService` actually consult, `UnsupportedOperationException` for writes nothing in this test suite exercises.)

### 5. `CharacterService` — passthrough

Add to `server/src/main/java/io/github/ydhekim/crimson_sky/server/service/CharacterService.java`, in the same style as the existing `getElo` passthrough (same try/orElseGet shape — read that method first and match it exactly):

```java
/** The daily-battle-cap bonus for a character (system design §20), for AttackService's cap check. */
public ServiceResult<Integer> getBonusDailyBattles(long characterId) {
    try {
        return characterDao.getBonusDailyBattles(characterId)
            .map(bonus -> ServiceResult.success(MessageCode.SUCCESS, bonus))
            .orElseGet(() -> {
                log.info("Daily-battle-bonus lookup found no character with ID: " + characterId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            });
    } catch (Exception e) {
        // match whatever catch/log pattern getElo uses here, if any
    }
}
```

Copy `getElo`'s exact structure (including whether it has a catch block at all) rather than inventing a new shape — this file already has three of these narrow-read passthroughs, stay consistent with them.

### 6. `AttackService` — new dependency, new pre-check method

`server/src/main/java/io/github/ydhekim/crimson_sky/server/service/AttackService.java` currently has two constructors:

```java
public AttackService(CharacterService characterService, BotFactory botFactory) {
    this(characterService, botFactory, ThreadLocalRandom.current());
}

AttackService(CharacterService characterService, BotFactory botFactory, Random random) {
    ...
}
```

Both gain a `BattleHistoryDao` parameter, inserted before `Random` (so every existing call passing an explicit `Random` only needs one new argument spliced in, not a reorder):

```java
private final BattleHistoryDao battleHistoryDao;
private static final int BASE_DAILY_BATTLE_CAP = 5;

public AttackService(CharacterService characterService, BotFactory botFactory, BattleHistoryDao battleHistoryDao) {
    this(characterService, botFactory, battleHistoryDao, ThreadLocalRandom.current());
}

AttackService(CharacterService characterService, BotFactory botFactory, BattleHistoryDao battleHistoryDao, Random random) {
    this.characterService = characterService;
    this.botFactory = botFactory;
    this.battleHistoryDao = battleHistoryDao;
    this.random = random;
}
```

New method, deliberately **not** folded into `attack()`'s `Optional<AttackResult>` — `attack()`'s empty-Optional today means "attacker couldn't be loaded" or "Elo lookup failed", genuine data problems distinct from "you've hit today's limit," and `attack()`'s existing contract is depended on directly by `AttackServiceTest`. Keep it completely untouched; call this new method first instead:

```java
/**
 * How many more battles {@code characterId} may fight today (system design §20), floored at 0. Call
 * this before {@link #attack}, not as part of it — "daily cap reached" is a distinct rejection reason
 * the client must be able to tell apart from attack()'s existing failure modes, so it stays a separate
 * pre-check rather than widening attack()'s return type.
 */
public int remainingDailyBattles(long characterId) {
    int bonus = 0;
    ServiceResult<Integer> bonusResult = characterService.getBonusDailyBattles(characterId);
    if (bonusResult.success()) {
        bonus = bonusResult.data();
    }
    int battlesToday = battleHistoryDao.countBattlesSince(characterId, QuestPeriods.startOfToday());
    return Math.max(0, (BASE_DAILY_BATTLE_CAP + bonus) - battlesToday);
}
```

A failed bonus lookup falls back to bonus 0 (base cap only) rather than blocking the request — by the time this is called the handler has already confirmed the character is owned by the connection's account, so a lookup failure here would be a genuine, rare data race, not a reason to spuriously cap a legitimate player at 0.

Import `io.github.ydhekim.crimson_sky.server.quest.QuestPeriods` (already public, already documents this exact reuse in its own javadoc) and `server.database.dao.BattleHistoryDao`.

### 7. New packet: `AttackRejectedResponse`

New file `common/src/main/java/io/github/ydhekim/crimson_sky/common/network/packet/AttackRejectedResponse.java`:

```java
package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Sent instead of {@link AttackResponse} when an {@link AttackRequest} is rejected before any battle
 * resolves — currently only the daily battle cap (system design §20). {@code reason} is a
 * {@code MessageCode} name, the same convention {@link CreateCharacterResponse#message()} uses.
 */
public record AttackRejectedResponse(String reason) {
}
```

Register in `common/src/main/java/io/github/ydhekim/crimson_sky/common/network/KryoConfig.java` — **append at the very end of `register()`, after the last existing `kryo.register(...)` call** (currently `ClaimQuestResponse`). Registration is positional and must match on both sides; do not insert it near `AttackResponse` even though that's the conceptually related packet.

```java
kryo.register(AttackRejectedResponse.class, new RecordSerializer<>(AttackRejectedResponse.class));
```

### 8. `MessageCode` — new code

Add to `common/src/main/java/io/github/ydhekim/crimson_sky/common/model/MessageCode.java`, as a new trailing section:

```java
// Account levers (system design §20)
DAILY_BATTLE_CAP_REACHED
```

### 9. `AttackRequestHandler` — pre-check before `attack()`

`server/src/main/java/io/github/ydhekim/crimson_sky/server/network/handler/AttackRequestHandler.java`: insert the cap check between the existing ownership check and the `attack()` call, leaving both of those exactly as they are:

```java
if (!attackService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
    // ...unchanged...
}

int remaining = attackService.remainingDailyBattles(request.characterId());
if (remaining <= 0) {
    log.info("Rejected attack request: daily battle cap reached for character " + request.characterId()
        + " (Connection ID: " + connection.getID() + ")");
    connection.sendTCP(new AttackRejectedResponse(MessageCode.DAILY_BATTLE_CAP_REACHED.name()));
    return;
}

Optional<AttackResult> result = attackService.attack(request.characterId());
// ...unchanged from here...
```

Add the two new imports (`AttackRejectedResponse`, `MessageCode`).

### 10. `ServiceRegistry` — wire the new dependency

`server/src/main/java/io/github/ydhekim/crimson_sky/server/service/ServiceRegistry.java`:

```java
CharacterDao characterDao = dbManager.getJdbi().onDemand(CharacterDao.class);
this.characterService = new CharacterService(characterDao);
BattleHistoryDao battleHistoryDao = dbManager.getJdbi().onDemand(BattleHistoryDao.class);
this.attackService = new AttackService(characterService, new BotFactory(), battleHistoryDao);
```

`BattleHistoryDao` is already imported via the wildcard `server.database.dao.*` import at the top of this file.

### 11. Fix every `new AttackService(...)` call site

Exhaustive list — grep `new AttackService(` to confirm nothing is missed after editing:

- `AttackServiceTest.java:48` — `service()` helper:
  ```java
  private AttackService service() {
      return new AttackService(new CharacterService(characterDao), new BotFactory(new Random(42L)), new FakeBattleHistoryDao(), new Random(42L));
  }
  ```
- `AttackRequestHandlerTest.java:61` — `handler()` helper. This test already has `TestDatabase db` in scope, so use the real DAO rather than the fake:
  ```java
  new AttackService(characterService, new BotFactory(), db.jdbi().onDemand(BattleHistoryDao.class))
  ```
- `RewardServicePetHealthTest.java:117-118` and `RewardServiceDurabilityTest.java:112-113` — both already have `db` (`TestDatabase`) in scope:
  ```java
  AttackService attackService = new AttackService(
      characterService, new BotFactory(new Random(42L)), db.jdbi().onDemand(BattleHistoryDao.class), new Random(42L));
  ```
- `BattleLeavesInventoryAloneTest.java` — three separate construction sites (lines ~92, ~161-162, ~298), each with its own `db`/`shopDb`/`bonusDb` `TestDatabase` already in scope. Use that test's own db instance at each site, e.g.:
  ```java
  attackService = new AttackService(characterService, new BotFactory(new Random(42L)), db.jdbi().onDemand(BattleHistoryDao.class), new Random(42L));
  ```
  and correspondingly `shopDb.jdbi()...` / `bonusDb.jdbi()...` at the other two.

### 12. New `FakeBattleHistoryDao`

New file `server/src/test/java/io/github/ydhekim/crimson_sky/server/support/FakeBattleHistoryDao.java`, mirroring `FakeCharacterDao`'s in-memory style — needed for `AttackServiceTest`, which is deliberately headless/no-DB (per its own class doc) and must stay that way:

```java
package io.github.ydhekim.crimson_sky.server.support;

import io.github.ydhekim.crimson_sky.server.database.dao.BattleHistoryDao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link BattleHistoryDao} so AttackServiceTest can construct an AttackService without a
 * database. Empty by default (0 battles fought today), so it never blocks the existing
 * opponent-selection tests; seed it via {@link #with} to exercise the daily-cap rejection path.
 */
public class FakeBattleHistoryDao implements BattleHistoryDao {

    private record Row(long characterId, boolean won, Instant createdAt) {
    }

    private final List<Row> rows = new ArrayList<>();

    /** Seeds one past battle, for cap-rejection tests. */
    public FakeBattleHistoryDao with(long characterId, boolean won, Instant createdAt) {
        rows.add(new Row(characterId, won, createdAt));
        return this;
    }

    @Override
    public void insert(long characterId, Long opponentCharacterId, boolean opponentIsBot, boolean won,
                        int goldDelta, long expDelta, int eloDelta) {
        rows.add(new Row(characterId, won, Instant.now()));
    }

    @Override
    public int countWins(long characterId, Instant since) {
        return (int) rows.stream()
            .filter(r -> r.characterId() == characterId && r.won() && r.createdAt().isAfter(since))
            .count();
    }

    @Override
    public int countBattlesSince(long characterId, Instant since) {
        return (int) rows.stream()
            .filter(r -> r.characterId() == characterId && r.createdAt().isAfter(since))
            .count();
    }
}
```

### 13. New tests — `AttackServiceTest`

Add alongside the existing tests, using `FakeBattleHistoryDao`:

- `remainingDailyBattles` returns 5 with no battles fought today.
- After seeding 5 battles today via `FakeBattleHistoryDao.with(...)`, `remainingDailyBattles` returns 0, and `attack()` is not even reached by the handler (that's the handler-level test below) — this test just pins the service-level number.
- A battle from yesterday (`createdAt` before `QuestPeriods.startOfToday()`) does not count against today's cap.
- With `bonus_daily_battles` — since `FakeCharacterDao.getBonusDailyBattles` always returns 0, this specific bonus-arithmetic case may need a small dedicated fake/stub, or can be left to a `CharacterService`-level unit test if one already exists for narrow reads; use judgment here, it's a minor coverage nicety, not a correctness requirement — the arithmetic itself (`BASE_DAILY_BATTLE_CAP + bonus - battlesToday`) is simple enough that the zero-bonus case already exercises the formula.

### 14. New test — `AttackRequestHandlerTest`

Add one test seeding 5 prior battles today for `CHARACTER_A` (via `db.withBattleHistory(...)` if that helper already exists from Epic P, else a direct insert), then asserting:

```java
handler().handle(connection, new AttackRequest(CHARACTER_A));

AttackRejectedResponse response = connection.onlySentPacket(AttackRejectedResponse.class);
assertEquals(MessageCode.DAILY_BATTLE_CAP_REACHED.name(), response.reason());
```

Confirm `FakeGameConnection.onlySentPacket` already supports arbitrary packet types generically (it does for `AttackResponse` already) — no change needed there.

## Q2 — Character slots (reuses `accounts.max_slots`)

### 1. `CharacterService.createCharacter` — signature change, real check

`server/src/main/java/io/github/ydhekim/crimson_sky/server/service/CharacterService.java`:

```java
public ServiceResult<Long> createCharacter(long accountId, int maxSlots, Character character) {
    try {
        if (characterDao.getCharacterCount(accountId) >= maxSlots) {
            log.info("Character creation failed for account ID " + accountId + ": Maximum character slots reached.");
            return ServiceResult.failure(MessageCode.CHAR_MAX_SLOTS_REACHED);
        }
        // ...unchanged from here...
```

This has exactly one production call site and no test calls it directly (confirmed by grepping `\.createCharacter\(` across the repo — only `CreateCharacterRequestHandler.java:26` and the DAO-level `characterDao.createCharacter` inside this same method).

### 2. `CreateCharacterRequestHandler` — pass the account's real slot count

`server/src/main/java/io/github/ydhekim/crimson_sky/server/network/handler/CreateCharacterRequestHandler.java`:

```java
var result = characterService.createCharacter(connection.account.id(), connection.account.maxSlots(), request.character());
```

No new dependency injected — `connection.account` is already the full `Account` entity (same object `LoginRequestHandler`/`CharacterListRequestHandler` call `.maxSlots()` on), so `maxSlots()` is already on hand.

### 3. `AccountDao` — new grant method (Q3, but natural to add alongside Q2)

Add to `server/src/main/java/io/github/ydhekim/crimson_sky/server/database/dao/AccountDao.java`, next to `addGlobalCurrency`:

```java
/**
 * Grants (or revokes, with a negative {@code delta}) bonus character slots (system design §20/Q3) by
 * incrementing the same {@code max_slots} column the client already sees — there is no separate bonus
 * field. An atomic increment, mirroring {@link #addGlobalCurrency}. Nothing calls this yet; it exists
 * so a future IAP/achievement/quest grant path has somewhere to write.
 */
@SqlUpdate("UPDATE accounts SET max_slots = max_slots + :delta WHERE id = :accountId")
void addCharacterSlots(@Bind("accountId") long accountId, @Bind("delta") int delta);
```

## Testing

Run `gradlew.bat core:test` (combat/quest tests live in `core`'s test source set per the module layout — but the Q1/Q2 tests above are all under `server/src/test`, so also run `gradlew.bat server:test` if that task exists, or `gradlew.bat test` for everything). Confirm:

- Every existing test compiles against the new `AttackService`/`CharacterService.createCharacter`/`CharacterDao`/`BattleHistoryDao` signatures — this is a compile-breaking change at every call site enumerated in Q1 §11, so a clean build is the real signal, not just green tests.
- `BattleLeavesInventoryAloneTest` (story C2's structural guard) still passes unmodified in its assertions — only its `AttackService` construction lines change, per §11 above; it must still prove `updateInventory`/`updateLoadout` are the only two writers touching `inventory`/`loadout`. Adding `AccountDao.addCharacterSlots`/`CharacterDao.addBonusDailyBattles` does not touch either column, so this guard needs no new exception carved into it.
- New `AttackServiceTest` cases (§13) and the new `AttackRequestHandlerTest` case (§14) pass.
- A quick manual read of `CreateCharacterResponse`'s `CHAR_MAX_SLOTS_REACHED` path still fires correctly for an account genuinely at its (now dynamic) `max_slots` limit — existing tests around character creation should already cover this if any assume the old hardcoded 3; if such a test exists, confirm it now passes `maxSlots = 3` explicitly rather than relying on a hardcoded constant, since the whole point of Q2 is that this number is no longer fixed.

## Definition of done

- Migration V12 exists and adds only `characters.bonus_daily_battles`.
- `AttackService` rejects with `AttackRejectedResponse(DAILY_BATTLE_CAP_REACHED)` once a character has fought 5 + bonus battles since UTC midnight, checked before combat resolves.
- `attack()`'s own signature and `AttackResponse`'s own shape are completely unchanged.
- `CharacterService.createCharacter` enforces the account's real `max_slots` (no new column), fixing the pre-existing display/enforcement mismatch.
- `AccountDao.addCharacterSlots` and `CharacterDao.addBonusDailyBattles` exist and compile, unused by any caller yet (that's expected — Q3 is scoped to the capability existing, not wiring a grant source).
- Every enumerated `new AttackService(...)` call site (7 total) and the one `characterService.createCharacter(...)` call site compile against the new signatures.
- `gradlew.bat build` (or at minimum the relevant module's `test` task) is green.
