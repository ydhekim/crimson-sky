# Claude Code prompt — L1–L3: character progression (leveling, stat points, skill points)

Copy everything below the line into Claude Code. Written 2026-07-15, first implementation slice of the M3.5 progression/economy expansion (nine design sections, §15–§23, locked this session). This is the foundation epic — Epics M through T all build on the currencies and level field this pass introduces.

---

Extend `RewardService.applyRewards` to compute leveling, stat points, and skill points alongside the existing Gold/Exp/Elo payout, and add a new request/response pair so a player can spend earned stat points. Do not touch matchmaking/opponent-selection, `BattleEngine`/combat resolution, or build any part of the skill tree — this pass only builds the currency/level foundation §16 (skill tree) will spend later.

Read first: `docs/planning/01-system-design-combat-engine.md` §15 (the full formulas and reasoning) — and note the correction below before implementing, it resolves a real inconsistency in that section's own worked example. Also read the current `RewardService.java`, `RewardOutcome.java`, `CharacterDao.java`, `CharacterEntity.java`, `AttackResponse.java`, `AttackRequestHandler.java`, `CreateCharacterResponse.java`/`CreateCharacterRequestHandler.java` (for the `success`/`message`/payload response shape), `KryoPacketRouter.java`, `ServiceRegistry.java`, `MessageCode.java`, `Stats.java`, and `CharacterCreationScreen.java` (for `MAX_STAT_VALUE`/`INITIAL_STAT_POOL`) — you're extending all of these, not replacing them.

## Scope

Implement **L1**, **L2**, and **L3** (`docs/planning/02-user-stories.md`, Epic L). Do not implement anything from Epic M (skill tree) — `skill_points` accumulates here but has no spend logic until that epic.

## 0. Exp-curve correction — implement this, not a literal reading of §15

§15 states `cumulativeExpForLevel(L) = 8 × L²` and separately claims "24 exp for level 1→2." Taken literally, `8×L²` gives a level-2 threshold of 32, not 24 — the two statements are inconsistent unless the curve is anchored to 0 at level 1 rather than 8. The increment formula (`8×(2L+1)`, which *does* correctly equal 24 for L=1) is the one to trust. Implement the threshold function as:

```java
static long expNeededForLevel(int level) {
    // 8×level² anchored so level 1 needs 0 (not 8) cumulative exp — matches §15's own worked
    // example ("24 exp for level 1→2"), which a literal reading of cumulativeExpForLevel wouldn't.
    return 8L * level * level - 8L;
}
```
A character at level `L` levels up to `L+1` when cumulative `experience` ≥ `expNeededForLevel(L+1)`. Verify: `expNeededForLevel(2) - expNeededForLevel(1) = 24 - 0 = 24`. Cap at level 50 — never level past it regardless of exp.

## 1. Migration — `V9__Add_Character_Progression_Currencies.sql`

```sql
ALTER TABLE characters ADD COLUMN unspent_stat_points INTEGER NOT NULL DEFAULT 0;
ALTER TABLE characters ADD COLUMN skill_points INTEGER NOT NULL DEFAULT 0;
```

## 2. Shared stat cap constant — fixes a real drift risk, not cosmetic

`CharacterCreationScreen.MAX_STAT_VALUE = 20` is a UI-only placeholder today (never an intended lifetime ceiling) and lives in `core`, unreachable from server-side validation in a different module. Add the real cap as a shared constant in `common`, e.g. `public static final int MAX_STAT_VALUE = 60;` on `Stats` (common module, reachable by both `core` and `server`). Update `CharacterCreationScreen` to reference `Stats.MAX_STAT_VALUE` instead of its own private constant (delete the private one). This is the "one shared constant both call sites reference" §15 asks for — without it, the creation screen and the new spend-validation below would silently drift apart the next time either cap changes.

## 3. `RewardOutcome` — extend, don't replace

```java
public record RewardOutcome(
    int goldDelta, long expDelta, int eloDelta,
    int skillPointsGained,
    int levelsGained, int statPointsGained,
    String bonusRewardGranted   // null when no milestone bonus fired
) {
    public static RewardOutcome none() {
        return new RewardOutcome(0, 0L, 0, 0, 0, 0, null);
    }
}
```

This breaks every existing 3-arg `new RewardOutcome(...)` call site at compile time. Fix `RewardServiceTest` (five assertions, lines ~79/93/120/134/142 as of this writing) to the new 7-arg shape — see the Testing section below for what values they should now expect, since L2's skill points apply to every battle outcome, not just leveling ones.

## 4. `CharacterDao` — extend the write path, don't add a parallel one

Rename `addExperienceAndElo` to `applyBattleProgress` (its only call site is `RewardService`, safe to rename) and extend it to write everything in one statement:

```java
@SqlUpdate("UPDATE characters SET experience = experience + :expDelta, elo = elo + :eloDelta, " +
    "level = :newLevel, unspent_stat_points = unspent_stat_points + :statPointsGained, " +
    "skill_points = skill_points + :skillPointsGained WHERE id = :characterId")
void applyBattleProgress(@Bind("characterId") long characterId, @Bind("expDelta") long expDelta,
                          @Bind("eloDelta") int eloDelta, @Bind("newLevel") int newLevel,
                          @Bind("statPointsGained") int statPointsGained,
                          @Bind("skillPointsGained") int skillPointsGained);
```
`newLevel` is the absolute resulting level (`currentLevel + levelsGained`), not a delta — `level` isn't itself additive the way the other columns are.

For L3's item grant, add two new methods reusing the `@Json` JDBI convention `CharacterEntity` already uses for `inventory`/`loadout` (Jackson2Plugin handles the (de)serialization — do not hand-roll JSON string manipulation):

```java
@SqlQuery("SELECT inventory FROM characters WHERE id = :characterId FOR UPDATE")
@Json
Optional<Inventory> getInventoryForUpdate(@Bind("characterId") long characterId);

@SqlUpdate("UPDATE characters SET inventory = :inventory WHERE id = :characterId")
void updateInventory(@Bind("characterId") long characterId, @Bind("inventory") @Json Inventory inventory);
```
The `FOR UPDATE` row lock matches the concurrency approach §17 already settled on for durability (lock the row inside the transaction rather than build real optimistic-concurrency handling) — this is simply the first place that approach actually gets built.

For L1's stat-point spend, add a guarded conditional update — atomic, no separate read-then-write race on the points balance itself:

```java
@SqlUpdate("UPDATE characters SET stats = :stats, unspent_stat_points = unspent_stat_points - :spent " +
    "WHERE id = :characterId AND unspent_stat_points >= :spent")
int spendStatPoints(@Bind("characterId") long characterId, @Bind("stats") @Json Stats stats, @Bind("spent") int spent);
```
Return type `int` (JDBI reports rows affected) — `0` means the guard failed (client tried to spend more than they have), `1` means it succeeded. The caller still needs the character's *current* stats first (a plain read) to compute the merged result before calling this — accept the small TOCTOU gap between that read and this write, same simplification already accepted for durability's concurrency model (unrealistic for one account to run two simultaneous spend requests for the same character).

## 5. `RewardService.applyRewards` — extend the existing transaction

After computing the existing Gold/Elo payout and this battle's `expDelta`:

1. **Skill points (L2):** `won ? 3 : 1` — flat, no Elo-gap term, same shape as the existing loss-branch simplicity.
2. **Level-up loop (L1):** starting from `attacker.data().level()` and `attacker.data().experience() + expDelta` (the character record loaded earlier in this method already has both), loop `while (currentLevel < 50 && newExperience >= expNeededForLevel(currentLevel + 1)) { currentLevel++; levelsGained++; }`. Grant `3 × levelsGained` stat points.
3. **Every-10 bonus roll (L3):** for each multiple of 10 crossed during that loop (10/20/30/40/50 — a multi-level jump could cross more than one), roll a 10% chance. On success, pick one of the three starter weapons at random (Twin Daggers/Steel Longsword/Warhammer — same definitions `BotFactory` already hardcodes; duplicate them here rather than reaching into `BotFactory`'s private constants) and append it to the attacker's `Inventory.weapons()`. This is a deliberate v1.0 placeholder: §15 also mentions pets and skill-restoration scrolls as possible rewards, but neither has a real acquisition mechanic in code yet (pets aren't grantable outside character creation, and consumable/scroll items don't exist as a concept until Epic O) — don't invent placeholder item types for those now. Record what was granted (e.g. the weapon's name) in `bonusRewardGranted`, or leave it `null` if no milestone was crossed or the roll failed.
4. Extend the existing `jdbi.useTransaction` block: call `applyBattleProgress` with the new level/stat-points/skill-points values, and — only when a bonus item was actually granted — also call `getInventoryForUpdate` + `updateInventory` inside the *same* transaction (read-modify-write the attacker's inventory, appending the new weapon to the existing array). All writes for one battle commit or roll back together, same guarantee the transaction already provides for Gold/Exp/Elo/battle_history.
5. Return the extended `RewardOutcome`.

Keep the existing failure handling: any exception in the transaction logs loudly and returns `RewardOutcome.none()` rather than propagating.

## 6. Wire the new fields into the response

`AttackResponse` (common) — add the new fields so the client can show them:
```java
public record AttackResponse(long battleId, String opponentDisplayName, boolean won,
                              Array<Array<ResolvedAction>> turns,
                              int goldDelta, long expDelta, int eloDelta,
                              int skillPointsGained, int levelsGained, int statPointsGained,
                              String bonusRewardGranted) {}
```
Update `AttackResult.toResponse(RewardOutcome)` to fold the new fields in. No `KryoConfig` change needed — `AttackResponse` is already registered, and `RecordSerializer` picks up new components automatically (same note as the C1 pass).

## 7. New packet pair — spending stat points

```java
// common/network/packet
public record AllocateStatPointsRequest(long characterId, Stats delta) {}
public record AllocateStatPointsResponse(boolean success, String message, Stats newStats, int unspentStatPoints) {}
```
Register both in `KryoConfig`, appended after the existing `AttackResponse` registration (append-only, never reorder — system design §5).

Add two `MessageCode` entries: `STAT_POINTS_INSUFFICIENT` and `STAT_CAP_EXCEEDED`.

Add `CharacterService.allocateStatPoints(long accountId, long characterId, Stats delta)`:
1. Ownership guardrail — same pattern as every other character-scoped action (`isCharacterOwnedBy`).
2. Load the character, compute `sum(delta)` (all 8 components, reject negative components) against `unspentStatPoints` — fail with `STAT_POINTS_INSUFFICIENT` if it exceeds the balance.
3. Compute the merged `Stats` (current + delta, component-wise) and check every one of the 8 resulting values ≤ `Stats.MAX_STAT_VALUE` — fail with `STAT_CAP_EXCEEDED` if any component would exceed it.
4. Call `characterDao.spendStatPoints(characterId, mergedStats, sum(delta))` — if it returns `0` (lost a race against the balance), fail with `STAT_POINTS_INSUFFICIENT` rather than silently succeeding.
5. Return success with the merged stats and remaining balance.

New `AllocateStatPointsRequestHandler` (server/network/handler), same shape as `CreateCharacterRequestHandler` — unauthenticated connections and failed ownership checks are dropped with a log line, not answered. Wire it into `KryoPacketRouter`'s constructor/map and `KryoPacketRouterFactory` the same way every other handler is wired (no new service needed — `CharacterService` is already threaded through both).

## Testing

- `expNeededForLevel`: verify the corrected formula directly — `expNeededForLevel(1) == 0`, `expNeededForLevel(2) == 24`, `expNeededForLevel(3) == 64`, matches the increment formula `8×(2L+1)` by construction.
- Level-up loop: a single battle's exp delta that crosses exactly one threshold grants 3 stat points and `levelsGained == 1`; an exp delta large enough to cross two thresholds in one battle (construct a fixture where this is possible, e.g. a large Elo-gap bonus) grants 6 stat points and loops correctly; a character already at level 50 never advances further regardless of exp delta.
- Every-10 bonus: with a seeded `Random`, assert the roll fires at exactly the configured 10% rate and grants a weapon append to `inventory.weapons()` only on a crossed multiple of 10, never on other level-ups.
- `RewardServiceTest`'s five existing assertions: update each `new RewardOutcome(...)` call to the new 7-arg shape. For win-branch assertions (25 gold / 50 exp / +16 elo), the skill-point delta is now `3` (win); for the loss-branch assertion (5 gold / 10 exp / -16 elo), it's `1` (loss). Confirm none of the existing fixtures' starting level/exp accidentally crosses a level-up threshold with the corrected formula (if a fixture does, that's fine — assert the level-up fields explicitly rather than treating it as a broken test).
- `AllocateStatPointsRequestHandler`/`CharacterService.allocateStatPoints`: ownership rejection, over-budget delta (`STAT_POINTS_INSUFFICIENT`), over-cap delta (`STAT_CAP_EXCEEDED`), and a valid partial spend leaving the correct remaining balance.
- C2 (`BattleLeavesInventoryAloneTest`): `noCharacterUpdateStatementCanEvenReachTheStoredItems` must be updated **deliberately** to allow `updateInventory` by name (assert every *other* `CharacterDao` method touching `inventory`/`loadout` still fails the check) — this is the exact moment that test's docstring anticipated, not a regression. Add a new positive test alongside it: trigger a guaranteed bonus roll (seeded `Random`, force a milestone crossing) and assert the granted weapon actually appears in the character's persisted `inventory.weapons()` afterward — the first real exercise of the new write path.
- Existing B4/C1-C3 tests (`AttackServiceTest`, `AttackRequestHandlerTest`, `BotFactoryTest`) must still pass — this pass extends `RewardService`, it doesn't change battle resolution.

## Definition of done

`gradlew.bat build` and `gradlew.bat server:test`/`core:test` pass. Update `docs/planning/02-user-stories.md` — L1, L2, L3 all to `done` with close-out notes in the existing style. If the exp-curve correction in §0 feels like it should be reflected back into system design §15's own text (its worked example already assumes the corrected numbers, but the formula statement above it doesn't), flag that back rather than silently leaving the doc inconsistent — that's a planning-doc fix, not something to patch quietly mid-implementation.
