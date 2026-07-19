# Claude Code prompt — O2: potions (`SkillType.CONSUMABLE`)

Copy everything below the line into Claude Code. Written 2026-07-16, fifth implementation slice of the M3.5 progression/economy expansion, building on Epics L, M, N and O1/O3 (all merged).

---

Add a third `SkillType`, `CONSUMABLE`: an equippable potion that automatically heals a resource mid-battle when it drops below a threshold, gated by a persisted charge count. Read first: `docs/planning/01-system-design-combat-engine.md` §18 (the "Potions" subsection, including the two 2026-07-16 amendments — restore amount is flat, not a roll, and `charges` deplete per-trigger, not per-battle). Also read, in full, the current `Skill.java`, `SkillType.java`, `ActionSource.java`, `ResolvedAction.java`, `BattleEngine.java`, `ActionResolver.java`, `PetResolver.java`, `CombatMath.java`, `BattleParticipant.java`, `SkillSlotComponent.java`/`PetSlotComponent.java` (the components you're adding a sibling of), `RewardService.java`, and `BattleLeavesInventoryAloneTest.java`.

## Scope

Implement **O2** in full: the new `SkillType`, the `Skill` fields, the pre-cascade trigger check in `BattleEngine`, and the persisted-charge wear/read path (mirroring N2/O3's Inventory-is-source-of-truth pattern, with one deliberate difference spelled out in §0). No new packets, handlers, or `MessageCode`s — potion *acquisition* is a content-authoring question (same "Epic E makes this data-driven" deferral `BotFactory`'s weapons/skills already carry), out of scope here.

## 0. Two decisions locked after the O1/O3 pass, read before touching code

**Restore amount is a flat quantity, not a range-plus-stat-bonus roll.** §18 originally sketched potions using the same `min`/`max`-plus-stat-bonus shape as damage; that's been superseded. A potion's `restoreAmount` is a single flat int — no RNG draw at all when it triggers. Potency is a property of the potion, not the drinker: content is sized by authoring separate instances (a "Small Health Potion" at 100, "Medium" at 200, "Large" at 300 — same pattern as `BotFactory`'s three separately-authored starter weapons), never a formula. This is also why resolving a potion trigger is the **only** branch of the whole cascade that consumes zero RNG.

**`charges` is persisted and depletes per-trigger, not per-battle.** This is the one place potions genuinely diverge from the N2/O3 shape they otherwise mirror. Weapon durability and pet health lose at most one point *per battle*, however many times the item acted that battle (a `Set` of fired ids). A potion's `charges` is different: a single long fight can genuinely cross its threshold and heal more than once, and each trigger burns one real charge — so tracking needs a **tally** (how many times did this potion id fire this battle), not a set (did it fire at all). `charges` lives on the `Skill` record itself, exactly like `Weapon.currentDurability`/`Pet.currentHealth` — `Inventory` is the source of truth, `Loadout`'s copy can go stale the identical way, and combat cross-references the equipped id against inventory at battle setup. There is no "repair" for a spent potion (`charges == 0` just means it never triggers again) — restocking is out of scope here, the same way shop pricing was out of scope for N2/O3 until O1 existed.

## 1. `ResourceType` and `Skill`'s new fields

```java
public enum ResourceType { HEALTH, MANA, STAMINA }
```
`SkillType` gains `CONSUMABLE` (append after `PASSIVE` — ordinal-based enum registration, append-only per system design §5). `Skill` gains four trailing fields, meaningful only when `type() == CONSUMABLE` (same convention as the existing passive-only trio being meaningless on `ACTIVE`):
```java
public record Skill(
    long id, String name, String description, SkillType type,
    int manaCost, Difficulty difficultyToAct, int minAttack, int maxAttack,
    PassiveEffectType passiveEffect, int passiveMagnitude, StatName passiveTargetStat,
    ResourceType restoresResource, int thresholdPercent, int restoreAmount, int charges
) {
    /** A copy with `charges` reduced by `times`, floored at 0 — this battle's tally of actual triggers (§18). */
    public Skill consumed(int times) {
        return new Skill(id, name, description, type, manaCost, difficultyToAct, minAttack, maxAttack,
            passiveEffect, passiveMagnitude, passiveTargetStat,
            restoresResource, thresholdPercent, restoreAmount, Math.max(0, charges - times));
    }

    /** A copy carrying `remaining` as its current charge count — the combat-time Inventory cross-reference. */
    public Skill withCharges(int remaining) {
        return new Skill(id, name, description, type, manaCost, difficultyToAct, minAttack, maxAttack,
            passiveEffect, passiveMagnitude, passiveTargetStat,
            restoresResource, thresholdPercent, restoreAmount, remaining);
    }
}
```
Fix every existing `new Skill(...)` call site (a repo-wide grep for `new Skill(` finds them: `BotFactory`'s `SPARK`/`LIGHTNING_BOLT`/`METEOR`; `SkillTreeCatalog`'s passive-grant construction; test fixtures in `PouchResolutionTest`, `ActionResolverTest`, `PassiveEffectsTest`, `BattleParticipantPassivesTest`, `DeterminismTest`, `SaveLoadoutRequestHandlerTest`, `CharacterServiceSaveLoadoutWeightTest`) — every one of them is `ACTIVE` or `PASSIVE`, so append `null, 0, 0, 0` (meaningless for those types, same treatment the existing passive-only trio already gets on `ACTIVE` skills).

## 2. `ConsumableSlotComponent` — a new pouch, sibling of `SkillSlotComponent`/`PetSlotComponent`

```java
public class ConsumableSlotComponent implements Component, Poolable {
    public final Array<Skill> equipped = new Array<>();      // priority order, CONSUMABLE only
    public final IntArray remainingCharges = new IntArray();  // index-aligned with `equipped`, battle-scoped

    @Override
    public void reset() {
        equipped.clear();
        remainingCharges.clear();
    }
}
```
`IntArray` (`com.badlogic.gdx.utils.IntArray`) over `Array<Integer>`, matching the "prefer `gdx.utils` collections" convention. `remainingCharges` is seeded once at battle setup from each equipped potion's *Inventory* charge count (§0) and decremented in real time as the battle progresses — a potion with 2 charges cannot trigger a third time within one long fight.

## 3. `BattleParticipant.fromCharacter` — populate the pouch, cross-reference charges

Add `atInventoryCharges(Skill equipped, Inventory inventory)` next to `atInventoryDurability`/`atInventoryPetHealth` — identical shape (scan `inventory.skills()` for a matching id by `type() == CONSUMABLE`, return `equipped.withCharges(owned.charges())`, else fall back to `equipped`'s own value for the bot case). Extend the existing skills loop:
```java
if (loadout.skills() != null) {
    for (Skill skill : loadout.skills()) {
        if (skill.type() == SkillType.ACTIVE) {
            skillSlot.equipped.add(skill);
        } else if (skill.type() == SkillType.CONSUMABLE) {
            Skill crossReferenced = atInventoryCharges(skill, character.inventory());
            consumableSlot.equipped.add(crossReferenced);
            consumableSlot.remainingCharges.add(crossReferenced.charges());
        }
        // PASSIVE skills are handled separately below, via PassiveEffects — unchanged.
    }
}
```
Create and `entity.add(consumableSlot)` alongside the existing `weaponSlot`/`skillSlot`/`petSlot` adds (always added, possibly empty — same convention). Add the matching `ComponentMapper<ConsumableSlotComponent>` and a `consumables()` accessor on `BattleParticipant`, mirroring `pet()`.

## 4. `ConsumableResolver` — the pure decision layer, Ashley-free like its siblings

`CombatMath` gains the threshold check, integer-only to avoid float division:
```java
/** True when current is at or below thresholdPercent of max (§18) — a max of 0 is never "below". */
public static boolean isBelowThreshold(int current, int max, int thresholdPercent) {
    return max > 0 && (long) current * 100 <= (long) thresholdPercent * max;
}
```
New `core.combat.ConsumableResolver`, in the exact style of `ActionResolver`/`PetResolver` — a plain function of primitives and pouches, no Ashley, no RNG (§0 — nothing to roll):
```java
public final class ConsumableResolver {
    private ConsumableResolver() {}

    record ConsumableActionResolution(int equippedIndex, ResolvedAction action, ResourceType resource, int restoreAmount) {}

    static ConsumableActionResolution chooseConsumable(
            int currentHealth, int maxHealth, int currentMana, int maxMana,
            int currentStamina, int maxStamina, Array<Skill> equipped, IntArray remainingCharges) {
        for (int i = 0; i < equipped.size; i++) {
            Skill skill = equipped.get(i);
            if (remainingCharges.get(i) <= 0) {
                continue;
            }
            boolean triggers = switch (skill.restoresResource()) {
                case HEALTH -> CombatMath.isBelowThreshold(currentHealth, maxHealth, skill.thresholdPercent());
                case MANA -> CombatMath.isBelowThreshold(currentMana, maxMana, skill.thresholdPercent());
                case STAMINA -> CombatMath.isBelowThreshold(currentStamina, maxStamina, skill.thresholdPercent());
            };
            if (triggers) {
                ResolvedAction action = new ResolvedAction(ActionSource.CONSUMABLE, skill.name(), 1, false,
                    skill.restoreAmount(), skill.id());
                return new ConsumableActionResolution(i, action, skill.restoresResource(), skill.restoreAmount());
            }
        }
        return null;
    }
}
```
Note the 6-arg `ResolvedAction` constructor is used directly (not the 5-arg decision-layer convenience) — a potion's `damage` field is known immediately (the flat `restoreAmount`), unlike weapon/skill/pet hits whose real total needs the mitigation math `BattleEngine.applyEntry` applies later. For this source, `ResolvedAction.damage` means **amount restored to the attacker**, not damage dealt to the defender — worth a one-line note on `ResolvedAction`'s javadoc next to the existing per-source explanation.

## 5. `ActionSource.CONSUMABLE` and `BattleEngine`'s one new check

Append `CONSUMABLE` to `ActionSource` (after `PET` — append-only, same enum-ordinal reasoning as `SkillType`). In `BattleEngine.resolveResultSet`, insert the check **before** Step 1's `ActionResolver.chooseCharacterAction` call, replacing that step's result when a potion fires:

```java
ConsumableSlotComponent consumables = attacker.consumables();
ConsumableActionResolution potionRes = ConsumableResolver.chooseConsumable(
    attacker.health().currentHealth, attacker.health().maxHealth,
    attacker.mana().currentMana, attacker.mana().maxMana,
    attacker.stamina().currentStamina, attacker.stamina().maxStamina,
    consumables.equipped, consumables.remainingCharges);

ResolvedAction characterEntry;
if (potionRes != null) {
    applyRestore(attacker, potionRes);
    consumables.remainingCharges.set(potionRes.equippedIndex(),
        consumables.remainingCharges.get(potionRes.equippedIndex()) - 1);
    characterEntry = potionRes.action(); // the turn's entire character action — cascade skipped
} else {
    CharacterActionResolution charRes = ActionResolver.chooseCharacterAction(
        stats, attacker.weapons().equipped, attacker.skills().equipped,
        attacker.mana().currentMana, attacker.stamina().currentStamina, rng);
    spendResource(attacker, charRes);
    characterEntry = applyEntry(charRes.action(), charRes.minAttack(), charRes.maxAttack(),
        charRes.pathStatValue(), defender, defenderDef, defenderSpeed, defenderDodgeBonus, attackerCritBonus);
}
```
The defender-derived locals (`defenderDef`, `defenderSpeed`, `defenderDodgeBonus`, `attackerCritBonus`) need hoisting above this branch since they no longer depend only on the "else" path. **The pet decision (Step 2) is unaffected either way** — it already runs independent of the character outcome (even a Burned cast still rolls the pet), so no change there beyond it now also running after a potion turn. `applyRestore` is a small new private helper: adds `restoreAmount` to the matching component's current value, capped at its max (`Math.min(max, current + amount)`), for whichever `ResourceType` the resolution names.

If `potionRes` is always `null` (no `CONSUMABLE` skills equipped, the case for every existing test and every current bot), this whole branch is dead code and the `else` path is byte-for-byte the pre-O2 cascade — no existing test's RNG consumption or outcome changes.

## 6. `RewardService` — tally triggers, decrement charges, same shared transaction

A potion "fires" is counted differently from a weapon "fires" (§0) — a tally, not a set:
```java
/** How many times each of the attacker's equipped potions triggered this battle (§18) — a tally, not a
 * set, because unlike durability/pet-health a potion's charges deplete once per actual trigger. */
static Map<Long, Integer> consumableTriggerCounts(AttackResult result) {
    Map<Long, Integer> counts = new HashMap<>();
    if (result.turns() == null) {
        return counts;
    }
    for (Array<ResolvedAction> turn : result.turns()) {
        for (ResolvedAction action : turn) {
            if (action.source() == ActionSource.CONSUMABLE && action.itemId() != 0L) {
                counts.merge(action.itemId(), 1, Integer::sum);
            }
        }
    }
    return counts;
}
```
Extend `updateInventory`'s signature with `Map<Long, Integer> consumableTriggerCounts`, and add a third loop alongside the weapons/pets ones, over `inventory.skills()`: for each `Skill` whose id is a key in the map, `skills.set(i, skill.consumed(counts.get(skill.id())))`, folding into the same `changed` flag. Update `applyRewards`'s call site to compute `consumableTriggerCounts(result)` before the transaction (same "decide the whole outcome before any write" reasoning as the milestone roll and the other two wear computations) and pass it through, extending the write-guard condition (`if (!bonusWeapons.isEmpty() || !firedWeaponIds.isEmpty() || !firedPetIds.isEmpty() || !consumableTriggerCounts.isEmpty())`). Construct the final `Inventory` with the updated `skills` array — `new Inventory(weapons, skills, pets, inventory.consumables())`, `consumables` (the shop's key→count map, unrelated despite the similar name) still riding through untouched.

## Testing

- `Skill.consumed`/`withCharges`: pure record-copy behavior — `consumed(2)` on `charges = 3` yields `1`; `consumed(5)` on `charges = 3` floors at `0`, never negative.
- `CombatMath.isBelowThreshold`: `50` current of `100` max at `50` threshold triggers (at-or-below, not strictly below); `51`/`100`/`50` does not; a `max` of `0` never triggers regardless of `thresholdPercent`.
- `ConsumableResolver`: the first equipped potion below its threshold with charges left triggers, in priority order (a healthy potion in slot 0 is skipped in favor of slot 1 if slot 1's resource is the one that's low); a potion with `remainingCharges == 0` is skipped even though its resource is below threshold; no potion equipped, or none triggering, returns `null`; confirm zero RNG is needed by the method signature itself (no `SplittableRandom` parameter — this is structural, not just behavioral).
- `BattleEngine`/integration: a character with a Health potion equipped and HP driven below its threshold triggers it on the next turn, heals by exactly `restoreAmount` (capped at `maxHealth`), consumes exactly one charge, and the normal weapon/skill cascade does not fire that turn (assert on `ResolvedAction.source() == CONSUMABLE` and that no weapon/skill entry appears); the pet still acts independently that same turn if its own gate rolls succeed; a potion at `0` charges never triggers regardless of how low the resource is; a battle with no `CONSUMABLE` skills equipped produces byte-for-byte the same turns as before this change (regression check against `DeterminismTest`/`ActionResolverTest`/`BattleEngineTest`'s existing fixtures, none of which equip one).
- `BattleParticipant`: sibling of `BattleParticipantDurabilityTest`/`BattleParticipantPetHealthTest` — a potion whose `Loadout` copy shows `charges = 5` but whose `Inventory` copy shows `charges = 0` is assembled with the *Inventory* value and never triggers, proving the cross-reference rather than just a wired-through default; an equipped potion missing from inventory (the bot case) keeps its own `charges` value.
- `RewardService`: a battle where an equipped potion triggers three times (a long fight, HP dips below threshold repeatedly) decrements its persisted `charges` by exactly 3, not 1 — the tally-vs-set distinction (§0) made observable; a potion that never triggered is untouched; charges never go below 0; a battle with a firing weapon, an acting pet, *and* a triggering potion all land in the one `updateInventory` write (extend `RewardServicePetHealthTest`'s style, or add a sibling covering all three at once).
- C2 (`BattleLeavesInventoryAloneTest`): no new exception needed (§0/§18's standing rule) — extend `petWearAndEveryShopWriteReachInventoryThroughTheSameSanctionedPath`'s sibling coverage (or add one) confirming a potion's charge decrement lands through the same sanctioned `updateInventory` path, and `onlyTheSanctionedGrantPathCanReachTheStoredItems` still passes unmodified (still exactly two sanctioned writers).
- Full existing suite regression: every fixture touching `Skill` construction needs its arity fixed (§1) — confirm none were left one argument short (a compile error, not a silent bug, but worth calling out since the list is long) and that no `ACTIVE`/`PASSIVE` fixture accidentally ships a nonzero `charges` or a non-null `restoresResource`, which would be inert today but is exactly the kind of stray value that turns into a real bug the day something reads it.

## Definition of done

`gradlew.bat build` and `gradlew.bat server:test`/`core:test` pass. Update `docs/planning/02-user-stories.md` — O2 to `done` with close-out notes covering: the flat-restore-amount and per-trigger-tally decisions (§0, both already reflected in this file's O2 acceptance criteria from the design-doc amendment), and that potion *content* (actually authoring a Small/Medium/Large Health Potion, or any potion at all) is still nobody's job yet — same "data-driven content is Epic E's problem" deferral already carried by every other item type in this codebase. Note that all of O1/O2/O3/O4's groundwork is now in place except O4's earning side (Epic P, quests).
