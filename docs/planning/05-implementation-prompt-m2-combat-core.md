# Claude Code prompt — M2 combat core: pouch/stamina resolution + BattleSession wiring

Copy everything below the line into Claude Code. Written 2026-07-09 after a Cowork planning pass; the planning docs it references are the actual spec — this prompt is a scoped work order pointing at them, not a duplicate of them.

---

Implement the next slice of the combat engine for Crimson Sky. Read these first, in order, and treat them as the source of truth — don't re-derive rules from memory; the planning docs are the spec and supersede/extend the original GDD:

1. `CLAUDE.md` — architecture conventions (ECS discipline, manual DI, Strategy-pattern packets, Kryo registration order, ownership-validation guardrail).
2. `docs/planning/01-system-design-combat-engine.md` §3, §4.1–§4.4, §6 — the actual spec for everything below.
3. `docs/planning/02-user-stories.md` — Epic A (A1–A7) and B2/B3 for acceptance criteria.
4. `docs/planning/04-starter-content.md` — use these exact weapon/skill/pet numbers as test fixtures. Don't invent placeholder content.

## Scope for this pass

Implement, in this order: **A1 (revisit) → A2 → A3 → A5 → A7 → B2 → B3**.

Explicitly **out of scope** — don't touch: D4 (loadout UI), E1/E2 (full content authoring — `04-starter-content.md`'s items are enough), the `Faction` A/B → Crimson/Skyborn rename (§14), the crit/dodge faction-skill idea (§14), Break/Steal skills (J9/J10), weapon durability, Embers, any client/`CombatScreen` work (M4), Steam/account-linking/security epics. If you find yourself touching any of these to make something compile, stop and flag it rather than expanding scope.

## Data model changes required

These are breaking changes to existing records in some cases — grep for existing usages before assuming a field is unused:

- **`Weapon`**: replace `attackPower` (single `int`) with `minAttack`/`maxAttack` (two `int`s). Add `staminaCost` (`int`).
- **`Skill`**: add `minAttack`/`maxAttack` (new fields — this is what actually deals damage now, `manaCost`/`difficultyToAct` stay as-is).
- **`Pet`**: replace `attackPower` with `minAttack`/`maxAttack` for consistency with the other two (lower priority than the two above, but do it in the same pass to avoid a second migration later).
- **`Character`**: add `maxStamina` (`int`), populated the same way `maxHp`/`maxMp` are (check `CharacterMapper` for the existing pattern).
- **`ResolvedAction`**: add a `damage` field (`int`) — the final post-mitigation damage for that entry. Check whether this needs Kryo registration changes (`KryoConfig`) since it's a record used in what will become network packets.
- **`WeaponSlotComponent`**: change `Weapon equipped` to an ordered `Array<Weapon>` — array index is priority, index 0 = tried first (no separate priority field).
- **`SkillSlotComponent`**: same change, `Array<Skill>`, and only `ACTIVE`-type skills belong in this pouch (filter `PASSIVE` ones out — passives aren't part of this pass's scope beyond making sure they don't accidentally end up in this array).
- **New `PetSlotComponent`**: `Pet equipped`, `int currentHealth` (system design §3).
- **New `BattleStateComponent`**: `int spentMana`, `int spentStamina`, `boolean petUsedThisTurn`. No `weaponDepleted` field — that was superseded by `spentStamina` (see system design §3's corrected version, and don't reintroduce the old field).
- **New `TurnResultComponent`**: `Array<ResolvedAction> actions`, `long turnNumber`.

## Resolution algorithm (implement exactly this — system design §4.1–§4.4 is the full derivation if you need it)

**Character action (per turn):**
1. **Weapon branch:** if the weapon pouch is non-empty, roll `d100 < effectiveStrength(pouch[0])` (system design §4.3's `comfortableWeight`/`effectiveStrength` formula, floored at 5, using **slot 0's** weight specifically for this one roll — see §4.4's "gap closed" note on why it's always slot 0). On success, walk the pouch by **Stamina affordability only** (`remainingStamina >= weapon.staminaCost()`, no re-rolling) and use the first affordable one; that weapon's own `weight` does not gate selection further, only its stat scaling (§4.2) and frequency (raw DEX, unaffected by weight — frequency is character-level, not weapon-specific). If none affordable, fall through to the skill branch.
2. **Skill branch:** same shape — one roll against slot 0's `effectiveWis` (Difficulty-adjusted, §4.3), then walk by Mana affordability (`currentMana >= skill.manaCost()`). If none affordable, that's a Burned cast on slot 0 specifically (existing §4.1 behavior, just point it at slot 0).
3. **Punch fallback:** both branches failed or empty pouches. Punch costs 0 Mana and 0 Stamina always (§4.2) — never itself blocked by a resource check.
4. **Damage, once an action is chosen:** `rawDamage = randomInt(item.minAttack, item.maxAttack) + floor(pathStat * 0.5)` (STR for weapon/punch, INT for skill) → mitigate: `itemPower = (minAttack+maxAttack)/2`, `mitigationFactor = itemPower/(itemPower+defenderBaseDef)`, `finalDamage = round(rawDamage * mitigationFactor)`. Store this in `ResolvedAction.damage`.
5. **Dodge:** rolled independently per individual hit within the frequency count (not once per Result Set entry) — `dodgeChance = min(75, round(speed*0.75))` on the defender.

**Pet action:** independent roll vs `effectiveInsight = insight + tamenessModifier(pet)` (§4.3 table), regardless of the character action's outcome (runs even on a Burned cast). On success, pet hits use `randomInt(pet.minAttack, pet.maxAttack)` with **no** stat bonus (self-contained, per §4.2).

**Within one Result Set:** `[character hits..., pet hits...]`, resolved strictly in that order, checking the ≤0 HP win condition after **every individual hit** — not just at the end of the array (§4.2's "within one combatant's Result Set" note). A kill mid-array skips everything remaining in that Result Set.

**Across the two combatants (needs B2 first):** compare `speed` once at battle start (static for the match, seeded-coinflip tiebreak). Higher-priority combatant's full Result Set resolves first each turn; if it kills, the lower-priority combatant's Result Set for that turn never runs at all.

**Win condition:** HP ≤ 0 → immediate loss. Turn cap 40 → winner is higher remaining HP%, then higher SPD, then seeded coinflip.

## Testing

- Existing `A1`/`A6` tests (`ActionResolverTest`, `DeterminismTest`) must still pass — they exercise a single-item pouch, which is just the degenerate case of the new array-based logic (array of size 1). If they don't pass unmodified, that's a signal the new logic changed behavior it shouldn't have, not a reason to change the tests' expectations.
- New tests needed: multi-item weapon pouch falling through on Stamina exhaustion (use `04-starter-content.md`'s Twin Daggers/Longsword/Warhammer at different Stamina levels), multi-item skill pouch falling through on Mana exhaustion (Spark/Lightning Bolt/Fireball/Meteor), the "nothing affordable" case for both, pet action firing independently of a Burned cast, and a full two-participant turn exercising the priority-order kill-prevents-counter-hit rule.
- Reuse the seeded-RNG (`SplittableRandom`) pattern already established — every new test should be reproducible from a fixed seed, not flaky.

## Definition of done (per `00-project-plan.md` §9)

`gradlew.bat build` and `gradlew.bat core:test` both pass. Update `docs/planning/02-user-stories.md` statuses for A1/A2/A3/A5/A7/B2/B3 as you complete each. If you make an architectural call not already decided in the system design doc (e.g. exactly how `BattleSession` wires to `BattleParticipant`), note it inline in code comments and flag it back to me so Cowork can fold it into the system design doc during close-out, per the working agreement in `CLAUDE.md`.
