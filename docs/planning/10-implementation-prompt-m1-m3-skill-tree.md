# Claude Code prompt — M1–M3: skill tree (content, learn/upgrade, passives in combat)

Copy everything below the line into Claude Code. Written 2026-07-15, second implementation slice of the M3.5 progression/economy expansion, building on Epic L (merged). This is a large pass — it also builds two capabilities the design docs assumed existed but don't: a crit mechanic (§14, locked this session) and a loadout-save endpoint (nothing today lets a player equip anything after character creation).

---

Build the skill tree end to end: 20 hardcoded v1.0 nodes, a learn/upgrade endpoint, and — the part that makes passives real — aggregate passive bonuses computed once per battle and actually consumed by combat resolution. Read first: `docs/planning/01-system-design-combat-engine.md` §14 (crit/dodge design, resolved this session) and §16 (skill tree). Also read, in full, the current `Skill.java`, `SkillType.java`, `Loadout.java`, `Faction.java`, `Character.java`, `CharacterEntity.java`, `ActionResolver.java`, `BattleParticipant.java`, `BattleEngine.java`, `DamageCalculator.java`, `CombatMath.java`, `CharacterService.java`, `CharacterDao.java`, `RewardService.java` (the `Jdbi`-transaction pattern you'll mirror), and `BattleLeavesInventoryAloneTest.java` (the C2 guard you're adding a third exception to). You're extending all of these, not replacing them.

## Scope

Implement **M1** (tree content + data model), **M2** (learn/upgrade), and **M3** (shared slot pool actually affecting combat) — plus two prerequisites the design docs assumed but the code doesn't have: the crit mechanic itself, and a `SaveLoadoutRequest` endpoint (today `Loadout` is only ever set once, at character creation, as `new Loadout(null, null, null)` — nothing lets a player equip a learned skill afterward).

**Do not implement:** M4 (single-node restore) or M5 (full reset) — both need a skill-restoration scroll, which doesn't exist as an item until Epic O (shop). Do not implement Epic N's weight cap or wire `RESOURCE_COST_REDUCTION` into the affordability checks in `ActionResolver` — no v1.0 tree node uses either effect type this pass (see §4 below); they exist as valid enum values for future content, not consumed by combat yet. Do not rename `Faction.A`/`Faction.B` to `CRIMSON_ACCORD`/`SKYBORN` — still deferred per §14's own note; this pass only decides which existing letter maps to which faction node (§4).

## 0. The crit mechanic — new, foundational (system design §14, locked 2026-07-15)

Dodge exists (`DamageCalculator.rollDodge`, capped at 75%). Crit does not exist anywhere in the codebase — no roll, no multiplier. Both faction nodes need it (Crimson's is a crit-chance bonus), so it has to be built first.

**Definition, per §14's locked sketch:** a hit naturally crits when its damage draw lands in the top 20% of the item's range — `rawRoll >= minAttack + 0.8 * (maxAttack - minAttack)`. A crit applies a flat `×1.5` multiplier to that hit's final, post-mitigation damage.

**Critical guard, or you will break dozens of existing tests:** this definition is meaningless — and dangerous — when `minAttack == maxAttack`. `CombatFixtures.flatWeapon()` is `100/100`; under a naive implementation, `100 >= 100 + 0.8*(0)` is trivially always true, meaning **every existing golden-path test using the flat-damage fixture would suddenly crit on every hit**, silently changing `EXPECTED_HIT_DAMAGE` from 150 to 225 everywhere it's asserted. Guard explicitly: natural crit is only possible when `maxAttack > minAttack`; a zero-width range can never naturally crit.

**Crimson's flat bonus** is a second, independent check: an extra d100 roll that forces a crit regardless of the natural roll, but **only consumed when the attacker's aggregate crit bonus is greater than zero** — skip the roll entirely otherwise, so every existing seeded-RNG test with no crit passive equipped sees identical RNG consumption to before this change.

```java
// DamageCalculator — extend the existing signature, don't add a parallel method.
public static int rollHitDamage(int minAttack, int maxAttack, int pathStatValue,
                                int defenderBaseDef, int critChanceBonus, SplittableRandom rng) {
    int rawRoll = randomInt(minAttack, maxAttack, rng);
    int rawDamage = rawRoll + CombatMath.statBonus(pathStatValue);
    int itemPower = CombatMath.itemPower(minAttack, maxAttack);
    double mitigationFactor = (double) itemPower / (itemPower + defenderBaseDef);
    int finalDamage = (int) Math.round(rawDamage * mitigationFactor);

    boolean crit = isNaturalCrit(rawRoll, minAttack, maxAttack)
        || (critChanceBonus > 0 && rng.nextInt(CombatMath.ROLL_BOUND) < critChanceBonus);
    return crit ? Math.round(finalDamage * CRIT_MULTIPLIER) : finalDamage;
}

static boolean isNaturalCrit(int rawRoll, int minAttack, int maxAttack) {
    if (maxAttack <= minAttack) {
        return false; // a flat range has no "top band" — see the guard note above
    }
    double threshold = minAttack + 0.8 * (maxAttack - minAttack);
    return rawRoll >= threshold;
}
```
`CRIT_MULTIPLIER = 1.5f`, package-private constant alongside `MAX_DODGE_CHANCE`. `BattleEngine.applyEntry` gains a `critChanceBonus` parameter (the attacker's, read from their new `PassiveModifiersComponent`, §1) threaded into `rollHitDamage`; `rollDodge` similarly gains the defender's `dodgeChanceBonus`, added to the speed-derived chance before the existing 75% cap (`Math.min(MAX_DODGE_CHANCE, Math.round(defenderSpeed * 0.75f) + dodgeChanceBonus)`).

**Deliberately out of scope:** `ResolvedAction` gets no `wasCrit` field this pass — the mechanic works and changes damage output correctly, but flagging a crit distinctly back to the client is display polish for the not-yet-built M4 combat screen, not a correctness requirement now.

## 1. New component — `PassiveModifiersComponent`

```java
public class PassiveModifiersComponent implements Component, Poolable {
    public int dodgeChanceBonus;
    public int critChanceBonus;
    public int resourceCostReduction;  // unused this pass — no node grants it yet, no combat code reads it
    public int weightCapacityBonus;    // unused this pass — Epic N (weight cap) doesn't exist yet

    @Override
    public void reset() {
        dodgeChanceBonus = 0;
        critChanceBonus = 0;
        resourceCostReduction = 0;
        weightCapacityBonus = 0;
    }
}
```
Computed once in `BattleParticipant.fromCharacter`, the single translation boundary §16 calls for: iterate `loadoutCmp.loadout.skills()`, filter to `PASSIVE`, and for each — if `passiveEffect() == STAT_BONUS`, fold `passiveMagnitude` directly into the `Stats` object already being built for `StatsComponent` (add to the named `passiveTargetStat`'s component); otherwise accumulate into the matching field above (`DODGE_CHANCE_BONUS`→`dodgeChanceBonus`, `CRIT_CHANCE_BONUS`→`critChanceBonus`, etc.). Attach the populated component to the entity alongside the existing slot components. Add a `passiveModifiers()` accessor to `BattleParticipant`, same shape as `weapons()`/`skills()`.

**No `ActionResolver` change needed** — verified against the current code: `BattleParticipant.fromCharacter` already filters `loadout.skills()` to `SkillType.ACTIVE` before populating `SkillSlotComponent` (`if (skill.type() == SkillType.ACTIVE)`), predating this pass. The "required engine change" §16 flagged is already done; what was actually missing is this component, not a cascade filter.

`BattleEngine.resolveResultSet`/`applyEntry` thread `attacker.passiveModifiers().critChanceBonus` and `defender.passiveModifiers().dodgeChanceBonus` into the calls described in §0.

## 2. `Skill` and new enums (M1)

```java
public record Skill(
    long id, String name, String description, SkillType type,
    int manaCost, Difficulty difficultyToAct, int minAttack, int maxAttack,
    PassiveEffectType passiveEffect,   // null when type == ACTIVE
    int passiveMagnitude,              // 0 when type == ACTIVE
    StatName passiveTargetStat         // null unless passiveEffect == STAT_BONUS
) {}

public enum PassiveEffectType {
    STAT_BONUS, DODGE_CHANCE_BONUS, CRIT_CHANCE_BONUS, RESOURCE_COST_REDUCTION, WEIGHT_CAPACITY_BONUS
}

public enum StatName {
    STRENGTH, DEXTERITY, VITALITY, INTELLIGENCE, WISDOM, SPIRIT, SPEED, INSIGHT
}
```
This changes `Skill`'s constructor arity — fix every existing call site (`BotFactory`'s `SPARK`/`LIGHTNING_BOLT`/`METEOR` constants, any test fixtures constructing a `Skill` directly) by passing `null, 0, null` for the three new trailing parameters on existing `ACTIVE` skills. Register both new enums in `KryoConfig`, appended after the existing enum block (append-only, system design §5).

## 3. `skill_tree` persistence

`Character`/`CharacterEntity` gain a new field: `Map<String, Integer> skillTree` (node id → current rank, 0 implied when absent). This changes the `Character`/`CharacterEntity` constructor arity too — fix every call site: `CharacterCreationScreen` (pass `new HashMap<>()`), `BotFactory.assemble`, `CombatFixtures.character`/`frailCharacter`/`characterAtLevel`, and the `seed()` helpers in `CharacterServiceAllocateStatPointsTest`/`AllocateStatPointsRequestHandlerTest`. `@Json` on the `CharacterEntity` field, same as `stats`/`inventory`/`loadout`.

**Migration V10:**
```sql
-- V10__Add_Character_Skill_Tree.sql
ALTER TABLE characters ADD COLUMN skill_tree JSONB NOT NULL DEFAULT '{}'::jsonb;
```

New `CharacterDao` method:
```java
@SqlUpdate("UPDATE characters SET skill_tree = :skillTree WHERE id = :characterId")
void updateSkillTree(@Bind("characterId") long characterId, @Bind("skillTree") @Json Map<String, Integer> skillTree);
```
This does **not** need a C2 exception — the guard test only polices `inventory`/`loadout`, and this touches neither.

## 4. Tree content — hardcoded catalog, v1.0 (M1)

**Scope decision, stated plainly:** all 20 nodes are `PASSIVE` this pass. The tree's whole point (per the design history in §16) was unlocking selectable passives — new `ACTIVE` tree content (brand-new attack skills, balanced against the existing Spark/Lightning Bolt/Fireball/Meteor tier system) is a separate, larger content question, deferred to the future data-driven content pass (Epic E1/M5), not invented here. Every node below is `STAT_BONUS`, `DODGE_CHANCE_BONUS`, or `CRIT_CHANCE_BONUS` — the three effect types this pass actually wires into combat (§0/§1). `RESOURCE_COST_REDUCTION`/`WEIGHT_CAPACITY_BONUS` stay valid, unused enum values.

Skill ids for tree nodes start at `1000L` (clear of starter content's `1L`–`4L` range). **One stable id per node, shared across all 3 ranks** — learning grants it, upgrading replaces the same id's entry in place (§5), never mints a new id per rank.

| Node id | Name | Tier gate | Effect | Magnitude/rank (3 ranks) |
|---|---|---|---|---|
| `physical.t1.n1` | Iron Grip | 1 | STAT_BONUS STRENGTH | +2 |
| `physical.t1.n2` | Swift Hands | 1 | STAT_BONUS DEXTERITY | +2 |
| `physical.t2.n1` | Battle Fortitude | 20 | STAT_BONUS VITALITY | +4 |
| `physical.t2.n2` | Precision Strikes | 20 | CRIT_CHANCE_BONUS | +3% |
| `physical.t3.n1` | Juggernaut | 40 | STAT_BONUS STRENGTH | +6 |
| `physical.t3.n2` | Executioner | 40 | CRIT_CHANCE_BONUS | +5% |
| `magical.t1.n1` | Arcane Focus | 1 | STAT_BONUS INTELLIGENCE | +2 |
| `magical.t1.n2` | Mind's Clarity | 1 | STAT_BONUS WISDOM | +2 |
| `magical.t2.n1` | Deep Reserves | 20 | STAT_BONUS SPIRIT | +4 |
| `magical.t2.n2` | Spell Weaving | 20 | STAT_BONUS INTELLIGENCE | +4 |
| `magical.t3.n1` | Archmage's Insight | 40 | STAT_BONUS WISDOM | +6 |
| `magical.t3.n2` | Overwhelming Power | 40 | STAT_BONUS INTELLIGENCE | +6 |
| `universal.t1.n1` | Fleet Foot | 1 | STAT_BONUS SPEED | +2 |
| `universal.t1.n2` | Keen Senses | 1 | STAT_BONUS INSIGHT | +2 |
| `universal.t2.n1` | Evasive Instinct | 20 | DODGE_CHANCE_BONUS | +3% |
| `universal.t2.n2` | Tracker's Eye | 20 | STAT_BONUS INSIGHT | +4 |
| `universal.t3.n1` | Untouchable | 40 | DODGE_CHANCE_BONUS | +5% |
| `universal.t3.n2` | Overdrive | 40 | STAT_BONUS SPEED | +6 |
| `faction.crimson.n1` | Crimson Fury | faction only | CRIT_CHANCE_BONUS | +5% |
| `faction.skyborn.n1` | Skyborn Grace | faction only | DODGE_CHANCE_BONUS | +5% |

Per-rank magnitude is `passiveMagnitude` × current rank when computing the aggregate in §1 (rank 3 of Precision Strikes contributes +9% crit, not +3%) — or, simpler and equally valid: store `passiveMagnitude` as the **per-rank increment** and have `PassiveModifiersComponent`'s aggregation multiply by the learned rank. Pick one and apply it consistently; document the choice in the node-content code comment so it isn't ambiguous later.

**Faction mapping:** `Faction.A` → Crimson (`faction.crimson.n1`), `Faction.B` → Skyborn (`faction.skyborn.n1`) — matches `CharacterCreationScreen`'s existing placeholder descriptions ("Faction A: might and raw power" ↔ crit; "Faction B: cunning and arcane knowledge" ↔ evasion). The actual `A`/`B` → `CRIMSON_ACCORD`/`SKYBORN` rename stays deferred, unaffected by this mapping.

**Cost table (system design §16):** tier 1 — 1 SP + 10 gold/rank; tier 2 and Faction — 3 SP + 60 gold/rank; tier 3 — 6 SP + 150 gold/rank. 3 ranks per node, cost is per-rank (paid each time, not just once).

## 5. Learn/upgrade (M2)

```java
public record LearnSkillNodeRequest(long characterId, String nodeId) {}
public record LearnSkillNodeResponse(boolean success, String message, Skill node, int newRank,
                                     int remainingSkillPoints, long remainingGold) {}
```
New **`SkillTreeService`**, mirroring `RewardService`'s exact justification: learning/upgrading writes `characters.skill_points`, `characters.skill_tree`, `characters.inventory`, **and** `accounts.global_currency` in one transaction — `CharacterService`/`AccountService`'s `onDemand` DAOs can't span that atomically, so this needs the raw `Jdbi` the same way `RewardService` does.

```java
public class SkillTreeService {
    public SkillTreeService(Jdbi jdbi, CharacterService characterService) { ... }
    public ServiceResult<LearnSkillNodeResult> learnOrUpgrade(long accountId, long characterId, String nodeId) { ... }
}
```
Validation order: ownership (fail closed); `nodeId` exists in the hardcoded catalog (§4); level gate — `character.level >= node.tierLevel`, or for Faction nodes, `character.faction == node.requiredFaction`; current rank (from `skillTree.getOrDefault(nodeId, 0)`) `< 3`; sufficient `skill_points` and `global_currency` for the **next** rank's cost. On success, inside one `jdbi.useTransaction`: decrement `skill_points`, decrement `global_currency`, write `skill_tree` with the incremented rank, and grant-or-replace the node's `Skill` in `inventory.skills()` — find an existing entry by `id` (matches §4's stable-id-per-node scheme) and replace it with the new rank's stat block, or append if this is rank 1. Reuse `CharacterDao.getInventoryForUpdate`/`updateInventory` (Epic L) for the read-modify-write, same row-lock pattern.

New `MessageCode` entries: `SKILL_NODE_NOT_FOUND`, `SKILL_LEVEL_GATE_NOT_MET`, `SKILL_FACTION_MISMATCH`, `SKILL_RANK_MAXED`, `SKILL_POINTS_INSUFFICIENT`, `SKILL_GOLD_INSUFFICIENT`.

New `LearnSkillNodeRequestHandler`, same shape as `AllocateStatPointsRequestHandler` — drops on unauthenticated/unowned, answers validation failures the player can act on. Wire into `KryoPacketRouter`/register both packets in `KryoConfig` (append-only).

## 6. `SaveLoadoutRequest` — new prerequisite capability

Not one of M1–M5's numbered stories, but required for M3 to mean anything: without it, a learned passive can never actually be equipped. Grounded in a real gap — no such endpoint exists anywhere today.

```java
public record SaveLoadoutRequest(long characterId, Loadout loadout) {}
public record SaveLoadoutResponse(boolean success, String message, Loadout savedLoadout) {}
```
`CharacterService.saveLoadout(accountId, characterId, loadout)` validates: ownership; every weapon/skill/pet `id` referenced in the submitted `loadout` actually exists in the character's current `inventory` (the ownership-of-items rule §4.4 always implied but never enforced in code — real gap, closed here); and `loadout.skills().size <= 5` (M3's shared cap — `ACTIVE` and `PASSIVE` skills share this one count, no separate caps). **Weight capacity is not enforced this pass** — Epic N doesn't exist yet; a loadout that would fail N1's future hard gate saves successfully today, same as it would have before this pass.

New `CharacterDao.updateLoadout(characterId, @Json Loadout loadout)` — an unconditional write, not a read-modify-write (the client submits the whole new loadout each time, unlike inventory's append-style grants). **This is C2's third sanctioned exception** — alongside `updateInventory` (Epic L), add `updateLoadout` by name to `BattleLeavesInventoryAloneTest`'s structural guard, asserting it touches `loadout` and *never* `inventory`, while every other `CharacterDao` update continues to fail the check on both columns.

New `MessageCode` entries: `LOADOUT_ITEM_NOT_OWNED`, `LOADOUT_SKILL_SLOTS_EXCEEDED`. New `SaveLoadoutRequestHandler`, same shape as the others; register packets in `KryoConfig`.

## Testing

- **Crit (§0):** `isNaturalCrit` — the flat-range guard (`100/100` never crits, any seed), the 80% threshold boundary (exactly at threshold crits, one below doesn't), and a real range's empirical crit rate over many trials lands near 20%. Crimson's forced-crit roll: zero `critChanceBonus` consumes no roll (assert via a `Random`/`SplittableRandom` that throws if drawn from, or an exact RNG-consumption count check); a positive bonus fires at roughly its configured rate over many trials.
- **Regression audit (§0), required:** run the full `ActionResolverTest`/`DamageCalculatorTest`/`BattleEngineTest` suite. `CombatFixtures.flatWeapon()`-based tests must show byte-identical damage to before this change (guarded by the flat-range check). Any test using a real (non-flat) range or the punch fallback may now land in the crit band under its existing fixed seed — if so, update that assertion's expected value to the correct crit-adjusted number; do not alter the fixture's seed or suppress crit to dodge the update.
- **`PassiveModifiersComponent` (§1):** a character with two `STAT_BONUS` passives (different stats) plus one `DODGE_CHANCE_BONUS` passive equipped ends up with correctly summed `StatsComponent`/`PassiveModifiersComponent` values after `fromCharacter`; a character with no `PASSIVE` skills equipped gets an all-zero component (no behavior change from before this pass).
- **`SkillTreeService`:** rejects below level gate, rejects faction mismatch on a faction node, rejects insufficient skill points, rejects insufficient gold, rejects a 4th rank attempt (already at 3); a successful rank-1 learn appends the `Skill` to inventory with the correct id; a successful upgrade replaces the same id's entry with the new rank's magnitude, decrementing points/gold and incrementing `skill_tree` atomically (verify via a forced mid-transaction failure that nothing partially commits, same style as `RewardServiceTest`'s failure case).
- **`SaveLoadoutRequestHandler`:** ownership rejection (drop, not answered); a loadout referencing an item id not in inventory is rejected with `LOADOUT_ITEM_NOT_OWNED`; a 6-skill loadout is rejected with `LOADOUT_SKILL_SLOTS_EXCEEDED`; a valid save persists and round-trips through `getCharacters`.
- **C2:** `updateLoadout` is the named third exception — assert it touches `loadout` and not `inventory`; every other method (including `updateInventory` and `updateSkillTree`) is checked exactly as before.

## Definition of done

`gradlew.bat build` and `gradlew.bat server:test`/`core:test` pass. Update `docs/planning/02-user-stories.md` — M1, M2, M3 to `done` with close-out notes covering: the crit mechanic as a new prerequisite (not originally its own story, needed because §14's design was never implemented), the `SaveLoadoutRequest` endpoint as a second unplanned prerequisite, and the all-passive v1.0 content scope decision (§4). Leave M4/M5 as `todo` — flag them as the natural next slice once Epic O prices a skill-restoration scroll.
