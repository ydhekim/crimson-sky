# Implementation prompt — Epic R1-R2: Ranked battle mode + dual Elo track

Paste this whole file to Claude Code as the task.

## Context

Crimson Sky (`docs/planning/01-system-design-combat-engine.md` §21, `docs/planning/02-user-stories.md` Epic R). This prompt covers **R1 and R2 only** — a second, opt-in Elo track (ranked), level-25-gated, matched and scored independently of normal battles. **R3 (the monthly ladder + claim reward) is deliberately left for a separate, later prompt**: it's a large enough chunk of its own (reward tiers, a claim service mirroring `QuestService`, a new migration) and it surfaces a real content gap worth its own grounding pass — §21/Epic R's own text names an "exclusive title" as part of the top-1 reward, but titles (`characters.equipped_title`) are part of §22/Epic S (achievements & character page), which hasn't been built yet. Better to let R1/R2 land first and design R3 once that's true rather than half-solve it here.

**The core idea, end to end:** `AttackRequest` gains a `BattleMode mode` (`NORMAL`/`RANKED`). A `RANKED` request is rejected (reusing Q1's `AttackRejectedResponse` packet, new reason code) unless `character.level >= 25`. Opponent matching reuses the exact same band→widen→bot algorithm `AttackService` already has, just pointed at a second, live-computed Elo number instead of the stored `characters.elo` column. Rewards use the identical Gold/Exp/skill-point formulas either way — only which Elo track moves differs. Ranked Elo is never a stored, mutable column (avoids ever having two numbers drift out of sync with `battle_history`): it's `1000 + SUM(ranked_elo_delta) FROM battle_history WHERE battle_mode = 'RANKED'`, the same "compute live from `battle_history`" rule already used for quests (Epic P) and the daily cap (Epic Q).

Read `docs/planning/01-system-design-combat-engine.md` §21 before starting.

## 1. `BattleMode` enum

New file `common/src/main/java/io/github/ydhekim/crimson_sky/common/model/BattleMode.java`:

```java
package io.github.ydhekim.crimson_sky.common.model;

/** Which Elo track and matchmaking pool a battle uses (system design §21). */
public enum BattleMode {
    NORMAL,
    RANKED
}
```

Register in `KryoConfig.register()` — **append at the very end**, after the current last line (`AttackRejectedResponse`), as a plain enum registration (no `RecordSerializer`), matching how `Faction`/`Rarity`/`ActionSource` etc. are registered:

```java
kryo.register(BattleMode.class);
```

## 2. `AttackRequest` — gains `mode`

`common/src/main/java/io/github/ydhekim/crimson_sky/common/network/packet/AttackRequest.java`:

```java
public record AttackRequest(long characterId, BattleMode mode) {
}
```

This breaks every existing construction of `AttackRequest` — see §9 below for the exhaustive list.

## 3. Migration V13

New file `server/src/main/resources/db/migration/V13__Add_Ranked_Battle_Mode.sql`:

```sql
ALTER TABLE battle_history ADD COLUMN battle_mode VARCHAR(16) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE battle_history ADD COLUMN ranked_elo_delta INTEGER;
```

Deliberately not adding `ladder_claims` here — that table belongs to the R3 prompt, alongside the service that will actually use it.

**`countWins`/`countBattlesSince` are deliberately left unfiltered by `battle_mode`.** A ranked battle is still a real battle: it counts toward the daily cap (Epic Q) and toward quest win-counts (Epic P) exactly like a normal one. Nothing in §19/§20/§21 asks for ranked battles to be excluded from either, and doing so would need its own justification this prompt doesn't have — so no change to either query.

## 4. `BattleHistoryDao` — live ranked Elo + `insert` gains two columns

`server/src/main/java/io/github/ydhekim/crimson_sky/server/database/dao/BattleHistoryDao.java`:

```java
/**
 * Live ranked Elo as of {@code asOf} (system design §21) — never a stored column, always
 * {@code 1000 + the sum of ranked-only deltas up to that instant}. The {@code asOf} bound is what lets
 * a future ladder query ask "what was this character's standing at the end of last month" with the
 * identical formula used for "what is it right now".
 */
@SqlQuery("SELECT 1000 + COALESCE(SUM(ranked_elo_delta), 0) FROM battle_history " +
    "WHERE character_id = :characterId AND battle_mode = 'RANKED' AND created_at <= :asOf")
int getRankedEloAsOf(@Bind("characterId") long characterId, @Bind("asOf") Instant asOf);

/** Live ranked Elo right now — the form {@code AttackService}'s matchmaking actually needs. */
default int getRankedElo(long characterId) {
    return getRankedEloAsOf(characterId, Instant.now());
}
```

`insert` gains `battleMode`/`rankedEloDelta`:

```java
@SqlUpdate("INSERT INTO battle_history (character_id, opponent_character_id, opponent_is_bot, won, gold_delta, experience_delta, elo_delta, battle_mode, ranked_elo_delta) " +
    "VALUES (:characterId, :opponentCharacterId, :opponentIsBot, :won, :goldDelta, :expDelta, :eloDelta, :battleMode, :rankedEloDelta)")
void insert(@Bind("characterId") long characterId,
            @Bind("opponentCharacterId") Long opponentCharacterId,
            @Bind("opponentIsBot") boolean opponentIsBot,
            @Bind("won") boolean won,
            @Bind("goldDelta") int goldDelta,
            @Bind("expDelta") long expDelta,
            @Bind("eloDelta") int eloDelta,
            @Bind("battleMode") String battleMode,
            @Bind("rankedEloDelta") Integer rankedEloDelta);
```

`battleMode` is bound as `BattleMode.name()` by the caller (`RewardService`, §7 below); `rankedEloDelta` is `null` for a `NORMAL` row, the computed delta for a `RANKED` row.

## 5. `CharacterDao` — ranked opponent-candidate queries

Add to `server/src/main/java/io/github/ydhekim/crimson_sky/server/database/dao/CharacterDao.java`, next to `findOpponentCandidatesInEloRange`/`findAllOpponentCandidates`:

```java
/**
 * Ranked opponent candidates within ±eloRange of a live-computed ranked Elo, restricted to level-25+
 * characters (system design §21). Ranked Elo isn't a stored column, so the correlated subquery computes
 * it inline per candidate — the same "compute live" rule §19/§20 already established, just inlined into
 * a WHERE clause instead of read separately.
 */
@SqlQuery("SELECT c.* FROM characters c WHERE c.id <> :characterId AND c.level >= 25 " +
    "AND (1000 + COALESCE((SELECT SUM(bh.ranked_elo_delta) FROM battle_history bh " +
    "WHERE bh.character_id = c.id AND bh.battle_mode = 'RANKED'), 0)) BETWEEN :minElo AND :maxElo")
List<CharacterEntity> findRankedOpponentCandidatesInEloRange(@Bind("characterId") long characterId,
                                                             @Bind("minElo") int minElo,
                                                             @Bind("maxElo") int maxElo);

/**
 * The unbounded ranked widening step (system design §21) — every level-25+ opponent but the requester,
 * regardless of Elo. No live-elo computation needed here, unlike the banded query above: the widen step
 * never filtered by Elo even in the normal-mode version ({@link #findAllOpponentCandidates}).
 */
@SqlQuery("SELECT * FROM characters WHERE id <> :characterId AND level >= 25")
List<CharacterEntity> findAllRankedOpponentCandidates(@Bind("characterId") long characterId);
```

### `FakeCharacterDao` — implements both, with a documented limitation

`FakeCharacterDao` has no access to `battle_history` data (it's a separate DAO's table), so it cannot compute ranked Elo. It implements the level filter honestly and is explicit about what it can't do:

```java
/**
 * The fake cannot compute ranked Elo — that lives in {@code battle_history}, a different DAO's table —
 * so this approximates by ignoring the Elo band and returning every level-25+ candidate. Real elo-band
 * filtering is verified against actual SQL in {@code CharacterDaoRankedOpponentCandidatesTest} (§10),
 * not through this fake. Tests that only care about the level-25 gate (not band-narrowing) can still use
 * this safely.
 */
@Override
public List<CharacterEntity> findRankedOpponentCandidatesInEloRange(long characterId, int minElo, int maxElo) {
    return candidates(characterId, row -> row.character().level() >= 25);
}

@Override
public List<CharacterEntity> findAllRankedOpponentCandidates(long characterId) {
    return candidates(characterId, row -> row.character().level() >= 25);
}
```

Both reuse the existing private `candidates(long, Predicate<Row>)` helper already in this file.

## 6. `CharacterService` — passthroughs

Add to `server/src/main/java/io/github/ydhekim/crimson_sky/server/service/CharacterService.java`, mirroring `findOpponentCandidates`/`findAllOpponentCandidates` exactly (same try/catch/log shape, reusing the existing private `toCharacters` helper):

```java
public ServiceResult<List<Character>> findRankedOpponentCandidates(long characterId, int elo, int eloRange) {
    try {
        return toCharacters(characterDao.findRankedOpponentCandidatesInEloRange(
            characterId, elo - eloRange, elo + eloRange));
    } catch (Exception e) {
        log.error("Ranked opponent candidate lookup failed for character ID: " + characterId, e);
        return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
    }
}

public ServiceResult<List<Character>> findAllRankedOpponentCandidates(long characterId) {
    try {
        return toCharacters(characterDao.findAllRankedOpponentCandidates(characterId));
    } catch (Exception e) {
        log.error("Unbounded ranked opponent candidate lookup failed for character ID: " + characterId, e);
        return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
    }
}
```

## 7. `AttackResult` — gains `mode`

`server/src/main/java/io/github/ydhekim/crimson_sky/server/combat/AttackResult.java`: add `BattleMode mode` as the **last** field (minimizes disruption — every existing construction just gets one trailing argument, no reordering):

```java
public record AttackResult(
    long battleId,
    long characterId,
    Long opponentCharacterId,
    String opponentDisplayName,
    boolean opponentIsBot,
    boolean won,
    Array<Array<ResolvedAction>> turns,
    BattleMode mode
) {
    public AttackResponse toResponse(RewardOutcome outcome) {
        // unchanged — mode does not cross the wire (see §8's note)
        return new AttackResponse(battleId, opponentDisplayName, won, turns,
            outcome.goldDelta(), outcome.expDelta(), outcome.eloDelta(),
            outcome.skillPointsGained(), outcome.levelsGained(), outcome.statPointsGained(),
            outcome.bonusRewardGranted());
    }
}
```

`AttackResponse` itself is **not** changed — the client already knows which mode it requested, and `eloDelta` in the response is "whichever track this battle moved," so no wire-format ambiguity exists without adding a field. This mirrors Q1's own scoping note: combat packets stay minimal until M5 wires up real client consumption.

## 8. `AttackService` — the level gate, mode-aware matching, `mode` threaded through

`server/src/main/java/io/github/ydhekim/crimson_sky/server/service/AttackService.java`:

```java
/** Minimum character level to queue a RANKED attack (system design §21). */
static final int RANKED_LEVEL_REQUIREMENT = 25;

/**
 * Whether characterId currently qualifies for ranked matchmaking (system design §21) — the guard
 * AttackRequestHandler checks before calling attack() with BattleMode.RANKED, the same "check first,
 * reject early" shape the daily-battle-cap check already uses (§20).
 */
public boolean isRankedEligible(long characterId) {
    ServiceResult<Character> character = characterService.getCharacter(characterId);
    return character.success() && character.data().level() >= RANKED_LEVEL_REQUIREMENT;
}
```

`attack` gains the `mode` parameter and threads it through opponent selection and Elo lookup:

```java
public Optional<AttackResult> attack(long characterId, BattleMode mode) {
    ServiceResult<Character> attacker = characterService.getCharacter(characterId);
    if (!attacker.success()) {
        log.info("Refusing attack: character " + characterId + " could not be loaded.");
        return Optional.empty();
    }

    int elo;
    if (mode == BattleMode.RANKED) {
        elo = battleHistoryDao.getRankedElo(characterId);
    } else {
        ServiceResult<Integer> eloResult = characterService.getElo(characterId);
        if (!eloResult.success()) {
            log.info("Refusing attack: Elo lookup failed for character " + characterId + ".");
            return Optional.empty();
        }
        elo = eloResult.data();
    }

    Opponent opponent = selectOpponent(characterId, elo, mode);
    return Optional.of(resolveBattle(attacker.data(), opponent, mode));
}
```

`selectOpponent` and `resolveBattle` both gain a trailing `BattleMode mode` parameter:

```java
private Opponent selectOpponent(long characterId, int elo, BattleMode mode) {
    Optional<Character> inRange = pickRandom(mode == BattleMode.RANKED
        ? characterService.findRankedOpponentCandidates(characterId, elo, BASE_ELO_RANGE)
        : characterService.findOpponentCandidates(characterId, elo, BASE_ELO_RANGE), characterId);
    if (inRange.isPresent()) {
        return new Opponent(inRange.get(), false);
    }

    Optional<Character> anyOpponent = pickRandom(mode == BattleMode.RANKED
        ? characterService.findAllRankedOpponentCandidates(characterId)
        : characterService.findAllOpponentCandidates(characterId), characterId);
    if (anyOpponent.isPresent()) {
        log.info("No opponent within ±" + BASE_ELO_RANGE + " Elo of character " + characterId
            + "; widened to an unbounded range.");
        return new Opponent(anyOpponent.get(), false);
    }

    log.info("No persisted opponent available for character " + characterId + "; synthesizing a bot.");
    return new Opponent(botFactory.createBot(elo), true);
}
```

(`botFactory.createBot(elo)` needs no change — it already takes whichever Elo number is handed to it, normal or ranked, per §21's own note that `BotFactory` needs no code change.)

`resolveBattle` just needs `mode` folded into the `AttackResult` it builds:

```java
private AttackResult resolveBattle(Character attacker, Opponent opponent, BattleMode mode) {
    // ...unchanged battle simulation...
    AttackResult result = new AttackResult(
        battleId, attacker.id(), opponent.persistedId(), opponent.character().name(),
        opponent.isBot(), won,
        battleEngine.turnHistoryOf(attackerParticipant), mode);
    // ...unchanged logging/return...
}
```

Add the `BattleMode` import.

## 9. New `MessageCode` + `AttackRequestHandler` — the level gate

`common/src/main/java/io/github/ydhekim/crimson_sky/common/model/MessageCode.java` — new trailing section:

```java
// Ranked ladder (system design §21)
RANKED_LEVEL_GATE_NOT_MET
```

`server/src/main/java/io/github/ydhekim/crimson_sky/server/network/handler/AttackRequestHandler.java` — insert the rank gate between the existing daily-cap check and the `attack()` call, reusing the `AttackRejectedResponse` packet Q1 already built:

```java
int remaining = attackService.remainingDailyBattles(request.characterId());
if (remaining <= 0) {
    // ...unchanged...
}

if (request.mode() == BattleMode.RANKED && !attackService.isRankedEligible(request.characterId())) {
    log.info("Rejected ranked attack request: character " + request.characterId()
        + " is below the level-25 gate (Connection ID: " + connection.getID() + ")");
    connection.sendTCP(new AttackRejectedResponse(MessageCode.RANKED_LEVEL_GATE_NOT_MET.name()));
    return;
}

Optional<AttackResult> result = attackService.attack(request.characterId(), request.mode());
// ...unchanged from here...
```

Add the `BattleMode` import.

## 10. `RewardService` — dual Elo application

`server/src/main/java/io/github/ydhekim/crimson_sky/server/service/RewardService.java` needs read access to ranked Elo *before* its transaction opens (same reason it already reads normal Elo early: the whole `RewardOutcome` — including gold/exp, which the Elo gap feeds — must be decided before any write starts). Add a `BattleHistoryDao` constructor dependency, inserted before `Random` like `AttackService`'s did in Q1:

```java
private final BattleHistoryDao battleHistoryDao;

public RewardService(Jdbi jdbi, CharacterService characterService, BattleHistoryDao battleHistoryDao) {
    this(jdbi, characterService, battleHistoryDao, new Random());
}

public RewardService(Jdbi jdbi, CharacterService characterService, BattleHistoryDao battleHistoryDao, Random random) {
    this.jdbi = jdbi;
    this.characterService = characterService;
    this.battleHistoryDao = battleHistoryDao;
    this.random = random;
}
```

`applyRewards` branches the Elo source by `result.mode()`. Replace the existing attacker/opponent Elo reads:

```java
public RewardOutcome applyRewards(AttackResult result) {
    ServiceResult<Character> attacker = characterService.getCharacter(result.characterId());
    if (!attacker.success()) {
        // ...unchanged...
    }

    BattleMode mode = result.mode();
    int attackerElo = mode == BattleMode.RANKED
        ? battleHistoryDao.getRankedElo(result.characterId())
        : requireElo(result, characterService.getElo(result.characterId()));
    if (mode == BattleMode.NORMAL && attackerElo == ELO_LOOKUP_FAILED) {
        return RewardOutcome.none();
    }

    Integer opponentElo = opponentElo(result, attackerElo, mode);
    if (opponentElo == null) {
        // ...unchanged log/return...
    }

    RewardOutcome base = computeRewards(result.won(), attackerElo, opponentElo);
    // ...unchanged leveling/milestone-roll logic...

    try {
        jdbi.useTransaction(handle -> {
            CharacterDao characterDao = handle.attach(CharacterDao.class);
            int normalEloDelta = mode == BattleMode.NORMAL ? outcome.eloDelta() : 0;
            characterDao.applyBattleProgress(result.characterId(), outcome.expDelta(), normalEloDelta,
                newLevel, outcome.statPointsGained(), outcome.skillPointsGained());
            handle.attach(AccountDao.class)
                .addGlobalCurrency(accountId, outcome.goldDelta());
            handle.attach(BattleHistoryDao.class).insert(
                result.characterId(), result.opponentCharacterId(), result.opponentIsBot(),
                result.won(), outcome.goldDelta(), outcome.expDelta(), outcome.eloDelta(),
                mode.name(), mode == BattleMode.RANKED ? outcome.eloDelta() : null);
            // ...unchanged inventory block...
        });
    } catch (Exception e) {
        // ...unchanged...
    }
    // ...unchanged...
}
```

Use your own judgment on the exact `requireElo`/`ELO_LOOKUP_FAILED` shape above — the point is only: **preserve the existing early-return-on-failed-normal-Elo-lookup behavior exactly**, and add the ranked branch alongside it without disturbing it. A `getRankedElo` call cannot itself "fail" the way `characterService.getElo` can (it's a `COALESCE`d SQL aggregate, never empty), so the ranked branch has no equivalent failure path to preserve — only the normal branch does. Feel free to restructure this more cleanly than the sketch above as long as: normal-mode behavior (including its failure logging) is unchanged, and `characters.elo` is never written to on a ranked battle.

`opponentElo` also needs a `mode` parameter — a bot's Elo still mirrors the attacker's own rating (§8.1's rule, unchanged), and a real opponent's Elo is read from whichever track matches `mode`:

```java
private Integer opponentElo(AttackResult result, int attackerElo, BattleMode mode) {
    if (result.opponentIsBot()) {
        return attackerElo;
    }
    if (mode == BattleMode.RANKED) {
        return battleHistoryDao.getRankedElo(result.opponentCharacterId());
    }
    ServiceResult<Integer> elo = characterService.getElo(result.opponentCharacterId());
    return elo.success() ? elo.data() : null;
}
```

Add `BattleMode` import.

### `ServiceRegistry` wiring

`server/src/main/java/io/github/ydhekim/crimson_sky/server/service/ServiceRegistry.java` already constructs a `BattleHistoryDao battleHistoryDao` local (for Q1's `AttackService`) — reuse the same instance for `RewardService`:

```java
this.rewardService = new RewardService(dbManager.getJdbi(), characterService, battleHistoryDao);
```

## 11. Exhaustive call-site fixes

Grep each pattern after editing to confirm none are missed — these counts are accurate as of this prompt being written, but re-verify.

**`new AttackRequest(` — 6 sites, all in `AttackRequestHandlerTest.java`.** Every one needs `BattleMode.NORMAL` appended as the second argument, e.g. `new AttackRequest(CHARACTER_A, BattleMode.NORMAL)`. Add the `BattleMode` import to this file.

**`new AttackResult(` — 9 sites.** Every one needs `BattleMode.NORMAL` appended as the trailing (8th) argument:
- `AttackService.java` (production, §8 above already covers this — passes the real `mode` parameter, not a hardcoded `NORMAL`).
- `RewardServiceTest.java:63` and `:68` (`realFight`/`botFight` helpers).
- `RewardServicePetHealthTest.java:64`.
- `RewardServiceDurabilityTest.java:56`.
- `RewardServiceConsumablePersistenceTest.java:92`.
- `BattleLeavesInventoryAloneTest.java:206`.
- `RewardServiceConsumableChargesTest.java:37` and `:82`.

Add the `BattleMode` import to each file touched.

**`.attack(` — 8 sites** (`AttackService.attack(...)` calls). Every one needs `, BattleMode.NORMAL` appended as the second argument — these are all pre-existing normal-mode tests, not new ranked coverage:
- `AttackRequestHandler.java` (production, §9 above already covers this — passes `request.mode()`, not hardcoded).
- `AttackServiceTest.java:57` (`attack()` helper) and `:143`.
- `RewardServicePetHealthTest.java:122`.
- `RewardServiceDurabilityTest.java:117`.
- `BattleLeavesInventoryAloneTest.java:101`, `:129`, `:167`, `:302`.

**`new RewardService(` — 9 sites.** Every one needs a `BattleHistoryDao` argument inserted right after `characterService` (before `Random`, where present):
- `ServiceRegistry.java` (§10 above).
- `AttackRequestHandlerTest.java:67` — this test already has `db` (`TestDatabase`) in scope: `new RewardService(db.jdbi(), characterService, db.jdbi().onDemand(BattleHistoryDao.class))`.
- `BattleLeavesInventoryAloneTest.java` — four sites (`:94`, `:164`, `:204`, `:300`), each with its own `TestDatabase` already in scope (`db`/`shopDb`/`potionDb`/`bonusDb`); use that test's own instance at each site.
- `RewardServiceConsumablePersistenceTest.java:74`.
- `RewardServiceDurabilityTest.java:115`.
- `RewardServicePetHealthTest.java:120`.
- `RewardServiceTest.java:52`.

All of these except `ServiceRegistry` already have a `TestDatabase db` (or equivalently named) instance in scope — use `db.jdbi().onDemand(BattleHistoryDao.class)` at each.

## 12. `FakeBattleHistoryDao` — extend for ranked Elo

`server/src/test/java/io/github/ydhekim/crimson_sky/server/support/FakeBattleHistoryDao.java` implements `BattleHistoryDao`, so it must implement the new abstract method (`getRankedEloAsOf` — `getRankedElo` is a default method and needs no override) and the new `insert` signature. Extend the internal `Row` to carry `battleMode`/`rankedEloDelta` so ranked-mode `AttackServiceTest` cases (§13) have real numbers to work with, not just level-gating:

```java
private record Row(long characterId, boolean won, Instant createdAt, String battleMode, Integer rankedEloDelta) {
}

/** Seeds one past NORMAL battle, for cap-rejection tests (unchanged from Q1). */
public FakeBattleHistoryDao with(long characterId, boolean won, Instant createdAt) {
    rows.add(new Row(characterId, won, createdAt, "NORMAL", null));
    return this;
}

/** Seeds one past RANKED battle with a known Elo swing, for ranked-Elo tests. */
public FakeBattleHistoryDao withRanked(long characterId, int rankedEloDelta, Instant createdAt) {
    rows.add(new Row(characterId, true, createdAt, "RANKED", rankedEloDelta));
    return this;
}

@Override
public void insert(long characterId, Long opponentCharacterId, boolean opponentIsBot, boolean won,
                    int goldDelta, long expDelta, int eloDelta, String battleMode, Integer rankedEloDelta) {
    rows.add(new Row(characterId, won, Instant.now(), battleMode, rankedEloDelta));
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

@Override
public int getRankedEloAsOf(long characterId, Instant asOf) {
    int sum = rows.stream()
        .filter(r -> r.characterId() == characterId && "RANKED".equals(r.battleMode()) && !r.createdAt().isAfter(asOf))
        .mapToInt(r -> r.rankedEloDelta() != null ? r.rankedEloDelta() : 0)
        .sum();
    return 1000 + sum;
}
```

## 13. New tests

**`AttackServiceTest`** (headless — stays true to its own "no DB" documentation, so only level-gating and Elo-value behavior, both of which the fakes model correctly):
- `isRankedEligible` returns `false` below level 25, `true` at or above (use `CombatFixtures.characterAtLevel`).
- Ranked matchmaking excludes a sub-25 candidate and falls back to a bot when no one else qualifies.
- `attack(characterId, RANKED)` reads Elo via `battleHistoryDao.getRankedElo`, not `characterService.getElo` — seed `FakeBattleHistoryDao.withRanked(...)` and confirm a bot fight's Elo delta is computed against that number, not 1000/whatever the character's normal `elo` fixture value is.
- A `NORMAL` attack's behavior is completely unchanged from before this prompt (regression coverage for the existing suite, which already covers this — just confirm nothing broke).

**New DAO-level test — `CharacterDaoRankedOpponentCandidatesTest`** (new file, `server/src/test/java/.../server/database/dao/`, mirroring the existing `BattleHistoryDaoCountWinsTest` precedent: real SQL against `TestDatabase`, not a fake). This is where actual elo-band correctness against real SQL is proven, since `FakeCharacterDao` can't model it (§5's documented limitation):
- Seed several characters at level 25+ with varying computed ranked Elo (via `TestDatabase.withBattleHistory`-style ranked rows — you'll need a small `TestDatabase` helper for seeding a ranked `battle_history` row with `battle_mode`/`ranked_elo_delta`, mirroring `withBattleHistory`; add one).
- Confirm `findRankedOpponentCandidatesInEloRange` returns only in-band, level-25+, non-self candidates.
- Confirm a level-24 character with an otherwise-qualifying computed Elo is excluded.
- Confirm `findAllRankedOpponentCandidates` returns every level-25+ character regardless of Elo.

**`RewardServiceTest`** — add ranked-mode coverage:
- A `RANKED` win/loss writes `ranked_elo_delta` on the `battle_history` row and leaves `elo_delta` at the value the K-factor formula produces (unchanged formula), but **`characters.elo` itself is untouched** — assert `db.eloOf(characterId)` is unchanged pre/post.
- A `NORMAL` win/loss (regression) still moves `characters.elo` and writes a `NULL` `ranked_elo_delta` — you'll likely need small `TestDatabase` accessors for reading `battle_mode`/`ranked_elo_delta` off the seeded row, alongside the existing `onlyBattleHistoryRow()`; extend that record/query rather than adding a parallel one.

### `TestDatabase` schema

`battle_history`'s H2 table (§21's V13 columns) needs `battle_mode VARCHAR(16) NOT NULL DEFAULT 'NORMAL'` and `ranked_elo_delta INTEGER` added, mirroring how V11's `won`/`quest_claims` were added in Epic P's prompt. Extend `withBattleHistory` or add a `withRankedBattleHistory(characterId, rankedEloDelta, createdAt)` overload, and extend `onlyBattleHistoryRow()`'s `BattleHistoryRow` (or add a narrow accessor) so tests can assert on the two new columns.

## Testing

Run `gradlew.bat server:test` (or `gradlew.bat build` for everything). Confirm:

- Every call site enumerated in §11 compiles against the new signatures — this is a compile-breaking change everywhere `AttackRequest`, `AttackResult`, `AttackService.attack`, and `RewardService`'s constructor are touched, so a clean build is the real signal.
- `BattleLeavesInventoryAloneTest` (C2's structural guard) still passes with only its construction lines changed — the ranked Elo track lives in `battle_history`, never in `characters.inventory`/`loadout`, so no new exception should be needed there.
- New tests from §13 pass, including the new DAO-level SQL test.
- A normal-mode attack's Elo, Gold, and Exp payout is byte-for-byte unchanged from before this prompt (the whole point of "additive, not a retrofit").

## Definition of done

- `AttackRequest` carries `BattleMode`; a `RANKED` request from a sub-25 character is rejected via `AttackRejectedResponse(RANKED_LEVEL_GATE_NOT_MET)` before any battle resolves.
- Ranked matchmaking reuses the exact band→widen→bot algorithm, restricted to level-25+ candidates, keyed off live-computed ranked Elo.
- A ranked battle moves `ranked_elo_delta` on its `battle_history` row and leaves `characters.elo` untouched; a normal battle does the reverse. Both use the identical reward formulas otherwise.
- `characters.elo` and `battle_history.elo_delta`/`won`/quest/cap counting behavior for **normal** battles is provably unchanged (regression-tested).
- `gradlew.bat build` is green.
- R3 (ladder viewing + monthly claim) is explicitly out of scope for this prompt — do not add `ladder_claims`, reward tiers, or a claim endpoint here.
