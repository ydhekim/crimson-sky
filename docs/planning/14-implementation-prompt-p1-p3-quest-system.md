# Claude Code prompt — P1–P3: quest system (daily, weekly, repeatable)

Copy everything below the line into Claude Code. Written 2026-07-17, building on Epics L–O (all merged). This is the first slice of the sixth backlog item; the reward-application transaction pattern it uses is by now well established (`RewardService`/`SkillTreeService`/`ShopService`).

---

Add three quests — a daily, a weekly, and an uncapped-period repeatable — computed live from `battle_history` and claimed through a new `quest_claims` table. Read first: `docs/planning/01-system-design-combat-engine.md` §19. Also read, in full, the current `BattleHistoryDao.java`, `RewardService.java` (specifically its `battle_history` insert call), `AccountDao.java`, `CharacterDao.java`, `ShopService.java` (the closest sibling — same raw-`Jdbi` justification and the same "two payment/reward targets" shape), `SkillTreeService.java`, `ServiceRegistry.java`, `KryoPacketRouter.java`/`KryoPacketRouterFactory.java`, `KryoConfig.java`, `TestDatabase.java`, and the full migration history in `server/src/main/resources/db/migration` (currently V1–V10).

## Scope

Implement **P1** (infrastructure), **P2** (status + claim), and **P3** (the three quests' content) together — they don't decompose cleanly (P2 has nothing to claim without P3's content, and P3 has nowhere to live without P1's table). Daily battle cap enforcement is explicitly **not** in scope — that's Epic Q (Q1), which only *reuses* the period-boundary utility this prompt builds; nothing here gates `AttackService.attack`.

## 0. A real gap found while grounding — read before touching code

**§19's own query snippet assumes a column that does not exist.** The design doc's live-progress query is `COUNT(*) FROM battle_history WHERE character_id = ? AND won = true AND created_at > periodStart` — but `battle_history` (`V8__Battle_History.sql`) has no `won` column, and `BattleHistoryDao.insert`/`RewardService.applyRewards`'s call site never wrote one. Win/loss is only reconstructable today by comparing `gold_delta` against `RewardService.LOSS_GOLD` (a loss is always exactly `5`; a win is always `>= WIN_BASE_GOLD (25)`, by construction, no overlap) — but reverse-engineering a boolean from a currently-true numeric coincidence is exactly the kind of implicit coupling that silently breaks the day someone tunes those constants. Fix it properly: add the column.

This prompt's migration therefore does two things, not one — both small, both prerequisites for the same slice:
```sql
-- V11__Add_Quest_Claims.sql
ALTER TABLE battle_history ADD COLUMN won BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS quest_claims (
    id SERIAL PRIMARY KEY,
    character_id INTEGER NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    quest_id VARCHAR(64) NOT NULL,
    period_start TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (character_id, quest_id, period_start)
);
```
`DEFAULT FALSE` on the new column means pre-migration rows read as losses for quest-counting purposes regardless of their real outcome — harmless in practice (pre-alpha, no real player history to protect) and self-correcting the moment new rows start recording the real value; call this out in the close-out note rather than silently letting it slide.

**Fix the two call sites this touches:** `BattleHistoryDao.insert` gains a `@Bind("won") boolean won` parameter (and the `INSERT` column list); `RewardService.applyRewards`'s single call site passes `result.won()` through (the value is already sitting right there — this is purely a wiring gap, not a new computation). Add `BattleHistoryDao.countWins(long characterId, Instant since)`:
```java
@SqlQuery("SELECT COUNT(*) FROM battle_history WHERE character_id = :characterId AND won = true AND created_at > :since")
int countWins(@Bind("characterId") long characterId, @Bind("since") Instant since);
```

**`TestDatabase` maintains its own hand-written H2 schema subset** (not the real Flyway migrations) — its `battle_history` `CREATE TABLE` string and `BattleHistoryRow` record both need the new column, and it needs an entirely new `quest_claims` table plus a couple of small seed/read helpers (`withQuestClaim(...)`, `questClaimCountOf(...)` or similar, in the style of its existing `withX`/`xOf` methods) for `QuestServiceTest` to seed and assert against.

## 1. `QuestPeriods` — the shared boundary utility (new `server.quest` package)

A new package, `server.quest`, distinct from `server.content` (which is skill-tree specific) — quest concerns (period math, catalog) get their own home since Epic Q's daily-battle-cap will import this utility too.

```java
public final class QuestPeriods {
    private QuestPeriods() {}

    /** UTC midnight of today (system design §19/§20) — the daily quest and, later, the daily battle cap. */
    public static Instant startOfToday() {
        return startOfToday(Clock.systemUTC());
    }

    /** UTC midnight of the most recent Monday (system design §19) — the weekly quest boundary. */
    public static Instant startOfWeek() {
        return startOfWeek(Clock.systemUTC());
    }

    static Instant startOfToday(Clock clock) {
        return LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    static Instant startOfWeek(Clock clock) {
        LocalDate today = LocalDate.now(clock);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
```
The package-private `Clock`-taking overloads are the test seam (same "seeded/stubbed dependency" convention `RewardService`'s `Random` and `BotFactory`'s `Random` already use) — a real `startOfWeek()` call is untestable without one, since "today" changes.

## 2. `QuestCatalog` — the three quests, curated in code

Content, not data — same treatment `BotFactory`'s starter weapons/skills/pets and `SkillTreeCatalog`'s tree nodes already get (Epic E makes this data-driven later, for all of them at once, not one at a time). New `server.quest.QuestCatalog`:

```java
public enum QuestPeriodType { DAILY, WEEKLY, REPEATABLE }

public enum QuestDefinition {
    DAILY_WIN_2("daily.win2", "Win 2 battles today", QuestPeriodType.DAILY, 2),
    WEEKLY_WIN_10("weekly.win10", "Win 10 battles this week", QuestPeriodType.WEEKLY, 10),
    REPEATABLE_WIN_1("repeatable.win1", "Win 1 battle", QuestPeriodType.REPEATABLE, 1);

    public final String id;
    public final String description;
    public final QuestPeriodType periodType;
    public final int targetWins;
    // constructor wiring the above, plus a static `find(String id)` lookup mirroring SkillTreeCatalog.find
}
```

**Rewards, decided during this pass (real gaps §19 left open, resolved the same way L3's bonus table resolved an equivalent gap — real content only, nothing invented ahead of the epic that defines it):**
- **Daily** → 1 skill-restoration scroll: increment `Inventory.consumables[ShopService.SKILL_RESTORATION_SCROLL]` by 1. Put `QuestService` in `server.service` specifically so it can reference `ShopService`'s package-private consumable-key constants directly, rather than duplicating the strings.
- **Weekly** → the player's choice of **exactly two** rewards: a Repair Token or a Pet Care Kit (`ShopService.REPAIR_TOKEN`/`PET_CARE_KIT`), incrementing whichever was chosen by 1. No weapon/pet option — deliberately kept to the two existing maintenance-economy consumables rather than reaching into `RewardService`'s/`BotFactory`'s weapon or pet constants, which are calibrated for other purposes (starter content, milestone bonuses) and were never meant to double as quest rewards.
- **Repeatable** → a flat 15 gold via `AccountDao.addGlobalCurrency` (unconditional grant, no guard needed — this is a payout, not a spend). §19 also names "a potion charge" as an alternative — not implemented here: Epic O2 built the potion *mechanic* but no potion `Skill` instance has ever been authored (`ShopService`/`BotFactory` grant real weapons because real weapons exist; nothing analogous exists for potions yet), so there is nothing concrete to grant. Gold-only for this pass, flagged in the close-out the same way L3 flagged pets/scrolls as table entries with "no acquisition mechanic in code yet."

## 3. `QuestClaimDao` — new, mirrors `BattleHistoryDao`'s simplicity

```java
public interface QuestClaimDao {
    @SqlUpdate("INSERT INTO quest_claims (character_id, quest_id, period_start) VALUES (:characterId, :questId, :periodStart)")
    void insert(@Bind("characterId") long characterId, @Bind("questId") String questId, @Bind("periodStart") Instant periodStart);

    /** Daily/weekly's guard: has this exact period already been claimed (system design §19)? */
    @SqlQuery("SELECT EXISTS(SELECT 1 FROM quest_claims WHERE character_id = :characterId AND quest_id = :questId AND period_start = :periodStart)")
    boolean isClaimed(@Bind("characterId") long characterId, @Bind("questId") String questId, @Bind("periodStart") Instant periodStart);

    /**
     * The repeatable quest's cap (§19's note): every claim gets its own {@code period_start} (its own
     * {@code claimed_at} moment, not a shared boundary), so the daily/weekly {@code UNIQUE} trick can't
     * enforce the "at most 3" rule — this counts claims by wall-clock time instead.
     */
    @SqlQuery("SELECT COUNT(*) FROM quest_claims WHERE character_id = :characterId AND quest_id = :questId AND claimed_at > :since")
    int countClaimsSince(@Bind("characterId") long characterId, @Bind("questId") String questId, @Bind("since") Instant since);
}
```

## 4. `QuestService` — new, mirrors `ShopService`'s raw-`Jdbi` shape

Same justification as `RewardService`/`SkillTreeService`/`ShopService`: a claim spans `quest_claims` (the claim row) and either `accounts.global_currency` or `characters.inventory` (the reward) atomically.

```java
public class QuestService {
    static final int REPEATABLE_GOLD_REWARD = 15;
    static final int REPEATABLE_DAILY_CLAIM_CAP = 3;

    public record QuestProgress(String questId, String description, int currentWins, int targetWins,
                                boolean claimable, boolean alreadyClaimed, int claimsRemainingToday) {}

    public record QuestClaimResult(String questId, long remainingGold, int scrollCount,
                                   int repairTokenCount, int petCareKitCount) {}

    public ServiceResult<Array<QuestProgress>> getStatus(long accountId, long characterId) { ... }
    public ServiceResult<QuestClaimResult> claim(long accountId, long characterId, String questId, String rewardChoice) { ... }
}
```

**`getStatus`** — ownership check (fail closed, `ERROR_UNKNOWN`), then for each of the three `QuestDefinition`s: pick the period boundary (`QuestPeriods.startOfToday()` for `DAILY`/`REPEATABLE`, `startOfWeek()` for `WEEKLY`), read `battleHistoryDao.countWins(characterId, periodStart)`, and read the claimed/remaining state (`questClaimDao.isClaimed(...)` for `DAILY`/`WEEKLY`; `REPEATABLE_DAILY_CLAIM_CAP - questClaimDao.countClaimsSince(...)` for `REPEATABLE`). `claimable = currentWins >= targetWins && (period type's own not-yet-exhausted check)`.

**`claim`** — validation order, fail-closed throughout (same posture as every other claim/spend path this expansion built):
1. Ownership → `ERROR_UNKNOWN`.
2. `QuestCatalog.find(questId)` → `QUEST_NOT_FOUND` if unknown.
3. Live progress against the quest's own period boundary → `QUEST_NOT_COMPLETE` if `currentWins < targetWins`.
4. The period-specific claim guard → `QUEST_ALREADY_CLAIMED` (daily/weekly) or `QUEST_DAILY_CLAIM_CAP_REACHED` (repeatable, already at 3 today).
5. For `WEEKLY` only: `rewardChoice` must be exactly `ShopService.REPAIR_TOKEN` or `ShopService.PET_CARE_KIT` → `QUEST_INVALID_REWARD_CHOICE` otherwise. Ignored for `DAILY`/`REPEATABLE` (no choice to make).
6. Inside `jdbi.useTransaction`: insert the claim row (`periodStart` = the shared boundary for `DAILY`/`WEEKLY`; `Instant.now()` — its own moment — for `REPEATABLE`, exactly as §19's note requires), then apply the reward: `accountDao.addGlobalCurrency` for `REPEATABLE`, or the `getInventoryForUpdate`/increment-one-key/`updateInventory` shape `ShopService.buy` already established for `DAILY`/`WEEKLY`.

There is a small TOCTOU gap between the progress/claim-guard reads and the transaction's writes — accepted deliberately, the same simplification `CharacterService.allocateStatPoints` and `ShopService`'s consumable paths already take: the `quest_claims` table's own `UNIQUE` constraint is the real backstop for daily/weekly (a race loses at the `INSERT`, caught and reported as `ERROR_UNKNOWN`), and the repeatable cap's worst case is one extra claim slipping through on a genuine simultaneous double-submission — not a realistic concern at this scale.

## 5. `MessageCode` additions

```java
// Quests (system design §19)
QUEST_NOT_FOUND,
QUEST_NOT_COMPLETE,
QUEST_ALREADY_CLAIMED,
QUEST_DAILY_CLAIM_CAP_REACHED,
QUEST_INVALID_REWARD_CHOICE
```

## 6. Packets, handler, wiring

```java
public record QuestStatusRequest(long characterId) {}
public record QuestStatusResponse(boolean success, String message, Array<QuestService.QuestProgress> quests) {}
public record ClaimQuestRequest(long characterId, String questId, String rewardChoice) {} // rewardChoice null/ignored unless questId is the weekly quest
public record ClaimQuestResponse(boolean success, String message, QuestService.QuestClaimResult result) {}
```
Two new handlers (`QuestStatusRequestHandler`, `ClaimQuestRequestHandler`), following `RepairWeaponRequestHandler`'s exact shape: unauthenticated connection and non-owned character are logged-and-dropped, never answered; every actionable refusal (all five new codes) is answered.

Wire both: `KryoConfig` registers `QuestStatusRequest/Response` and `ClaimQuestRequest/Response` (`RecordSerializer`) plus `QuestProgress`/`QuestClaimResult` (also records, riding inside the responses) appended after `BuyResetTokenResponse` — append-only, per system design §5. `ServiceRegistry` adds `QuestService questService = new QuestService(dbManager.getJdbi(), characterService)`, same comment style as `ShopService`'s ("spans `quest_claims` and either `accounts` or `characters.inventory`, so it needs the raw `Jdbi`, not `onDemand` proxies"); internally `QuestService` attaches `CharacterDao`/`AccountDao`/`BattleHistoryDao`/`QuestClaimDao` to that one `Jdbi` exactly as `ShopService` attaches `CharacterDao`/`AccountDao` today — no constructor change to `ServiceRegistry`'s other services. Add a `getQuestService()` getter. `KryoPacketRouterFactory`/`KryoPacketRouter` thread `serviceRegistry.getQuestService()` through and register the two new handlers.

## Testing

- `QuestPeriods`: `startOfToday(clock)` returns UTC midnight for a fixed instant; `startOfWeek(clock)` returns the same value for every day Monday–Sunday of one calendar week (assert all seven land on the same Monday-midnight instant), and rolls over correctly across a Sunday→Monday boundary.
- `QuestCatalog.find`: the three known ids resolve; an unknown id returns `null`/empty (whichever convention `SkillTreeCatalog.find` already established — match it exactly).
- `QuestService.getStatus`: a character with 1 of 2 daily wins reports `claimable = false`, `currentWins = 1`; 2+ wins reports `claimable = true` (until claimed, then `alreadyClaimed = true`, `claimable = false` even with enough wins); the repeatable quest's `claimsRemainingToday` decrements as claims are seeded and floors these scenarios independently of the daily/weekly ones (they must not share state).
- `QuestService.claim` — one test per rejection reason: unowned character (`ERROR_UNKNOWN`, gold/inventory untouched); unknown quest id (`QUEST_NOT_FOUND`); insufficient wins (`QUEST_NOT_COMPLETE`); a second claim of the same daily/weekly period (`QUEST_ALREADY_CLAIMED`); a 4th repeatable claim in one day (`QUEST_DAILY_CLAIM_CAP_REACHED`, verify the 3rd still succeeds); an invalid `rewardChoice` on the weekly quest (`QUEST_INVALID_REWARD_CHOICE`); a well-formed claim of each of the three quests lands the right reward in the persisted `accounts`/`characters` row and inserts the claim row with the right `period_start` shape (shared boundary for daily/weekly, `Instant.now()`-distinct for repeatable — assert two same-day repeatable claims get two different `period_start` values but land under the same `quest_id`/day for the cap count).
- `BattleHistoryDao.countWins`/the `won` wiring: seed a mix of won/lost rows across and outside a period boundary, confirm the count is exactly the in-period wins — this is the test that would have caught §0's gap if it had existed before this pass.
- Regression: `RewardServiceTest`/`BattleLeavesInventoryAloneTest`/anything asserting on `BattleHistoryDao.insert`'s signature or `TestDatabase.onlyBattleHistoryRow()`'s shape needs updating for the new `won` column/parameter — confirm every existing assertion about a battle's outcome still passes with the real value wired through (a won battle now correctly reads `won = true`, not just inferable from `gold_delta`).

## Definition of done

`gradlew.bat build` and `gradlew.bat server:test`/`core:test` pass. Update `docs/planning/02-user-stories.md` — P1, P2, P3 to `done` with close-out notes covering: the `battle_history.won` gap found and fixed (§0), the weekly reward catalog decision (Repair Token/Pet Care Kit only, no gear), and the repeatable quest's gold-only reward (no potion content exists yet to grant instead). Flag Epic Q (daily battle cap, character slots) as the natural next slice — it only needs to import `QuestPeriods.startOfToday()` and add the two bonus columns; nothing here blocks it.
