# Claude Code prompt — N1–N2: loadout constraints (weight capacity, weapon durability)

Copy everything below the line into Claude Code. Written 2026-07-15, third implementation slice of the M3.5 progression/economy expansion, building on Epics L and M (both merged). Smaller than M, but touches a real architectural decision worth reading carefully before starting (§0).

---

Add a hard weight-capacity gate to loadout-saving, and make weapons wear down with use. Read first: `docs/planning/01-system-design-combat-engine.md` §17. Also read, in full, the current `Weapon.java`, `ResolvedAction.java`, `ActionResolver.java`, `BattleParticipant.java`, `BattleEngine.java`, `CombatMath.java`, `CharacterService.java` (specifically `saveLoadout`, added by the M pass), `RewardService.java`, `CharacterDao.java`, and `BattleLeavesInventoryAloneTest.java`. You're extending all of these.

## Scope

Implement **N1** (weight capacity) and **N2** (durability). Repair is **not** in scope — it's a shop action (Epic O), priced against durability but not built here; N2 only needs the decrement and the "0 durability = unaffordable" behavior.

## 0. A real design decision, resolved here — read before touching code

§17 doesn't say precisely where durability's mutable state lives when `Loadout` and `Inventory` both hold independent copies of the same weapon (by id). This matters because `BattleParticipant.fromCharacter` currently reads combat weapons straight from `Loadout.weapons()`, and `saveLoadout` (Epic M) lets a client submit an arbitrary `Weapon` object into `Loadout` at any time — if durability lived on both copies independently, they could drift: a post-battle decrement written only to `Inventory` would never be seen by combat, which reads `Loadout`'s own (possibly stale, undamaged) copy.

**Resolution: `Inventory` is the single source of truth for durability; `Loadout` only ever means "this item id is equipped."** `BattleParticipant.fromCharacter` is changed to cross-reference each equipped weapon's id against `character.inventory().weapons()` and use *that* copy's `currentDurability` (falling back to the `Loadout` copy's own value only if the id isn't found in inventory — defensive, shouldn't happen given `saveLoadout` already validates item ownership). This means the post-battle decrement only ever writes `inventory` — reusing `CharacterDao.getInventoryForUpdate`/`updateInventory` exactly as they exist today. **No new `CharacterDao` method, no new C2 exception, no dual-write.**

## 1. Shared pure utility — extract, don't duplicate

`BattleParticipant.fromCharacter` already aggregates `PASSIVE`-skill bonuses inline (Epic M) into `PassiveModifiersComponent`. N1's weight-capacity gate needs the exact same `WEIGHT_CAPACITY_BONUS` sum, but at loadout-save time — a plain server-side call with no Ashley `Engine`, so it can't go through `BattleParticipant`. Extract the aggregation into a new pure class, `core.combat.PassiveEffects` (no Ashley dependency, same free-standing style as `CombatMath`/`DamageCalculator` — `server` already depends on `core`, confirmed by `AttackService` importing `BattleEngine`/`BattleParticipant` directly, so this is reachable from both sides):

```java
public final class PassiveEffects {
    public static int totalWeightCapacityBonus(Loadout loadout) { ... }
    public static int totalDodgeChanceBonus(Loadout loadout) { ... }
    public static int totalCritChanceBonus(Loadout loadout) { ... }
    public static Stats applyStatBonuses(Stats base, Loadout loadout) { ... }
}
```
Refactor `BattleParticipant.fromCharacter` to call these instead of its inline loop (behavior-preserving — same sums, same result, just not duplicated). `CharacterService.saveLoadout` calls `PassiveEffects.totalWeightCapacityBonus` directly for N1's gate below. One formula, one place, both callers agree by construction — no risk of the two ever computing a different number for the same character.

## 2. Weight capacity — hard gate in `saveLoadout` (N1)

`maxCarryWeight = strength × 3 + PassiveEffects.totalWeightCapacityBonus(loadout)` (system design §17). Add this check to `CharacterService.saveLoadout`, after the existing skill-slot-count check and before the item-ownership check: sum `loadout.weapons()`'s `weight()` fields (skills/pets have no weight dimension) and reject if it exceeds `maxCarryWeight`. New `MessageCode.LOADOUT_WEIGHT_EXCEEDED`. This is layered on top of — not a replacement for — the existing soft per-item `comfortableWeight` penalty in `CombatMath.effectiveStrength`, which is untouched.

## 3. Weapon durability (N2)

```java
public record Weapon(
    long id, String name, String description, Rarity rarity, float weight,
    int minAttack, int maxAttack, int staminaCost,
    int maxDurability, int currentDurability
) {}
```
Flat `maxDurability = 20` for every weapon, starter content and future content alike (first pass, per §17). This changes `Weapon`'s constructor arity — fix every existing call site: `BotFactory`'s three hardcoded constants, `RewardService`'s three duplicated bonus-table constants, and any test fixtures constructing a `Weapon` directly (`CombatFixtures.flatWeapon()`, any inline `new Weapon(...)` in tests). **Set every one of them to `20, 20`** (full durability) — a fixture that accidentally ships at `0` would silently become unusable in every test that touches it.

**Unaffordable at 0, reusing the Stamina mechanism — no new branch.** `CombatMath.isAffordable(Weapon weapon, int remainingStamina)` gains the durability check:
```java
public static boolean isAffordable(Weapon weapon, int remainingStamina) {
    return weapon.currentDurability() > 0 && remainingStamina >= weapon.staminaCost();
}
```
This is a pure, no-RNG change — no test's RNG consumption shifts, only weapons already at 0 durability newly become unaffordable, and no existing fixture starts at 0 (per the note above).

## 4. `ResolvedAction` item id — required for durability tracking

`ResolvedAction` currently has no way to identify *which* weapon fired when a pouch holds several. Add it:
```java
public record ResolvedAction(ActionSource source, String label, int frequency, boolean failed, int damage, long itemId) {
    public ResolvedAction(ActionSource source, String label, int frequency, boolean failed, long itemId) {
        this(source, label, frequency, failed, 0, itemId);
    }
}
```
Populate `itemId` with the acted-upon item's `.id()` for `WEAPON`/`SKILL`/`PET` sources; use `0L` for `PUNCH` (no backing record, per `ActionSource`'s own doc) and the Burned-cast entry. Fix every `ResolvedAction` construction site in `ActionResolver` (`chooseCharacterAction`'s weapon/skill/burned/punch branches) accordingly. Populating it uniformly (not just for weapons) costs nothing extra now and sets up Epic O's consumable-charge tracking for free — same "build the shared capability once" reasoning Epic L used for the item-grant path.

## 5. Decrementing durability — extend `RewardService`'s existing transaction

After computing rewards/leveling/milestone bonus (all unchanged), scan `result.turns()` for every distinct weapon `itemId` that appears in a `WEAPON`-source entry (a weapon "fired" if it appears at all — once per battle regardless of hit count, per §17, not once per repeat). Inside the existing `jdbi.useTransaction` block, if any weapon ids fired: read the attacker's inventory (`getInventoryForUpdate`, same row lock as the milestone-bonus grant), find each fired weapon by id, decrement `currentDurability` by 1 (floor at 0, never negative), and write back via the existing `updateInventory` — same method, no new one. This is the same "only touch inventory when something actually changed" pattern the milestone bonus already uses; a battle with no weapon hits touches nothing new.

**Deliberately out of scope:** no `AttackResponse` field reporting "this weapon just broke" this pass — same treatment as the crit flag in the M pass, deferred to the not-yet-built combat screen (M4), not a correctness requirement now.

## Testing

- `PassiveEffects`: extracted methods produce identical sums to what `BattleParticipant.fromCharacter`'s old inline logic did — run the existing M-pass passive tests unchanged against the refactored code as a regression check.
- `CharacterService.saveLoadout`: a loadout whose total weapon weight exceeds `strength × 3 (+ any equipped WEIGHT_CAPACITY_BONUS)` is rejected with `LOADOUT_WEIGHT_EXCEEDED`; a loadout at or under the cap saves; a character with a weight-capacity passive equipped can save a loadout that would otherwise exceed the STR-only cap.
- `CombatMath.isAffordable(Weapon, int)`: a weapon at `currentDurability = 0` is unaffordable regardless of remaining Stamina; a weapon with Stamina left and positive durability is affordable, matching current behavior exactly (regression check against the existing `ActionResolverTest`/`CombatMathTest` suite — no assertion should change).
- `BattleParticipant.fromCharacter`: an equipped weapon's combat-time durability reflects `Inventory`'s copy, not a stale `Loadout` copy — construct a character whose `Loadout` weapon has `currentDurability = 20` but whose matching `Inventory` entry has `currentDurability = 0`, and confirm the assembled participant's weapon is unaffordable (proves the cross-reference, not just that some value was read).
- `RewardService`: a battle where the attacker's weapon fires decrements that weapon's `currentDurability` by exactly 1 in the persisted inventory, regardless of hit count within the battle; a battle where only a skill/punch fires leaves weapon durability untouched; durability never goes below 0 (seed a weapon at `currentDurability = 1`, confirm it lands at 0, not −1, after a firing battle).
- C2 (`BattleLeavesInventoryAloneTest`): no new exception needed (§0) — add a positive test alongside the existing ones confirming a durability decrement lands in the persisted `inventory` JSON via the already-sanctioned `updateInventory` path, and that `onlyTheSanctionedGrantPathCanReachTheStoredItems` still passes unmodified (still exactly two sanctioned writers: `updateInventory`, `updateLoadout`).
- Full existing suite regression: `ActionResolverTest`, `DamageCalculatorTest`, `BattleEngineTest`, `AttackServiceTest` — the `Weapon`/`ResolvedAction` constructor-arity changes touch many call sites; confirm every fixture was updated to full durability (§3) and a real `itemId` (§4), not left at a zero-value default that would silently change behavior.

## Definition of done

`gradlew.bat build` and `gradlew.bat server:test`/`core:test` pass. Update `docs/planning/02-user-stories.md` — N1, N2 to `done` with close-out notes covering: the `PassiveEffects` extraction (and why — avoiding two independent copies of the same bonus math), and the Inventory-as-source-of-truth-for-durability decision (§0), since that resolves an ambiguity the original design left open rather than following an already-locked instruction. Flag Epic O (shop, repair pricing) as the natural next slice — it's what actually lets a player recover from 0 durability.
