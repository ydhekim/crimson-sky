# Implementation prompt — Epic R3: Monthly ladder + claim

Paste this whole file to Claude Code as the task.

## Context

Crimson Sky (`docs/planning/01-system-design-combat-engine.md` §21, `docs/planning/02-user-stories.md` Epic R). R1-R2 (ranked battle mode, level-25 gate, dual Elo track) already shipped — `BattleHistoryDao.getRankedElo`/`getRankedEloAsOf` and the ranked opponent-candidate queries on `CharacterDao` already exist and are what this prompt builds on. This is R3: a character's monthly ladder standing (live, informational) and a once-per-month claim of a rank-tiered reward, mirroring the quest system's (`QuestService`) shape closely — same raw-`Jdbi` service, same claim-ledger-with-a-`UNIQUE`-constraint pattern, same accepted TOCTOU gap.

**A real scope gap, resolved here rather than half-built:** §21's own text names an "exclusive title" as part of the top-1 reward. Titles (`characters.equipped_title`) are part of §22/Epic S (achievements & character page), which has not been built. This prompt does **not** grant a title — top-1's reward is gold + a rare weapon only, and the title is left as a documented follow-up once Epic S exists. §21 gets a short correction note reflecting this, the same treatment §20 got when Q2's design gap was found.

Read `docs/planning/01-system-design-combat-engine.md` §21 before starting, and skim `docs/planning/13-implementation-prompt-o2-potions.md`'s and the quest-system prompt's `QuestService`/`QuestClaimDao` shape — this prompt asks you to mirror that pattern closely rather than reinvent it.

## 1. Design docs already updated

`docs/planning/01-system-design-combat-engine.md` §21 and `docs/planning/02-user-stories.md`'s R3 entry have already been corrected to reflect the scope below (500 gold + Warhammer for top 1, title deferred to Epic S) — no doc edits needed as part of this task, just build to match what they now say.

## 2. `QuestPeriods` — gains month boundaries

`server/src/main/java/io/github/ydhekim/crimson_sky/server/quest/QuestPeriods.java` already holds `startOfToday`/`startOfWeek` plus their `Clock`-taking test seams. Add the calendar-month equivalent, same shape:

```java
/** UTC midnight of the 1st of this month (system design §21) — the ladder's live-standing boundary. */
public static Instant startOfMonth() {
    return startOfMonth(Clock.systemUTC());
}

/** UTC midnight of the 1st of last month (system design §21) — the period a ladder claim targets. */
public static Instant startOfPreviousMonth() {
    return startOfPreviousMonth(Clock.systemUTC());
}

static Instant startOfMonth(Clock clock) {
    return YearMonth.now(clock).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
}

static Instant startOfPreviousMonth(Clock clock) {
    return YearMonth.now(clock).minusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
}
```

Add the `java.time.YearMonth` import. **A claim always targets the most recently completed month**, not the current in-progress one — standing can still move mid-month, so nothing is claimable until the month it describes has actually ended. "As of end of last month" is expressed as `startOfMonth().minusNanos(1)` (one nanosecond before this month began) wherever it's needed below — reusing `BattleHistoryDao.getRankedEloAsOf`'s existing `<=` bound rather than adding a `<` variant, the same half-open-window pattern `countWins`'s own docstring already calls out.

## 3. Migration V14

New file `server/src/main/resources/db/migration/V14__Add_Ladder_Claims.sql`:

```sql
CREATE TABLE IF NOT EXISTS ladder_claims (
    id SERIAL PRIMARY KEY,
    character_id INTEGER NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    period_start TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (character_id, period_start)
);
```

`period_start` is always the start of the month being claimed (i.e. `QuestPeriods.startOfPreviousMonth()` at claim time) — the `UNIQUE` is the real backstop against a double claim, same role `quest_claims`' triple plays.

## 4. `LadderClaimDao`

New file `server/src/main/java/io/github/ydhekim/crimson_sky/server/database/dao/LadderClaimDao.java`, mirroring `QuestClaimDao`:

```java
package io.github.ydhekim.crimson_sky.server.database.dao;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.time.Instant;

/** The claim ledger for the monthly ladder (system design §21, V14) — mirrors QuestClaimDao's shape. */
public interface LadderClaimDao {

    @SqlUpdate("INSERT INTO ladder_claims (character_id, period_start) VALUES (:characterId, :periodStart)")
    void insert(@Bind("characterId") long characterId, @Bind("periodStart") Instant periodStart);

    @SqlQuery("SELECT EXISTS(SELECT 1 FROM ladder_claims WHERE character_id = :characterId AND period_start = :periodStart)")
    boolean isClaimed(@Bind("characterId") long characterId, @Bind("periodStart") Instant periodStart);
}
```

## 5. `CharacterDao` — rank computation

Add to `server/src/main/java/io/github/ydhekim/crimson_sky/server/database/dao/CharacterDao.java`, next to R1-R2's ranked opponent-candidate queries:

```java
/**
 * How many level-25+ characters (other than characterId) have a live-computed ranked Elo, as of asOf,
 * strictly greater than elo (system design §21) — one more than this count is the character's ladder
 * rank. Reuses the same correlated-subquery shape as findRankedOpponentCandidatesInEloRange.
 */
@SqlQuery("SELECT COUNT(*) FROM characters c WHERE c.id <> :characterId AND c.level >= 25 " +
    "AND (1000 + COALESCE((SELECT SUM(bh.ranked_elo_delta) FROM battle_history bh " +
    "WHERE bh.character_id = c.id AND bh.battle_mode = 'RANKED' AND bh.created_at <= :asOf), 0)) > :elo")
int countRankedCharactersAboveEloAsOf(@Bind("characterId") long characterId, @Bind("elo") int elo,
                                      @Bind("asOf") Instant asOf);
```

`FakeCharacterDao` must implement this too — same documented limitation as the R1-R2 candidate queries (it can't compute ranked Elo, since that lives in `battle_history`). Return `0` unconditionally (a safe "you're always rank 1" stub); real correctness is proven at the DAO level (§10 below), same split R1-R2 established:

```java
/** As with the ranked candidate queries, the fake can't compute ranked Elo — see that javadoc. Real
 *  rank correctness is proven against SQL in CharacterDaoLadderRankTest, not through this fake. */
@Override
public int countRankedCharactersAboveEloAsOf(long characterId, int elo, Instant asOf) {
    return 0;
}
```

## 6. `LadderRewardTier`

New file `server/src/main/java/io/github/ydhekim/crimson_sky/server/ladder/LadderRewardTier.java` — pure, no DB/Ashley dependency, same treatment `QuestPeriodType`/`QuestDefinition` get:

```java
package io.github.ydhekim.crimson_sky.server.ladder;

/** Monthly ladder reward tiers by rank, first-pass numbers (system design §21). */
public enum LadderRewardTier {
    TOP_1(1, 1),
    TOP_2_10(2, 10),
    TOP_11_100(11, 100);

    private final int minRank;
    private final int maxRank;

    LadderRewardTier(int minRank, int maxRank) {
        this.minRank = minRank;
        this.maxRank = maxRank;
    }

    /** The tier {@code rank} falls into, or {@code null} below rank 100 (no reward, per §21). */
    public static LadderRewardTier forRank(int rank) {
        for (LadderRewardTier tier : values()) {
            if (rank >= tier.minRank && rank <= tier.maxRank) {
                return tier;
            }
        }
        return null;
    }
}
```

## 7. `common.model` records

New file `LadderStatus.java`:

```java
package io.github.ydhekim.crimson_sky.common.model;

/**
 * A character's live ladder standing plus last month's claimable reward (system design §21). Computed on
 * demand, never stored — the same "compute live from battle_history" rule §19/§20 already established.
 *
 * <ul>
 *   <li>{@code currentRankedElo}/{@code currentRank} — this instant's standing, informational only; it can
 *       still move before the month ends and is never what a claim pays against.</li>
 *   <li>{@code lastMonthRank}/{@code rewardTier} — the frozen standing as of the end of the most recently
 *       completed month, and the tier (if any) it falls into. {@code rewardTier} is {@code null} below
 *       rank 100.</li>
 *   <li>{@code claimable}/{@code alreadyClaimed} — whether last month's reward can be taken right now.</li>
 * </ul>
 */
public record LadderStatus(
    boolean rankedEligible,
    int currentRankedElo,
    int currentRank,
    int lastMonthRank,
    String rewardTier,
    boolean claimable,
    boolean alreadyClaimed
) {
}
```

New file `LadderClaimResult.java`:

```java
package io.github.ydhekim.crimson_sky.common.model;

/** The outcome of a successful ladder claim (system design §21) — mirrors QuestClaimResult's shape. */
public record LadderClaimResult(
    String rewardTier,
    long remainingGold,
    int repairTokenCount,
    int petCareKitCount,
    String itemGranted
) {
}
```

`itemGranted` is the granted weapon's name for `TOP_1`, `null` for every other tier.

## 8. Packets

Four new files in `common/src/main/java/io/github/ydhekim/crimson_sky/common/network/packet/`, mirroring `QuestStatusRequest`/`QuestStatusResponse`/`ClaimQuestRequest`/`ClaimQuestResponse` exactly:

```java
public record LadderStatusRequest(long characterId) {}

public record LadderStatusResponse(boolean success, String message, LadderStatus status) {}

public record ClaimLadderRewardRequest(long characterId) {}

public record ClaimLadderRewardResponse(boolean success, String message, LadderClaimResult result) {}
```

Register all four in `KryoConfig.register()` — **append at the very end**, after `BattleMode` (R1-R2's last line):

```java
kryo.register(LadderStatus.class, new RecordSerializer<>(LadderStatus.class));
kryo.register(LadderClaimResult.class, new RecordSerializer<>(LadderClaimResult.class));
kryo.register(LadderStatusRequest.class, new RecordSerializer<>(LadderStatusRequest.class));
kryo.register(LadderStatusResponse.class, new RecordSerializer<>(LadderStatusResponse.class));
kryo.register(ClaimLadderRewardRequest.class, new RecordSerializer<>(ClaimLadderRewardRequest.class));
kryo.register(ClaimLadderRewardResponse.class, new RecordSerializer<>(ClaimLadderRewardResponse.class));
```

## 9. `MessageCode`

New trailing section:

```java
// Ranked ladder claim (system design §21)
LADDER_NOT_RANKED_ELIGIBLE,
LADDER_NO_REWARD_THIS_RANK,
LADDER_ALREADY_CLAIMED
```

## 10. `LadderService`

New file `server/src/main/java/io/github/ydhekim/crimson_sky/server/service/LadderService.java` — raw `Jdbi` + `CharacterService`, same constructor shape as `QuestService` (deliberately **not** a dependency on `AttackService`: the level-25 check is inlined via `characterService.getCharacter(...).data().level()`, keeping this service's dependency list as narrow as `QuestService`'s own).

```java
package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.LadderClaimResult;
import io.github.ydhekim.crimson_sky.common.model.LadderStatus;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.server.database.dao.AccountDao;
import io.github.ydhekim.crimson_sky.server.database.dao.BattleHistoryDao;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.database.dao.LadderClaimDao;
import io.github.ydhekim.crimson_sky.server.ladder.LadderRewardTier;
import io.github.ydhekim.crimson_sky.server.quest.QuestPeriods;
import org.jdbi.v3.core.Jdbi;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * The monthly ladder's live status and once-per-month claim (system design §21, Epic R3) — the same
 * shape as {@code QuestService}: nothing here stores standing, it's always recomputed off
 * {@code battle_history}, so the only state a claim writes is {@code ladder_claims}' one row per period.
 *
 * <p>Same accepted TOCTOU gap {@code QuestService} documents: the pre-transaction rank/claim-guard read
 * can race a concurrent claim, but {@code ladder_claims}' UNIQUE constraint is the real backstop — a
 * race loses at the INSERT, caught and reported as {@code ERROR_UNKNOWN}.
 */
public class LadderService {

    private static final Logger log = new Logger("LadderService", Logger.DEBUG);

    static final int RANKED_LEVEL_REQUIREMENT = 25;
    static final int TOP_1_GOLD_REWARD = 500;
    static final int TOP_2_10_GOLD_REWARD = 100;
    static final int TOP_11_100_GOLD_REWARD = 30;

    /** Duplicated from RewardService's own milestone-bonus constant, same id/stats, deliberately not
     *  reached into (it's private there) — the identical "curated content, no new item invented ahead of
     *  its own epic" precedent RewardService's own javadoc already establishes for BONUS_WEAPONS. */
    private static final Weapon TOP_1_WEAPON_REWARD =
        new Weapon(3L, "Warhammer", "", Rarity.RARE, 40f, 15, 45, 25, 20, 20);

    private final Jdbi jdbi;
    private final CharacterService characterService;

    public LadderService(Jdbi jdbi, CharacterService characterService) {
        this.jdbi = jdbi;
        this.characterService = characterService;
    }

    public ServiceResult<LadderStatus> getStatus(long accountId, long characterId) {
        try {
            if (!characterService.isCharacterOwnedBy(accountId, characterId)) {
                log.info("Ladder status rejected: character " + characterId + " not owned by account " + accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            ServiceResult<Character> character = characterService.getCharacter(characterId);
            if (!character.success()) {
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }
            if (character.data().level() < RANKED_LEVEL_REQUIREMENT) {
                return ServiceResult.success(MessageCode.SUCCESS,
                    new LadderStatus(false, 1000, 0, 0, null, false, false));
            }

            LadderStatus status = jdbi.withHandle(handle -> {
                BattleHistoryDao battleHistoryDao = handle.attach(BattleHistoryDao.class);
                CharacterDao characterDao = handle.attach(CharacterDao.class);
                LadderClaimDao claimDao = handle.attach(LadderClaimDao.class);

                int currentElo = battleHistoryDao.getRankedElo(characterId);
                int currentRank = characterDao.countRankedCharactersAboveEloAsOf(
                    characterId, currentElo, Instant.now()) + 1;

                Instant lastMonthEnd = QuestPeriods.startOfMonth().minusNanos(1);
                Instant lastMonthStart = QuestPeriods.startOfPreviousMonth();
                int lastMonthElo = battleHistoryDao.getRankedEloAsOf(characterId, lastMonthEnd);
                int lastMonthRank = characterDao.countRankedCharactersAboveEloAsOf(
                    characterId, lastMonthElo, lastMonthEnd) + 1;

                LadderRewardTier tier = LadderRewardTier.forRank(lastMonthRank);
                boolean claimed = claimDao.isClaimed(characterId, lastMonthStart);
                boolean claimable = tier != null && !claimed;

                return new LadderStatus(true, currentElo, currentRank, lastMonthRank,
                    tier != null ? tier.name() : null, claimable, claimed);
            });

            return ServiceResult.success(MessageCode.SUCCESS, status);
        } catch (Exception e) {
            log.error("Ladder status lookup failed for character " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    public ServiceResult<LadderClaimResult> claim(long accountId, long characterId) {
        try {
            if (!characterService.isCharacterOwnedBy(accountId, characterId)) {
                log.info("Ladder claim rejected: character " + characterId + " not owned by account " + accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            ServiceResult<Character> character = characterService.getCharacter(characterId);
            if (!character.success()) {
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }
            if (character.data().level() < RANKED_LEVEL_REQUIREMENT) {
                return ServiceResult.failure(MessageCode.LADDER_NOT_RANKED_ELIGIBLE);
            }

            Instant lastMonthEnd = QuestPeriods.startOfMonth().minusNanos(1);
            Instant lastMonthStart = QuestPeriods.startOfPreviousMonth();

            ClaimGuard guard = jdbi.withHandle(handle -> {
                int lastMonthElo = handle.attach(BattleHistoryDao.class).getRankedEloAsOf(characterId, lastMonthEnd);
                int rank = handle.attach(CharacterDao.class)
                    .countRankedCharactersAboveEloAsOf(characterId, lastMonthElo, lastMonthEnd) + 1;
                boolean claimed = handle.attach(LadderClaimDao.class).isClaimed(characterId, lastMonthStart);
                return new ClaimGuard(rank, claimed);
            });

            LadderRewardTier tier = LadderRewardTier.forRank(guard.rank());
            if (tier == null) {
                return ServiceResult.failure(MessageCode.LADDER_NO_REWARD_THIS_RANK);
            }
            if (guard.claimed()) {
                return ServiceResult.failure(MessageCode.LADDER_ALREADY_CLAIMED);
            }

            long[] remainingGold = {0L};
            int[] repairTokens = {0};
            int[] petCareKits = {0};
            String[] itemGranted = {null};

            jdbi.useTransaction(handle -> {
                CharacterDao characterDao = handle.attach(CharacterDao.class);
                AccountDao accountDao = handle.attach(AccountDao.class);
                handle.attach(LadderClaimDao.class).insert(characterId, lastMonthStart);

                Inventory locked = lockedInventory(characterDao, characterId);
                Map<String, Integer> consumables = consumablesOf(locked);
                Array<Weapon> weapons = locked.weapons() != null ? locked.weapons() : new Array<>();

                switch (tier) {
                    case TOP_1 -> {
                        accountDao.addGlobalCurrency(accountId, TOP_1_GOLD_REWARD);
                        weapons.add(TOP_1_WEAPON_REWARD);
                        itemGranted[0] = TOP_1_WEAPON_REWARD.name();
                        characterDao.updateInventory(characterId,
                            new Inventory(weapons, locked.skills(), locked.pets(), consumables));
                    }
                    case TOP_2_10 -> {
                        accountDao.addGlobalCurrency(accountId, TOP_2_10_GOLD_REWARD);
                        consumables.merge(ShopService.REPAIR_TOKEN, 1, Integer::sum);
                        consumables.merge(ShopService.PET_CARE_KIT, 1, Integer::sum);
                        characterDao.updateInventory(characterId,
                            new Inventory(weapons, locked.skills(), locked.pets(), consumables));
                    }
                    case TOP_11_100 -> accountDao.addGlobalCurrency(accountId, TOP_11_100_GOLD_REWARD);
                }

                remainingGold[0] = accountDao.getGlobalCurrency(accountId).orElse(0L);
                repairTokens[0] = consumables.getOrDefault(ShopService.REPAIR_TOKEN, 0);
                petCareKits[0] = consumables.getOrDefault(ShopService.PET_CARE_KIT, 0);
            });

            log.info("Character " + characterId + " claimed the ladder's " + tier + " reward for "
                + lastMonthStart + ".");
            return ServiceResult.success(MessageCode.SUCCESS,
                new LadderClaimResult(tier.name(), remainingGold[0], repairTokens[0], petCareKits[0], itemGranted[0]));
        } catch (Exception e) {
            log.error("Ladder claim failed for character " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    private record ClaimGuard(int rank, boolean claimed) {
    }

    /** Mirrors QuestService's identical helper. */
    private static Inventory lockedInventory(CharacterDao characterDao, long characterId) {
        return characterDao.getInventoryForUpdate(characterId)
            .orElseGet(() -> new Inventory(new Array<>(), new Array<>(), new Array<>(), new HashMap<>()));
    }

    /** Mirrors QuestService's identical helper. */
    private static Map<String, Integer> consumablesOf(Inventory inventory) {
        return inventory.consumables() != null ? new HashMap<>(inventory.consumables()) : new HashMap<>();
    }
}
```

Use your own judgment on the exact import list / minor structuring — the point is the logic above, mirroring `QuestService`'s established shape.

## 11. Handlers

Two new files, `server/src/main/java/io/github/ydhekim/crimson_sky/server/network/handler/`, mirroring `QuestStatusRequestHandler`/`ClaimQuestRequestHandler` exactly (same ownership-guard-then-answer-every-refusal posture):

```java
public class LadderStatusRequestHandler implements RequestHandler<LadderStatusRequest> {
    // constructor(LadderService, CharacterService) — mirrors QuestStatusRequestHandler
    // handle(): auth check (drop if unauthenticated) → ladderService.getStatus(accountId, characterId)
    //   → sendTCP(new LadderStatusResponse(result.success(), result.code().name(), result.data()))
}

public class ClaimLadderRewardRequestHandler implements RequestHandler<ClaimLadderRewardRequest> {
    // constructor(LadderService, CharacterService) — mirrors ClaimQuestRequestHandler
    // handle(): auth + ownership check (drop if either fails) → ladderService.claim(accountId, characterId)
    //   → sendTCP(new ClaimLadderRewardResponse(result.success(), result.code().name(), result.data()))
}
```

## 12. Wiring

`ServiceRegistry` — add a `LadderService` field, constructed the same way `QuestService` is:

```java
// Same reason as QuestService (system design §21): a claim spans `ladder_claims` and either
// `accounts.global_currency` or `characters.inventory` atomically, so it needs the raw Jdbi.
this.ladderService = new LadderService(dbManager.getJdbi(), characterService);
```

Add the field, constructor line, and a `getLadderService()` getter, matching `getQuestService()`'s placement.

`KryoPacketRouter` + `KryoPacketRouterFactory` — add `ladderService` as a constructor parameter (both classes), and register the two new handlers:

```java
handlers.put(LadderStatusRequest.class, new LadderStatusRequestHandler(ladderService, characterService));
handlers.put(ClaimLadderRewardRequest.class, new ClaimLadderRewardRequestHandler(ladderService, characterService));
```

## 13. Tests

**`QuestPeriodsTest`** — add cases for `startOfMonth`/`startOfPreviousMonth` using the `Clock`-seeded overloads, mirroring the existing `startOfWeek` tests (a fixed clock mid-month, confirm both boundaries land on the 1st at UTC midnight of the right month).

**New `LadderRewardTierTest`** (pure, no DB) — `forRank` returns the right tier at each boundary (1, 2, 10, 11, 100, 101) and `null` above 100.

**New `CharacterDaoLadderRankTest`** (DAO-level, mirrors `CharacterDaoRankedOpponentCandidatesTest`'s precedent exactly — real SQL against `TestDatabase`, since `FakeCharacterDao` can't model this either):
- Seed several level-25+ characters with varying ranked Elo via `TestDatabase.withRankedBattleHistory`.
- Confirm `countRankedCharactersAboveEloAsOf` counts only strictly-higher, level-25+, non-self characters.
- Confirm the `asOf` bound correctly excludes a battle that happened after it (seed one character's qualifying battle after the boundary; confirm they don't count).

**New `LadderServiceTest`** (mirrors `QuestServiceTest`'s shape, real `TestDatabase`):
- A character with no ranked history, level 25+: `getStatus` reports `rankedEligible=true`, elo 1000, rank 1 (alone on the ladder), `lastMonthRank` 1, tier `TOP_1`, `claimable=true`.
- A sub-25 character: `getStatus` reports `rankedEligible=false` and the rest zeroed/null — no exception, no DB hit for the rank computation.
- Claiming a `TOP_1` standing pays `TOP_1_GOLD_REWARD` gold and adds the Warhammer to `inventory.weapons` — assert via `db.inventoryJsonOf`/`db.goldOf`.
- Claiming twice for the same month fails the second time with `LADDER_ALREADY_CLAIMED` — seed via `TestDatabase`'s new `withLadderClaim` helper (add it, mirroring `withQuestClaim`) or by calling `claim` twice in the test itself.
- A character ranked below 100 gets `LADDER_NO_REWARD_THIS_RANK` on claim (seed enough higher-ranked level-25+ characters to push them out).

### `TestDatabase` additions

Add the `ladder_claims` table to the H2 schema (mirrors `quest_claims`' exact shape) and a `withLadderClaim(characterId, periodStart)` helper mirroring `withQuestClaim`.

## Testing

Run `gradlew.bat server:test` (or `gradlew.bat build`). Confirm:

- Every new file compiles and wires cleanly — this prompt adds new files/methods almost exclusively (unlike R1-R2, nothing existing changes signature except the additive `CharacterDao` method and `FakeCharacterDao`'s matching override), so there should be no call-site breakage elsewhere in the codebase.
- `BattleLeavesInventoryAloneTest` (C2's structural guard) still passes unmodified — the ladder's `TOP_1`/`TOP_2_10` rewards route through the existing `updateInventory` call, the one sanctioned writer, exactly like every other inventory-mutating feature since N2.
- New tests from §13 pass.

## Definition of done

- A level-25+ character can query live ladder standing and last-completed-month's claimable tier.
- Claiming pays the tier's gold (+ Repair Token/Pet Care Kit for top 2-10, + the Warhammer for top 1), writes exactly one `ladder_claims` row per character per month, and rejects a second claim for the same month.
- A sub-100 rank claims nothing (`LADDER_NO_REWARD_THIS_RANK`); a sub-25 character can't claim at all (`LADDER_NOT_RANKED_ELIGIBLE`).
- The exclusive-title portion of the top-1 reward is explicitly not implemented — §21/Epic R3's docs say so, so nobody re-derives it as a gap later.
- `gradlew.bat build` is green.
