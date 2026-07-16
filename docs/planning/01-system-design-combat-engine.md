# System Design — Mizan Combat Engine (M2/M3)

Last updated: 2026-07-06 (revised same day: N-participant BattleSession, ownership guardrail, RNG choice, account linking, Steam integration, security hardening)

Source of truth for game rules: `Mizan_Combat_Engine_GDD_v4.pdf`. This document maps those rules onto the existing codebase, following the conventions already established in `CLAUDE.md` (strict ECS for simulation, server-authoritative, manual DI, Strategy-pattern packet routing).

## 1. Scope

In scope for M2: the deterministic per-turn resolution logic (Steps 1–3 of the GDD) as headless-testable Ashley systems on the **server**, plus the packets needed to request a turn and return its Result Set.

In scope for M3: matchmaking/session plumbing and reward persistence. Out of scope for both: client-side rendering/animation (M4) and data-driven content authoring (M5).

This doc also now covers three cross-cutting design areas that came up during planning and don't neatly belong to a single milestone: the M4 placeholder-rendering approach (§10), the M6 cross-platform account-linking schema (§11), and Steam integration technical notes plus general security/ops hardening that apply across M2–M6 (§12–13).

## 2. Open design question: TCP vs UDP for combat

`CLAUDE.md` currently states combat state sync should use UDP ("fast, fire-and-forget") while login/matchmaking use TCP. That guidance predates any combat implementation. Given the GDD describes a **discrete, turn-based** simulation (one Result Set per turn, not continuous positional state), a request/response over the existing TCP `KryoServer`/`GameConnection` connection — reusing the same `RequestHandler`/`PacketHandlerRegistry` pattern as every other feature — is likely sufficient and much simpler to get right (ordering, no packet loss handling needed).

**Recommendation:** build M2/M3 on TCP request/response first. Only introduce a UDP channel later if turn latency or server load actually demands it. Flagging this rather than assuming, since it reverses a stated architectural intent — worth a conscious decision before work starts.

## 3. New ECS components (`core/ecs/component`)

**Existence check (so this table isn't read as "all five already exist"):** `WeaponSlotComponent`/`SkillSlotComponent` already exist in the repo today (currently single-item, per §4.1/§4.4). `PetSlotComponent`, `BattleStateComponent`, and `TurnResultComponent` do **not** exist yet — they're genuinely new-for-M2, listed here as spec for A2/A5/A3 to build, not as already-built code.

| Component | Fields | Notes |
|---|---|---|
| `WeaponSlotComponent` | `Weapon equipped` | Bridges `Loadout.weapons()` selection into ECS |
| `SkillSlotComponent` | `Skill equipped` | Bridges `Loadout.skills()` selection |
| `PetSlotComponent` | `Pet equipped`, `int currentHealth` | Pet battle-scoped HP separate from the immutable `Pet` record |
| `BattleStateComponent` | `int spentMana`, `int spentStamina`, `boolean petUsedThisTurn` | GDD §4 "Battle State (Memory)" — volatile, reset every battle. Lives only on entities inside an active `BattleSession`; never persisted. **Corrected:** the original single `weaponDepleted` boolean (from before Stamina existed, §4.4) can't represent per-weapon availability across a multi-item pouch — it's replaced by `spentStamina`, checked per-weapon against `weapon.staminaCost()` at draw time (§4.2/§4.4), the same way `spentMana` is already checked per-skill. |
| `TurnResultComponent` | `Array<ResolvedAction> actions`, `long turnNumber` | Populated by `ResultCompilationSystem` each turn, consumed by the packet layer and then cleared |

Existing components (`HealthComponent`, `ManaComponent`, `StatsComponent`, `BaseStatsComponent`) are reused as-is — `StatsComponent` already carries the eight GDD stats matching the `Stats` record.

## 4. New ECS systems (`core/ecs/system` — new package, none exist today)

Run server-side only, in this order each turn (matches GDD §3 cascade):

1. **`ActionResolutionSystem`** — GDD Step 1.
   - Roll vs `strength` → success: equip `WeaponSlotComponent.equipped`, frequency derived from `dexterity` (per GDD, DEX/WIS "multiplies number of actions per turn").
   - On weapon-roll failure, roll vs `wisdom` → success: check `spirit`-derived mana pool against `Skill.manaCost()`; insufficient mana → action is `Burned` (`ResolvedAction.failed = true`).
   - Both fail → fallback `Punch`.
2. **`PetResolutionSystem`** — GDD Step 2. Independent roll vs `insight`, regardless of Step 1 outcome. On success, appends a pet action with frequency derived from `Pet.tameness()` + `insight`.
3. **`ResultCompilationSystem`** — GDD Step 3. Merges the character action and pet action (if any) into the ordered `ResolvedAction` array on `TurnResultComponent`, matching the GDD's `[3x Hammer, 2x Wolf]` shape.

All rolls use a **per-battle seeded RNG** (seed stored on the `BattleSession`, not per-entity) so a battle's outcome is reproducible for debugging and for unit tests — this was flagged as a gap in the current codebase (no tests exist yet per `CLAUDE.md`). Use `java.util.SplittableRandom` rather than plain `java.util.Random` — better statistical distribution over long sequences, still fully seedable/reproducible. This is a deliberate choice made once, up front, rather than defaulting silently and having battle logs/tests already built against whatever came first.

`TurnOrderSystem` (priority ordering across two combatants, using `speed` for dodge/priority per GDD) is needed once two-sided battles exist (M3), not for single-cascade unit testing in M2.

### 4.1 Concrete rules chosen while implementing A1/A4/A6 (was open in the GDD)

The GDD leaves the exact numbers open ("Roll vs STR", "Frequency is 3x"). These were fixed during implementation and are the source of truth going forward:

- **Roll model:** `rng.nextInt(100) < stat` succeeds — a d100 against a 0–100 stat. RNG governs **only** the pass/fail of each check (weapon draw vs STR, skill cast vs WIS); frequency is deterministic, not rolled.
- **Frequency:** `1 + stat/30` (integer division), DEX for weapons, WIS for skills. Reproduces the GDD scenarios (DEX 60 → 3, WIS 80 → 3). Punch fallback is a single strike (frequency 1).
- **Burned cast:** skill roll succeeds but `currentMana < skill.manaCost()` → `ResolvedAction(SKILL, "FAILED_CAST", 1, failed=true)`. A character with no weapon equipped simply skips the weapon roll (no RNG consumed).

**Where the code landed:** the pure cascade is `core/combat/ActionResolver` (no Ashley/GDX deps, headless-testable); `core/ecs/system/ActionResolutionSystem` (the repo's first Ashley `IteratingSystem`, introduces `ComponentMapper`) bridges components → resolver → `CharacterActionComponent`. New slot components `WeaponSlotComponent`/`SkillSlotComponent` (§3) exist and are read but are **not yet populated** by `CharacterMapper` — populating them from a `Loadout` selection is a battle-setup/equip concern (B2/D4). `BattleSession` (`core/combat`) currently holds only the seed + `SplittableRandom`; its participant `Array` (§7) is future.

**Known wiring gap (for §6/B3):** `server` does not depend on `core` and has no Ashley dependency, so there is currently no path for the server to run these core-resident systems. Add `project(':core')` to `server` (or extract the engine) when wiring `CombatActionRequestHandler`/`CombatService`. A1/A4/A6 are fully satisfied without it — all logic is in `core` and proven via the `core` test set.

### 4.2 Damage, mitigation, dodge, priority, and win condition (first-pass numeric design)

The GDD and §4.1 define whether/how-often an action happens (the roll + frequency), but nothing about how much HP that action removes, how defense reduces it, whether evasion applies, who acts first, or when a battle ends. None of this exists in code yet — this is a first-pass proposal, calibrated to the pacing target below, not implemented numbers. Expect a tuning pass once real weapon/skill/pet content exists (Epic E) and ideally a Monte Carlo simulation extending the existing `DeterminismTest` harness before locking anything in.

**Pacing target:** ~20+ turns for an evenly-matched 1v1 (your call — favors a grindier, momentum-driven feel over a quick skirmish). Every formula below is calibrated loosely against this; see the worked example for a sanity check.

**HP / MP / Stamina pools** (`Character.maxHp` / `maxMp` / `maxStamina` — currently unpopulated by any formula). `maxHp`'s constant was recalibrated after the worked example below (was `* 8`; the range-based damage model below hit ~14 turns at that value, short of the ~20-turn target). `maxStamina` is new this session (see §4.4) — deliberately formula-identical to `maxMp`, just STR-sourced instead of SPI-sourced, since it's the physical path's mirror of the same "resource that gates repeated use" concept:
```
maxHp      = 100 + vitality * 11
maxMp      = 50  + spirit   * 5
maxStamina = 50  + strength * 5
```

**baseAtk / baseDef** (`Character.baseAtk` / `baseDef`): per the GDD's own stat table, VIT *and* SPI both govern "resistances," so defense blends both HP-side stats rather than being purely physical. Treat these two fields as an aggregate power/toughness *rating* (useful for matchmaking/Elo seeding or UI display), not the value actually used in per-hit math below — per-hit math always reads the specific path's stat (STR or INT) plus the specific gear's power value directly.
```
baseAtk = round((strength + intelligence) * 0.6)
baseDef = round((vitality + spirit) * 0.6)
```

**Per-hit damage** — updated per this session's content-design pass: each weapon/skill now carries its own `minAttack`/`maxAttack` range (its "feel," independent of who wields it), and the wielder's STR/INT adds a flat bonus **on top of both ends of that range** (your call — a strong character's hit is uniformly better, not just occasionally):
```
rawDamage = randomInt(item.minAttack, item.maxAttack) + statBonus
statBonus = floor(pathStat * 0.5)
```
- `pathStat` = `strength` for Weapon and Punch actions, `intelligence` for Skill actions. Pet hits do **not** get a `statBonus` — a pet's damage is self-contained (its own range only), since Insight already governs whether/how-often it acts at all (see §4.3); Insight-scaled pet damage is a reasonable later addition, not included here to avoid double-dipping Insight's effect.
- `item` = `Weapon` (weapon hits), `Skill` (skill hits), or `Pet` (pet hits) — **all three need a `minAttack`/`maxAttack` pair on their record** (see data-model gap below; this supersedes last pass's single `Skill.power` proposal). Punch has no backing record — use a fixed `randomInt(1, 5)` in its place. **Explicit, not just implied:** Punch costs **0 Mana and 0 Stamina** and is always available regardless of either pool — it's the fallback of last resort (GDD §3 Step 1c) and the state a fresh character starts a battle in (no weapon, no skill drawn yet), so it can never itself be blocked by a resource check.

**Mitigation** (ratio formula, your choice): defense never fully negates damage, but scales down hits from lower-power sources disproportionately. Uses the item's own range **midpoint** as its reference power (the `statBonus` is excluded here — mitigation is about the gear's inherent power tier, not the wielder's stat, which is already reflected in `rawDamage`):
```
itemPower = (item.minAttack + item.maxAttack) / 2
mitigationFactor = itemPower / (itemPower + defenderBaseDef)
finalDamage = round(rawDamage * mitigationFactor)
```

**Dodge**: rolled independently per individual hit (not once per whole "3x Hammer" group), using the same d100-vs-stat model as §4.1, so a Result Set can show partial dodges (e.g. "3x Hammer → 2 landed, 1 dodged").
```
dodgeChance = min(75, round(speed * 0.75))
```
Capped at 75% so no build becomes literally unhittable. Applies uniformly to weapon/skill/pet/punch hits — dodge is a defender-side property, not source-specific.

**Turn priority** (your call: priority can prevent a counter-hit entirely):
- Compare the two combatants' `speed` once, at battle start — static for the whole match, since nothing currently modifies SPD mid-battle. Ties broken by a single coinflip off the battle's seeded RNG (fixed for the match, reproducible).
- Each turn, the higher-priority combatant's full Result Set resolves and applies damage **first**. If the opponent's HP drops to ≤ 0, the battle ends immediately — the lower-priority combatant's Result Set for that turn is never rolled or applied.
- Otherwise, the lower-priority combatant's Result Set resolves normally afterward, against the now-damaged opponent.

**Within one combatant's Result Set (gap closed):** a Result Set is `[character hits..., pet hit(s)...]` — matching the GDD's own `[3x Hammer, 2x Wolf]` ordering (character action listed first, pet second). Resolve strictly in that order: all of the character's own hits first (left to right, checking the ≤0 win condition after **every individual hit**, not just at the end of the array), then the pet's hits, same per-hit win-condition check. A kill mid-array — whether by the character's own hits or, more rarely, one of the pet's — ends the battle immediately and skips everything remaining in that Result Set, consistent with the cross-combatant priority rule above applying at the same granularity (per-hit, not per-turn).

**Win condition:**
- HP ≤ 0 → immediate loss.
- Turn cap of **40** (2x the pacing target) guards against a theoretical high-mutual-dodge stalemate. On cap: winner = higher remaining HP%; tie → higher SPD; still tied → seeded coinflip.

**Data model gap (flag for Claude Code, not fixed here):**
- **`Character.maxStamina` (`int`) does not exist on the record at all** (checked directly — only `maxHp`/`maxMp` exist) and must be added, the same way `maxHp`/`maxMp` already are, populated via the formula above.
- None of the three action-source records currently carry a `minAttack`/`maxAttack` range.
- `Weapon.attackPower` (a single `int`) should become `minAttack`/`maxAttack` (two `int`s) — a breaking change to an existing field, not an addition. **Also add `Weapon.staminaCost` (`int`)** — new this session, mirroring `Skill.manaCost` exactly (see §4.4).
- `Skill` (`manaCost`, `difficultyToAct`) needs `minAttack`/`maxAttack` added new — this supersedes last pass's recommendation of a single `power` field; a range is more consistent with the Weapon change and with the game's own symmetric-paths philosophy.
- `Pet.attackPower` (also currently a single `int`) — recommend the same `minAttack`/`maxAttack` change for consistency across all three sources, though lower priority since it's not required by anything else in this section.
- `BattleStateComponent` (`core/ecs/component`, §3) needs a new `spentStamina` (`int`) field alongside its existing `spentMana` — same ephemeral, per-battle, never-persisted treatment.

All of these are cheap to change now since no weapon/skill/pet content is seeded yet (per project plan §2).

### 4.3 Plugging Tameness, Difficulty, and weight into the existing cascade

`Skill.difficultyToAct` (`EASY`/`MEDIUM`/`HARD`/`MYTHIC`) and `Pet.tameness` (`WILD`/`STUBBORN`/`TRACEABLE`/`LOYAL`) already exist on the data model but aren't referenced by the §4.1 roll/frequency formula. Proposed numeric mapping, designed to plug into the *existing* `1 + stat/30` frequency formula and d100 roll rather than inventing a new mechanism:

| `Difficulty` | WIS modifier (skill roll) | | `Tameness` | Insight modifier (pet roll) |
|---|---|---|---|---|
| EASY | +0 | | WILD | −10 |
| MEDIUM | −10 | | STUBBORN | +0 |
| HARD | −20 | | TRACEABLE | +10 |
| MYTHIC | −35 | | LOYAL | +20 |

`effectiveWis = wisdom + difficultyModifier(skill)`, used for both the skill-cast roll and its frequency (`1 + effectiveWis/30`). Same shape for pets: `effectiveInsight = insight + tamenessModifier(pet)`, used for both the pet-aid roll and its frequency.

**Correction from last pass:** `Weapon.weight` was previously speculated to reduce effective DEX/frequency. This session clarified it relates to STR directly, as a **soft penalty** (never a hard equip-block) on the weapon-draw success roll — a too-heavy weapon is harder to draw reliably, not harder to swing once drawn:
```
comfortableWeight = strength / 2
overage = max(0, weapon.weight - comfortableWeight)
effectiveStrength = max(5, strength - round(overage * 10))
```
`effectiveStrength` replaces `strength` in the weapon-draw roll (`roll(rng) < effectiveStrength`) only — frequency still reads raw `dexterity`, unaffected. Constants (`/2`, `*10`) are first-guess placeholders pending playtesting, same caveat as everything else in this section. **Fixed a self-contradiction:** the floor was originally `max(0, ...)`, which at extreme weight/STR mismatches drives `effectiveStrength` to exactly 0 — since the roll is `rng.nextInt(100) < effectiveStrength`, 0 means the weapon can *never* succeed, which is a hard gate in disguise despite this being explicitly designed as a soft penalty. Floored at `5` instead, so even a wildly mismatched weapon keeps a small (~5%) chance rather than becoming silently impossible.

**Player-facing requirement (ties to D4):** when `weapon.weight` exceeds a character's `comfortableWeight`, the loadout screen must surface this before the fight (e.g. "Too heavy for your Strength — reduced draw chance"), so the player can knowingly keep the weapon or swap it out. This is a soft penalty specifically so the player stays in control of the tradeoff, not a hidden one.

**Worked example (illustrative, not authored content; re-run with the range-based model), assuming neither weapon triggers the weight penalty above:** two full, stat-symmetric characters — Character A: STR 70 / DEX 60 / VIT 40 / SPI 40 / SPD 50, Hammer (`minAttack` 15 / `maxAttack` 35, midpoint 25 — same average power as last pass's flat 25, for continuity); Character B: INT 70 / WIS 60 / VIT 40 / SPI 40 / SPD 50, a MEDIUM-difficulty Lightning skill (`minAttack` 40 / `maxAttack` 70, midpoint 55, `manaCost` 30). Both get `maxHp` 540 (recalibrated constant), `baseDef` 48.

Running the formulas above: A's weapon succeeds 70% of turns at frequency 3; B's skill, after the −10 MEDIUM penalty, succeeds 50% of turns at frequency 2. Expected damage/turn comes out to roughly **27.7 for A vs. 30.2 for B** — much closer than last pass's 35.5-vs-16.9 (a ~2x gap down to ~9%), because the skill's midpoint (55) already sits at roughly the 2.2x-a-comparable-weapon ratio last pass's analysis called for. **That heuristic (skills need meaningfully higher power than comparable-tier weapons to offset Difficulty's roll/frequency penalty — roughly EASY ≈1.5x, MEDIUM ≈2–2.5x, HARD ≈3x, MYTHIC ≈4x+ a comparable weapon's range midpoint) still holds and is now validated against the actual range-based formula, not just the old flat-value one.**

At these numbers, whichever combatant's HP depletes first does so around **turn 14–18** (B outdamages A slightly, so A tends to fall first, around turn 18; the recalibrated `maxHp` constant above, `vitality * 11`, is what pulled this from ~14 turns up toward the ~20-turn target — still a first-pass estimate, not a locked number.)

### 4.4 Loadout pouches: multiple weapons/skills, priority order, and capacity

This session's content-design pass: instead of one equipped weapon and one equipped skill, a character equips a small **ordered set** of each ("pouch") before a fight, drawn from probabilistically — but per your call, "drawn from" means **priority order**, not random selection.

**Architecture note (checked against the actual code, not assumed):** this is a bigger change than it sounds. `WeaponSlotComponent`/`SkillSlotComponent` (`core/ecs/component`) currently hold a single `Weapon`/`Skill` field each, and `ActionResolver.resolveCharacterAction(Stats, Weapon, Skill, ...)` — story **A1, already marked `done`** — takes exactly one weapon and one skill as parameters. Supporting a pouch means both components need to hold an ordered collection instead, and the resolver needs a new "walk the priority list, use the first one that qualifies" step inserted after each successful roll. This revisits already-implemented, already-unit-tested code, not just adding to it — flag this clearly when it's picked up so re-testing is budgeted for, not assumed free.

**No new field needed for ordering:** `Loadout.weapons()` / `Loadout.skills()` are already `Array<Weapon>` / `Array<Skill>` (`common/model/Loadout.java`) — array index already can *be* priority (index 0 = tried first), by convention rather than a schema change.

**What "qualifies" means per source — both now driven by an affordability check, same shape:**
- **Skills:** the existing roll-vs-WIS (§4.1/§4.3) decides *if* a skill fires; on success, walk the skill priority list and use the **first skill the character can currently afford** (`currentMana >= skill.manaCost()`). If none are affordable, that's a Burned cast (§4.1), same as today, just checked against the whole list instead of one skill.
- **Weapons:** the roll-vs-effectiveStrength (§4.3) decides *if* a weapon fires; on success, walk the weapon priority list and use the **first weapon the character can still afford from their Stamina pool** (`remainingStamina >= weapon.staminaCost()`, where `remainingStamina = maxStamina - spentStamina`). If none are affordable, fall through exactly like a character with no weapon equipped does today (skip to the skill-cast roll, then Punch).

**Gap closed — which item's modifier governs the single gate roll, when the pouch holds several items with different Difficulty/weight?** `effectiveWis`/`effectiveStrength` (§4.3) are per-item (each skill's Difficulty, each weapon's weight, can differ). Rather than rolling once per candidate item (which would consume variable, unpredictable amounts of RNG per turn and break the existing A1/A6 determinism tests' assumptions), **the single gate roll always uses priority-slot-0's modifier** — your top-priority item's reliability decides whether that action-type happens at all this turn, exactly one roll, no change to the existing RNG-consumption shape. If the roll succeeds but slot 0 isn't affordable, the engine falls through the list by affordability alone (no re-rolling) and whichever item is actually selected uses **its own** Difficulty/weight for its own frequency calculation — only the initial gate uses slot 0's modifier specifically, frequency is always computed from whichever item actually resolves.

**Why Stamina, not durability:** raised initially as "add weapon durability later" — Stamina is the same idea (a weapon becomes unusable once you've used it "too much") but shaped as a **shared pool with a per-weapon cost**, which is exactly the pattern `manaCost`/mana already establishes for skills, rather than a new per-item state (`usesRemaining`, "broken" flags, etc.) that durability would require. Same player-facing outcome — a heavily-used weapon stops being available and the pouch rotates to the next one — for close to zero new mechanism. This also closes a real asymmetry: today weapons are gated only by a roll, while skills are gated by a roll *and* a resource cost; giving weapons a resource cost too makes the two paths symmetric again, consistent with the Mizan premise.

**Deliberately deferred (not in this pass):** "Break" (disable a specific opponent weapon) and "Steal" (temporarily use a specific opponent weapon) skill ideas raised this session. Both are real, fun counterplay tools, but they're an *additional* layer on top of Stamina-driven rotation, not the mechanism that makes rotation happen in the first place — most fights will already show pouch depth from Stamina alone, with or without either skill in play. Recommend picking these up as ordinary `Skill` content (Epic E) once the base cascade is stable, reading/writing the same per-weapon "currently usable" check Stamina already establishes — no additional core-engine change needed. Logged in the backlog (Epic J) rather than specced further here.

**Loadout capacity:** your call was "medium to start (roughly 4–5 weapons, 4–5 active skills, 2–3 passives), later extendable via purchase or achievement rewards." Since `Loadout`/`Inventory` are plain `Array<T>` with no size limit at the type level, capacity is purely a **server-side validation rule keyed off account progression** (e.g. an `unlockedWeaponSlots` count checked when saving a loadout), not a schema change — cheap to make upgradable later without touching the record shape. Logged as a future progression/monetization lever in the backlog (Epic J) rather than designed further here, since it's not core-loop-blocking.

**Passive skills:** `Skill.type` (`ACTIVE`/`PASSIVE`) already exists in the model and already anticipates this split — it just isn't read by anything yet. Per your description, passives sit outside the priority-order pouch entirely (no roll, no fallback — every equipped passive simply applies, all the time), and only `ACTIVE` skills go into the ordered skill pouch above. What a passive's effect actually *is* (a flat stat bonus? a conditional trigger?) is content-authoring territory (Epic E), not decided here.

**Gap closed — Inventory vs. Loadout validation:** nothing today stops a saved `Loadout` from referencing a `Weapon`/`Skill`/`Pet` the account's `Inventory` doesn't actually contain (checked — no such validation exists anywhere in `server/`). When D4's loadout-save flow is implemented, it must validate every item in the submitted `Loadout` exists in `connection.account`'s `Inventory` before persisting — the same "never trust client-supplied references beyond an ownership check" guardrail already established for characters/battles (§6/§13), just applied to items instead of character/battle IDs.

## 5. New common records (`common/model` + `common/network/packet`)

```java
public record ResolvedAction(ActionSource source, String label, int frequency, boolean failed, int damage) {}
public enum ActionSource { WEAPON, SKILL, PUNCH, PET }

public record AttackRequest(long characterId) {}
public record AttackResponse(long battleId, String opponentDisplayName, boolean won,
                             Array<Array<ResolvedAction>> turns) {}
```

**Superseded 2026-07-09** (async matchmaking redesign — see §7): `CombatActionRequest`/`CombatActionResponse` and `MatchmakingRequest`/`MatchmakingFoundResponse` (all four already implemented per the B1/B3 pass) are replaced by the single `AttackRequest`/`AttackResponse` pair above. The live, multi-request "advance this battle by one turn" model they supported no longer applies once a whole battle resolves synchronously in one call — see §7 for why. `opponentDisplayName` is a `String`, not a `characterId`, deliberately: whether the opponent was a real persisted character or a synthesized bot must never be distinguishable from the response shape (§7's transparency decision) or from anything else observable client-side, not just hidden in the UI.

These follow the existing record + `RecordSerializer` convention. **Register them at the end of `KryoConfig.register()`**, after the existing entries — registration order is positional and must stay identical on both sides, per the existing convention documented in `CLAUDE.md`. Do not reorder existing registrations. (The four superseded packet classes and their registrations should be removed, not left dead — nothing should reference them once `AttackRequest`/`Response` land.)

## 6. Networking / handler wiring (M2/M3)

Follows the exact existing pattern (`CLAUDE.md` §"Networking: packet flow end-to-end") — no new pattern introduced:

- Client: `GameClient` sends `AttackRequest` → server `KryoPacketRouter.route()` dispatches to `AttackRequestHandler implements RequestHandler<AttackRequest>` (server/network/handler) → calls `AttackService` (server/service, registered in `ServiceRegistry`) → `AttackService` finds an opponent (§7), builds both `BattleParticipant`s, runs the battle to completion, persists the outcome, and returns `AttackResponse` — all inside this one call.
- Client: response arrives via `NetworkListener` → `PacketHandlerRegistry` dispatches by class → hands the full `turns` array to the M4 `CombatScreen`, which plays every turn back in sequence (extending the single-turn playback §10 already describes, not a new mechanism).

**Security guardrail (unchanged):** `AttackRequestHandler` validates `connection.account != null` and that `AttackRequest.characterId()` belongs to `connection.account`, exactly as `CharacterListRequestHandler`/`DeleteCharacterRequestHandler` already do. There is no client-supplied `battleId`/opponent id to validate against anymore (§7) — the server picks the opponent, so the participation-check half of the old B3 guardrail no longer has anything to check.

## 7. Matchmaking (M3) — async, no live queue

**Redesigned 2026-07-09, replacing the live-queue version B1 originally implemented.** The live-queue model (both players connected simultaneously, a waiting room, a multi-turn session) assumed a form of real-time coordination this game never needed: combat has no manual input, no live decisions, nothing that benefits from both sides being present at once. Forcing that synchronicity imports real-time-matchmaking failure modes — thin-population starvation, Elo-widening unfairness, a "nobody's playing" perception — for no gameplay payoff. The fix is the Clash-of-Clans shape: attack a *persisted snapshot* of an opponent, resolved instantly, no live opponent required.

**Opponent selection**, in order:
1. Query for a persisted character within `±100` Elo (same constant as the old `MatchmakingService.BASE_ELO_RANGE`) of the requester, excluding the requester itself. Pick randomly among candidates, not simply the closest — an always-closest pick makes outcomes too predictable turn over turn.
2. If none found, widen to an unbounded Elo range and try again. Unlike the old design, there's no "wait and see if someone joins" — everything resolves within the one request, so widening happens immediately rather than after a timeout.
3. If still nothing (e.g. an empty table), synthesize a bot character.

**Bot generation.** Stats/loadout drawn from `04-starter-content.md`'s items, assembled into one of a small set of curated archetype templates (tank/dps/dodge-leaning stat distributions) chosen at random per fight, with a total stat budget scaled to the requester's own Elo so difficulty roughly tracks skill/progression. Decided this session: bot fights **count fully** toward Elo and rewards, identically to a real opponent — which means bot calibration is load-bearing, not cosmetic. A miscalibrated bot isn't a curiosity, it's either a farmable exploit or an unexplained wall, and since bots are never disclosed (below), a player has no way to discount a loss as "oh, just a bot." Get the stat-budget-vs-Elo curve right before this ships, and expect a tuning pass same as every other numeric system in this doc.

**Transparency — decided, non-negotiable:** a bot must be indistinguishable from a real opponent, at every layer, not just the UI. `AttackResponse` carries an `opponentDisplayName` (`String`), never a `characterId` the client could use to look anything real up — this is a protocol-level guarantee, not just a client-side display choice. Whether a fight was against a bot is recorded server-side only (see §8's `battle_history.opponent_is_bot` note) for analytics/anti-abuse, never serialized to the client.

**Whole-battle resolution, one response.** `AttackService` builds both `BattleParticipant`s (unchanged — `BattleParticipant.fromCharacter`) into a fresh `BattleSession`/`BattleEngine` (unchanged) and resolves the entire match before responding — no session persists across multiple requests, so there is nothing for a registry to track and nothing to leak if a client disconnects mid-fight (there's no "mid-fight" from the network's perspective). **Implementation gap to close, not just a formality:** `BattleEngine.runToCompletion()` as it exists today only leaves the *final* turn's `TurnResultComponent` behind — each call to `resolveTurn()` clears and overwrites it. Returning the full `turns` array this section's `AttackResponse` needs means either (a) `AttackService` calls `resolveTurn()` itself in a loop and copies each turn's Result Set out immediately after each call (reuses `BattleEngine` as-is, more calling-code responsibility), or (b) `BattleEngine` grows its own accumulating per-participant turn history so `runToCompletion()` can hand back the whole log directly (cleaner call site, a real change to already-tested engine code). Flagging both rather than picking one — this is Claude Code's call at implementation time, see the prompt.

**What this retires:** `MatchmakingService`'s queue (and the two bugs living in it, K2/K3 — see backlog), `BattleSessionRegistry`/`ActiveBattle`'s multi-request session lifecycle, and the "how does the opposing client learn what happened" question (§6, previously open) — there is no live opposing client in this model at all. `BattleSession`/`BattleParticipant`/`BattleEngine`/`ActionResolver`/`DamageCalculator`/`PetResolver` are unaffected; they don't know or care whether they're invoked once synchronously or across several requests.

**Forward-compatible design decision (unchanged):** `BattleSession` participants stay an `Array<BattleParticipant>`, not two hardcoded fields, for the same raids-later reason as before (project plan §8) — nothing about this redesign touches that.

## 8. Persistence additions (M3)

**Updated 2026-07-09 — `elo` already landed, `battle_history` is next.** `V6` went to Stamina and `V7__Add_Character_Elo.sql` already added `characters.elo` (both ahead of plan, from the M2/B1 implementation passes) — `battle_history` is now **V8**, not V6. Checked against the actual `V1__Initial_Schema.sql`: the table is `characters` (plural), PKs are `SERIAL`/`INTEGER` (not `BIGSERIAL`/`BIGINT`).

**New column vs. the original sketch, required by the async/bot redesign (§7):** a bot opponent has no row in `characters` at all, so `opponent_character_id` can't stay `NOT NULL` — and the server needs to know internally which fights were against a bot (analytics, anti-abuse tuning) even though the client is never told (§7's transparency decision). Starting point, revised:

```sql
-- V8__Battle_History.sql
CREATE TABLE IF NOT EXISTS battle_history (
    id SERIAL PRIMARY KEY,
    character_id INTEGER NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    opponent_character_id INTEGER REFERENCES characters (id) ON DELETE CASCADE, -- NULL when opponent_is_bot
    opponent_is_bot BOOLEAN NOT NULL DEFAULT FALSE,
    gold_delta INTEGER NOT NULL,
    experience_delta BIGINT NOT NULL,
    elo_delta INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Note `global_currency` (the "gold" wallet) already lives on `accounts`, not `characters` — reward application touches both tables, not just one. Confirm this split still makes sense before implementing the reward-application story.

Explicit GDD rule to preserve in `CharacterService`/inventory logic: items "lost" in battle via skills are **not** removed from the permanent inventory — only `battle_history` and the character's persisted gold/exp/Elo change. `BattleStateComponent` (in-memory, per §3) is what's discarded at battle end; nothing about item loss should touch the DB.

### 8.1 Reward formulas (Elo/Gold/Exp) — decided 2026-07-10

**Scope, deliberately narrow:** C1 accumulates raw `accounts.global_currency`/`characters.experience`/`characters.elo` numbers only. There is **no leveling mechanic** in this codebase today — `Character.level`/`experience` are written once at character creation (`CharacterDao`'s insert) and never read back for a level-up threshold or stat-growth consequence anywhere. Crediting Exp is safe (it's just accumulating a number) but "what leveling up does" is an entirely separate, unscoped future epic — do not invent a level curve as a side effect of C1. Same reasoning applies to Gold: `accounts.global_currency` has no spending sink yet (no shop/store exists in the built meta layer) either — rewarding now and giving it a sink later is normal sequencing, not a gap to fix here.

**Elo** — standard rating formula, chosen because it's a well-established default, not a bespoke invention:
```
expectedScore = 1 / (1 + 10^((opponentElo - myElo) / 400))
actualScore = won ? 1 : 0
eloDelta = round(K * (actualScore - expectedScore)), K = 32
```
For a bot opponent, treat `opponentElo` as equal to the attacker's own Elo — `BotFactory` already calibrates the bot's stat budget to the attacker's Elo, so this is consistent with that design, gives `expectedScore ≈ 0.5` (a fair coinflip-calibrated adjustment), and needs no separate bot-Elo tracking. This formula naturally produces a negative `eloDelta` on a loss — no special-casing needed for the "loss" branch.

**Gold and Exp** — first pass, explicitly tunable like every other number in this doc (needs a real playtesting/economy pass once there's a Gold sink and a leveling system to validate against):
```
On a win:
  goldDelta = 25 + max(0, round((opponentElo - myElo) * 0.1))
  expDelta  = 50 + max(0, round((opponentElo - myElo) * 0.2))

On a loss (small consolation, decided 2026-07-10 — keeps losing streaks from feeling like dead time):
  goldDelta = 5
  expDelta  = 10
```
The win-side bonus term rewards beating a higher-rated opponent more; it's naturally zero against a bot (bot Elo == attacker's own Elo per above), so bot fights pay exactly the flat base with no bonus — consistent with §7's "count fully, same as real" decision without giving bots either an advantage or a penalty.

## 9. Testability

Because Steps 1–3 are pure functions of (stats, loadout, seeded RNG), `ActionResolutionSystem`/`PetResolutionSystem`/`ResultCompilationSystem` should be unit-testable headlessly (no LibGDX rendering dependency, consistent with the existing "headless-friendly" ECS guideline). This is the first place unit tests make sense in this repo — recommend adding a `core` test source set as part of the M2 stories rather than deferring further. Given how easily probability logic breaks silently during a refactor, story A6 (unit tests using the GDD's three worked scenarios as fixed-seed fixtures) is treated as **P0**, not a nice-to-have — see `docs/planning/00-project-plan.md` §7.

## 10. Client placeholder-rendering seam (M4, no assets required)

M4 doesn't need real art to be built or validated — the goal is to prove the `CombatScreen` layout and Result Set playback timing work, with real assets swapped in during M5 without touching this logic.

- Build placeholders as Scene2D `Image` actors backed by solid-color 1x1 `Texture`s (or a shared "white pixel" region tinted via `setColor()`), laid out in a `Table` like the existing screens — **not** raw `ShapeRenderer` draws inside `render()`. A `ShapeRenderer`-based placeholder would need a structural rewrite later (different drawing pipeline entirely); an `Image`-based one only needs its `TextureRegion` swapped.
- Route every "what does this `ResolvedAction` look like" decision through one small lookup — a `CombatVisualFactory` returning a `Drawable`/`TextureRegion` per `ActionSource`/weapon-or-skill id. Placeholder implementation returns flat-color drawables; the M5 implementation swaps the same method to pull from `AssetLoader`/an atlas. One seam to change, same spirit as `CharacterMapper` being the single place that bridges DTOs into ECS.
- Animate with Scene2D `Action`s (`moveBy`, `fadeOut`, `sequence`, etc.) applied to the `Image` actors, not hand-rolled timers in `render()`. `Action`s apply to any `Actor` regardless of what texture it holds, so the animation logic survives the asset swap unchanged.
- `BitmapFont`/`Label` text for actions ("3x Hammer", "FAILED_CAST") isn't really throwaway placeholder work — a readable combat log alongside the animation is a normal thing to keep in the shipped game.
- What can't be faked cheaply: genuine hand-drawn frame-by-frame sprite animation. Don't attempt that with placeholders — the motion primitives above (`Action`s) are enough to validate timing without it.

## 11. Cross-platform account linking (feeds M6)

Today, `accounts.user_id` is a strict one-to-one unique FK to `users` (checked against `V1__Initial_Schema.sql`). A player logging in via a second platform identity (e.g. Google on mobile, having already played via Steam on desktop) would get an entirely separate account under the current schema — no shared progress. This needs a deliberate fix before mobile ships, and the schema half of it is cheap to do now, before Steam launches and real account data exists.

**Schema change** — new migration, next in sequence after whatever V6/V7 land from Steam/combat work:

```sql
CREATE TABLE IF NOT EXISTS account_identities (
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    user_id    INTEGER NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    linked_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, user_id)
);
```

`user_id UNIQUE` enforces "one identity belongs to exactly one account" while allowing an account to have many linked identities. Login resolves via this join table instead of (or in addition to, during migration) the direct `accounts.user_id` column.

**Linking flow** — an in-session link code, reusing the existing Strategy-pattern handler style:

- `GenerateLinkCodeRequest`/`Response`: while authenticated on device A, server generates a short-lived code tied to `connection.account.id()`.
- `RedeemLinkCodeRequest`/`Response`: while authenticated on device B (a different account/identity), server validates the code and inserts a row into `account_identities` linking device B's `user_id` to device A's `account_id`.
- No dependency on Steam/Apple/Google's own account-linking or federation APIs — both sides are already-authenticated sessions on your own server, which is sufficient trust.

**Merge policy (decided):** if the identity being linked already has its own characters/progress, reject the link — require the player to link before creating progress on the second platform, rather than attempting to merge two independent character rosters (duplicate names, character-slot overflow, overlapping achievements). Simplest to build, no conflict-resolution logic needed. Revisit only if this turns out to frustrate real players after launch.

The linking UX itself (screens, prompts) is scoped to M6 — but doing the `account_identities` migration now, while only `PlatformType.TEST` (and soon `STEAM`) data exists, avoids a live-data migration later.

## 12. Steam integration — technical notes (Epic G)

- **Packaging:** use the existing `construo` path (already configured in `lwjgl3/build.gradle` with `linuxX64`/`macM1`/`macX64`/`winX64` targets, each bundling its own JRE) for the Steam build. Do **not** use the opt-in `enableGraalNative` path for this — GraalVM native-image and JNI-based libraries (which any Java Steamworks wrapper will be) are a much less-traveled combination than a normal bundled JVM, and it's an unnecessary risk to take on for the Steam build specifically.
- **Auth:** implement the `PlatformType.STEAM` branch in `LoginRequestHandler` (currently only `PlatformType.TEST` is handled; everything else is explicitly rejected). The `identity_token` for a Steam login is the Steam auth session ticket, verified server-side against Steamworks. This is additive to the existing login flow, not a rewrite of it.
- **Achievements:** keep the existing DB-backed `AchievementService`/`AchievementDao` as the source of truth. On unlock, additionally call the Steamworks stats API to mirror the unlock for Steam's own UI/notifications — a notification call, not a second source of truth.
- **Explicitly out of scope for v1.0:** Steam Cloud (redundant — server-authoritative Postgres persistence already covers save-sync, which most single-player Steam games don't have and need Cloud specifically to solve) and Steam Input/Deck controller navigation (would require a gamepad focus-navigation pass across every Scene2D/VisUI screen; deferred to a post-1.0 epic if there's demand).
- **Timing:** a JNI-init + `construo`-packaging spike belongs in Alpha (de-risk early, cheap in isolation, painful if discovered during RC instead); the auth branch and achievement sync belong in Beta; SteamPipe depot/branch setup and Valve review submission (budget ~2 weeks lead time) belong in RC.

## 13. Security & operational hardening

Flagged during a technical design review of the current codebase — none of this blocks M2 engineering work, but all of it needs to land before real player/Steam traffic exists:

- **Transport encryption:** `KryoServer` currently runs plain, unencrypted TCP/UDP (checked directly — no TLS layer present). Acceptable for local development; not acceptable once real auth tokens (Steam tickets, Apple/Google identity tokens) cross the open internet. Add TLS or an equivalent encrypted channel before RC.
- **Hardcoded config:** `DatabaseManager.init()` hardcodes DB credentials (`postgres`/`postgres`) and `Main.java` hardcodes `TCP_PORT`/`UDP_PORT`. Both need to move to environment-based configuration before going live — consistent with the existing note in `CLAUDE.md` not to add new config plumbing without being asked, but this is the point at which it should be asked for.
- **Ownership validation:** see §6 above — every new handler that acts on a specific character or battle must validate ownership against `connection.account`, following the pattern already established elsewhere in the codebase.
- **Awareness, not urgent:** `DatabaseManager`'s eager-init singleton may make future service-layer unit tests harder to isolate (revisit if Epic C testing gets painful — not worth refactoring pre-emptively). No CI currently exists; a minimal `gradlew build`/`test` GitHub Actions workflow on push would catch a broken merge early, particularly valuable once many changes are flowing through Claude Code.

## 14. Faction-gated skills (crit vs. dodge) — resolved 2026-07-15, see §16

Raised during lore/worldbuilding pass (`docs/planning/03-lore-and-worldbuilding.md` §4/§6). **No longer open** — built as the dedicated Faction branch of the skill tree (§16), not a separate mechanic. The formula/design reasoning below stands as written and is exactly what §16's faction nodes implement; only the "where does this live" question has been settled.

The lore's two-faction identity (Crimson Accord = certainty/submission, Skyborn = evasion/defiance) suggests a natural mechanical split: a Crimson-aligned skill that raises critical-hit chance, a Skyborn-aligned skill that raises dodge chance, kept in deliberate balance so neither faction is strictly stronger. This maps onto stats the GDD already defines rather than introducing new ones — STR/INT already governs "Damage Floor & High-Tier Action Weight" (a natural home for crit), and SPD already governs "Dodge Chance and Turn Priority." Recommended implementation shape, **once picked up**: faction-gated `Skill` records through the existing Loadout/Skill system (`common/model`), not a new mechanic layer — consistent with this codebase's preference for extending an established pattern over inventing one.

**Update — partially unblocked by the §4.2 damage-detail pass. Corrected to match the current (range-based) formula** — an earlier version of this note referenced the pre-§4.4 flat-`itemPower` formula, which no longer exists; this now matches `rawDamage = randomInt(item.minAttack, item.maxAttack) + statBonus` (§4.2) exactly. Damage's random component is now the `randomInt(item.minAttack, item.maxAttack)` draw itself, which gives "critical hit" a natural, cheap definition: a hit is a **crit** when that draw lands in the top band of the item's range (e.g. ≥ 80% of the way from `minAttack` to `maxAttack`), applying a flat bonus multiplier (e.g. ×1.5) to that hit's `finalDamage` (applied after mitigation, alongside `statBonus` either way — order doesn't matter for a flat multiplier). Under that definition:
- **Crimson's skill** adds an independent flat chance (its own tunable %, via a separate d100 check) to force any hit into a crit regardless of the natural roll — a clean, single-number knob.
- **Skyborn's skill** adds a flat percentage-point bonus directly to the existing `dodgeChance` formula (§4.2) — same knob shape, mirrored. Recommend keeping both skills' bonuses subject to the same 75% dodge ceiling (§4.2) rather than letting Skyborn's skill exceed it, so the global cap stays meaningful; revisit only if Skyborn otherwise feels underpowered relative to Crimson in practice.
- **No longer open, locked 2026-07-15:** ×1.5 crit multiplier, 80% range-position threshold for a natural crit, and a symmetric +5 percentage points per rank for both Crimson's (`faction.crimson.n1`, crit) and Skyborn's (`faction.skyborn.n1`, dodge) faction node, 3 ranks (max +15 each) — same first-pass-number caveat as everything else in this doc, still awaiting the Monte Carlo tuning pass §4.2 already flags. One correctness note the implementation pass surfaced: the crit definition is meaningless when `minAttack == maxAttack` (a flat-range weapon/skill has no "top band" to speak of) — natural crit must be explicitly guarded to never fire on a zero-width range, or every flat-damage fixture in the existing test suite would start critting on every hit.

**Data model gap (flag for Claude Code, not renamed here):** `Faction` (`common/model/Faction.java`) already exists and is already wired onto `Character.faction` — but it only has placeholder values `A`/`B`. Once faction identity is picked up, rename to match the lore doc's naming (`CRIMSON_ACCORD`/`SKYBORN` or similar) — this is a pure rename plus a DB/save-data check for any existing rows referencing the old values, not a redesign.

## 15. Character progression: leveling, stat points, skill points — decided 2026-07-10

New scope, part of the broader a–k expansion discussed 2026-07-10 (project plan §2/§8 note the full list and sequencing). This section covers only the first, foundational slice: the exp curve, level-up stat points, and per-battle skill points. Skill tree spending, quests, shop, durability, and weight capacity are separate future sections — this one exists so those have a levelling/currency foundation to build on.

**Guiding principle.** Level governs access and pacing; Elo stays the primary skill signal. This matters specifically because daily battle count is a monetizable/extensible lever (item c) — paying for more attempts must not translate into paying for competitive advantage. Elo already protects this for free: more games converge a player to their *true* rating faster, they don't raise the ceiling. Keeping level-based stat growth a minor contributor to combat outcomes (next to build choice and RNG) is what keeps this property intact — a whale who levels faster still loses to better-built, similarly-rated opponents, because matchmaking pairs by Elo, not level.

**Exp curve.** `expNeededForLevel(L) = 8 × L² − 8` (total lifetime `characters.experience` needed to have reached level `L`, anchored so level 1 needs 0 — not 8 — since a brand-new character starts at level 1 with 0 exp by definition; `experience` itself never resets, it's a running total same as today). The increment to advance from `L` to `L+1` is `8 × (2L + 1)` — 24 exp for level 1→2 (under one battle), growing smoothly with no cliffs. **Correction, 2026-07-15:** an earlier draft of this formula omitted the `− 8` anchor term, which would have made the very first level-up cost 32 exp rather than the 24 this section's own worked example assumes — caught and fixed during the L1 implementation pass, not left for the code to quietly disagree with the doc.

**Level cap: 50** (hard for now, extensible later like every other account lever in this expansion — not a structural limit, just today's number). 25 is a mid-game milestone (ranked unlock, system design pending its own section), not the ceiling.

**Worked example (first-pass, needs a real playtesting pass like every other number in this doc):** against the existing battle-reward formula (§8.1: win ≈ 50-90 exp, loss = 10), a ~50%-win-rate player earning ~175 exp/day (5-battle daily cap) reaches level 25 in about a month and level 50 in roughly 3.5-4 months. An ~80%-win-rate player (~250 exp/day) reaches level 25 in about 3 weeks and level 50 in roughly 80 days. The daily battle cap, not the curve shape, is what actually bounds how fast this can be bought or rushed — extra battles scale exp income linearly, they don't skip the curve.

**Stat points.** 3 per level gained (looped if one exp delta crosses more than one threshold in a single battle — rare, but possible against a big Elo-gap bonus). A new **per-stat lifetime cap of 60** replaces `CharacterCreationScreen.MAX_STAT_VALUE` (currently `20`) — that constant was a UI placeholder only, never an intended lifetime ceiling; it needs to change to 60 (ideally pulled into one shared constant both the creation screen and the new spend-validation logic reference, so the two never drift apart). A level-50 character earns 147 points from leveling plus the creation-time pool of 20 — enough to push 2-3 focused stats toward the cap, never all 8 — the scarcity is deliberate, forcing real build specialization rather than eventual maxing of everything.

Needs: `characters.unspent_stat_points INTEGER NOT NULL DEFAULT 0` (new column, V9). A new request/response pair — `AllocateStatPointsRequest(long characterId, Stats delta)` / `AllocateStatPointsResponse(Stats newStats, int unspentStatPoints)` — where `delta` is how much the client wants to add to each of the 8 stats in one round trip (batch, not one-point-at-a-time). Server validates: ownership (existing guardrail pattern), `sum(delta) ≤ unspentStatPoints`, and every resulting stat value `≤ 60`; applies atomically and decrements the balance.

**Skill points — separate currency, per battle, not per level.** Deliberately modelled on Silkroad Online's split: level gates *access* to a skill, skill points gate whether you can actually afford to learn/upgrade it — a level-50 character can still be skill-point-poor on their top-tier nodes. Same win/loss branch shape as gold/exp (§8.1): **3 on a win, 1 on a loss.** Needs `characters.skill_points INTEGER NOT NULL DEFAULT 0` (V9, same migration as `unspent_stat_points`). Applied inside `RewardService.applyRewards`'s existing transaction — same row, same commit, no new transactional seam needed. No spend logic yet; that's the skill tree's job when it's designed.

**Every-10-level bonus roll (10, 20, 30, 40, 50).** On crossing a multiple of 10, roll a chance (first-pass 10%) at a bonus reward — an item, a pet, or a skill-restoration scroll — from a small curated table. **This is the first system that needs to grant an item to a character after creation, and nothing in the codebase can do that today by design.** `BattleLeavesInventoryAloneTest` (story C2) currently asserts, structurally, that no `UPDATE` statement in `CharacterDao` touches `inventory`/`loadout` at all — deliberately, so nothing could silently start writing there. Implementing this bonus roll means adding exactly one such write path and **updating that test on purpose** to allow it by name while still failing on any other, accidental one — not a regression, the exact moment that test's own docstring anticipated. Because quests, the shop, and achievements will all need the same "grant an item" capability shortly after, build it as one shared method (e.g. `CharacterDao.addInventoryItem`/a small `InventoryService`), not a one-off for this bonus roll alone.

**Where leveling is computed.** Natural home is `RewardService.applyRewards`, extended: after applying the exp delta, check whether the new cumulative `experience` crosses one or more `cumulativeExpForLevel` thresholds (looping for a multi-level jump), grant `3 × levelsGained` unspent stat points, and roll the every-10 bonus for each multiple of 10 crossed — all inside the same `useTransaction` block already writing `characters`/`accounts`/`battle_history`, so a level-up can never partially apply. `AttackResponse` should grow to report this too (levels gained, stat points gained, any bonus reward), symmetric with how it already reports gold/exp/Elo — exact packet shape to be finalized when this becomes an implementation prompt.

**Migration V9** (next after V8):
```sql
-- V9__Add_Character_Progression_Currencies.sql
ALTER TABLE characters ADD COLUMN unspent_stat_points INTEGER NOT NULL DEFAULT 0;
ALTER TABLE characters ADD COLUMN skill_points INTEGER NOT NULL DEFAULT 0;
```

## 16. Skill tree — decided 2026-07-15

Second slice of the a–k expansion (§15 built the leveling/currency foundation this spends). Also **closes §14's long-open faction-skill question** — Crimson's crit skill and Skyborn's dodge skill are built here, as faction-locked tree content, not a separate mechanic.

**Shape: 4 branches, smaller than the first pass.** Physical, Magical, and Universal each get 3 level-gated tiers (not the originally-floated 5) at levels 1/20/40, with 2 nodes per tier — 18 nodes across the three. A fourth branch, Faction, is not level-gated (available from character creation, once a faction is chosen) and only shows nodes matching the character's own `Faction` — a Crimson character never sees Skyborn content or vice versa. It starts with exactly one node per faction (the crit/dodge skills) and is deliberately built to grow post-launch without restructuring. **20 nodes total for v1.0.**

**Passives are selectable, not automatic — and they share one pool with actives.** Reconsidered mid-design: rather than a separate `Loadout.passives` field with its own capacity, `PASSIVE`-type `Skill`s go into the *same* `Loadout.skills` array as `ACTIVE` ones, competing for the same slots. This is simpler (no new `Loadout`/`Inventory` field at all) and adds a real build-tension axis: every slot spent on a passive's permanent bonus is a slot not spent on an active option. It also means a passive only benefits a character while it's actually equipped, not merely learned — keeping power tied to build choice rather than unbounded accumulation from grind time (the same principle §15 leans on for Elo vs. level). **Shared cap: 5 skill slots total (active + passive combined)**, replacing whatever "medium pouch size" placeholder A7/§4.4 was left with — extensible later via J7 like every other slot lever in this expansion.

**Required engine change, not optional:** `ActionResolver`'s turn cascade currently walks `Loadout.skills()` assuming every entry is castable — true only because nothing but `ACTIVE` skills has ever existed in that list. Mixing `PASSIVE` entries in requires an explicit filter to `ACTIVE` when picking a turn's action; `PASSIVE` entries still count toward the 5-slot cap at save time, they just never enter the cascade roll.

**`Skill` needs new fields, meaningful only when `type() == PASSIVE`:**
```java
public record Skill(
    long id, String name, String description, SkillType type,
    int manaCost, Difficulty difficultyToAct, int minAttack, int maxAttack,   // ACTIVE-only, unchanged
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
`StatName` is new — `Stats` has no existing way to address "which of the 8 stats" as data, only as named record components. Deliberately a bounded, closed set of effect types rather than a generic modifier system — auditable, and every type maps onto a formula the combat engine already has (dodge/crit caps from §4.2/§14, Mana/Stamina costs from §4.2/§4.4, weight capacity from the not-yet-designed topic #3). Passive effects are read once, at `BattleParticipant.fromCharacter()` — filtering `loadout.skills()` to `PASSIVE` — the same single translation boundary every other per-battle derivation already goes through, not scattered live lookups.

**Ranks and cost**, escalating by tier so higher tiers stay a real grind even at high level (3 ranks per node, first-pass numbers, same "needs playtesting" caveat as everything else):

| Tier | Level gate | Cost per rank | Cost to max (3 ranks) |
|---|---|---|---|
| 1 (Physical/Magical/Universal) | 1 | 1 skill point + 10 gold | 3 SP + 30 gold |
| 2 (Physical/Magical/Universal) | 20 | 3 skill points + 60 gold | 9 SP + 180 gold |
| 3 (Physical/Magical/Universal) | 40 | 6 skill points + 150 gold | 18 SP + 450 gold |
| Faction | — (faction only) | 3 skill points + 60 gold | 9 SP + 180 gold |

Learning rank 1 of a node grants the corresponding `Skill` into the character's `Inventory` — reusing L3's shared "grant an item" capability, not a separate pathway. Upgrading a rank replaces the previous rank's `Skill` instance with the new one (same `Inventory` slot; if it was equipped in the `Loadout`, the upgraded version stays equipped in the same position) — a service-layer replace, not an add.

**Restoration, per the original spec exactly:**
- **Single-node restore:** refunds 75% of the skill points spent on that node's current rank (not the gold — a real sunk cost), costs one skill-restoration scroll plus a gold fee, drops the node one rank. Gold fee and scroll acquisition are shop/quest concerns, priced when those are designed.
- **Full tree reset:** refunds 100% of all skill points ever spent across every node (still not gold), shop-obtainable. Resets every node to un-learned.

**Persistence:** `characters.skill_tree JSONB NOT NULL DEFAULT '{}'::jsonb` — a node-id-to-rank map, same JSONB pattern already used for `stats`/`inventory`/`loadout` and explicitly flagged in the project plan as forward-compatible for exactly this kind of addition. Node ids are simple readable strings (e.g. `physical.t1.n1`, `faction.crimson.n1`) since tree *content* (which nodes exist, their costs/effects) isn't DB-driven yet — same as weapons/skills/pets today, deferred to E1 (M5).

**Migration V10** (next after V9):
```sql
-- V10__Add_Character_Skill_Tree.sql
ALTER TABLE characters ADD COLUMN skill_tree JSONB NOT NULL DEFAULT '{}'::jsonb;
```

**New packets, shape to be finalized when this becomes an implementation prompt:** a learn-or-upgrade request (character id + node id — "learn" and "upgrade rank" are the same action, whichever rank comes next), a single-node restoration request (character id + node id + scroll consumption), and a full-reset request (character id, shop-gated). All follow the existing ownership-guardrail pattern.

## 17. Loadout constraints: weight capacity and weapon durability — decided 2026-07-15

Third slice of the a–k expansion, designed as one pass since both modify the same layer (what's allowed in / usable from the weapon pouch). Neither replaces anything already built — §4.3's soft per-item `comfortableWeight` penalty and Stamina's in-battle rotation both stand unchanged; these are two new, independent axes layered on top.

**Weight capacity — a hard gate, distinct from the existing soft penalty.** `maxCarryWeight = strength × 3`, plus any flat bonus from an equipped `WEIGHT_CAPACITY_BONUS` passive (§16 already has this effect type ready). *(Built 2026-07-16. The bonus term is summed by the pure `core.combat.PassiveEffects`, extracted from `BattleParticipant.fromCharacter`'s inline §16 aggregation for this: the gate runs at loadout-save time with no Ashley `Engine` in reach, and duplicating the sum would have let the capacity that admits a loadout drift from the one combat plays. `CharacterService.saveLoadout` and `fromCharacter` now share the one implementation.)* Applies only to weapons — skills and pets have no `weight` dimension in the data model. Enforced at loadout-save time: the sum of equipped weapons' `weight` must not exceed `maxCarryWeight`, or the weapon is rejected outright, same validation moment as the existing Inventory-vs-Loadout ownership check (§4.4). This is a total-budget constraint across the whole pouch; §4.3's per-item penalty still separately reduces the draw roll for any individual item that's heavy relative to STR, even when it fits inside the budget — the two coexist without conflict.

**Durability.** New `Weapon` fields: `int maxDurability`, `int currentDurability`. Adding mutable per-copy state directly to `Weapon` is consistent with this codebase's actual persistence shape — `Inventory`/`Loadout` are JSONB blobs embedded in `characters`, not normalized rows against a shared item catalog, so each owned copy is already independent data. First-pass: flat `maxDurability = 20` for every starter weapon regardless of tier/rarity — deliberately not inventing per-rarity numbers ahead of the real content-authoring pass (E1/M5).

**Resolved during implementation, 2026-07-15 — `Inventory`, not `Loadout`, is the single source of truth for durability.** Since `Loadout`'s `saveLoadout` capability (§16/Epic M) lets a client submit its own copy of a weapon at any time, and `BattleParticipant.fromCharacter` originally read combat weapons straight from `Loadout`, two independent mutable copies of the same weapon's durability could drift — a post-battle decrement written only to `Inventory` would never be seen by combat. Fixed by having `fromCharacter` cross-reference each equipped weapon's id against `Inventory` for its current durability, falling back to `Loadout`'s own copy only if the id isn't found there. `Loadout` now means only "this item id is equipped"; all durability reads and writes go through `Inventory`.

**Decrement rule:** −1 durability per battle, for any weapon that fired at least once that battle (not per individual hit — simpler, and doesn't punish one long battle disproportionately). At the 5-battle daily cap (system design pending its own section — see project plan §2 item c), that's roughly 4 days of steady use on a main weapon before it needs repair.

**At 0 durability:** treated as unaffordable in the pouch cascade — reuses the exact mechanism Stamina-insufficiency already uses, rather than a new "does nothing" branch. A broken weapon is skipped by the priority walk exactly like an unaffordable one, rotating to the next weapon or falling through to punch. **Repair** is a shop action: resets `currentDurability` to `maxDurability`; gold price deferred to the shop epic, same treatment as the skill-restoration scroll's price.

**Required data-model extension — a real gap, not optional:** `ResolvedAction` currently carries only a coarse `ActionSource` category (`WEAPON`/`SKILL`/`PUNCH`/`PET`) and a display `label` string — nothing identifies *which specific weapon* fired on a given turn when a pouch can hold several. Durability tracking needs this, so `ResolvedAction` needs a new field — an item id, populated whenever `source` is `WEAPON`/`SKILL`/`PET`, irrelevant for `PUNCH` — before durability bookkeeping can be built at all.

**C2 interaction, again:** durability decrement is the *second* capability (after L3's item-grant, §15) that needs to write to `characters.inventory`. Unlike gold/exp/Elo's atomic `+`/`-` updates, this one is a JSONB read-modify-write — find the used weapon(s) inside the embedded array, decrement, write the whole column back — so it needs its own `CharacterDao.updateInventory(long characterId, String inventoryJson)` and another deliberate, named exception carved into `BattleLeavesInventoryAloneTest`, exactly the moment that test anticipated a second time.

> **Corrected when built, 2026-07-16 — neither turned out to be needed.** L3 had already built `updateInventory`, and §18 (written after this section) settled the rule that supersedes the sentence above: every feature that mutates `inventory` from here on is *a different in-memory transformation before the same write call*, not a new write path, so **one** C2 exception covers all of them. The sanctioned-writer list stayed at exactly two (`updateInventory`, `updateLoadout`) and durability added no method. The milestone grant and the decrement share one locked read and one write, wearing before granting. What C2's round-trip test *did* need was narrowing: it asserted the stored inventory JSON was byte-for-byte identical after a battle, which durability makes wrong rather than violated — it now asserts no stored item is ever taken away (the rule C2 was always defending), keeps the byte-for-byte check on `loadout`, and pins the decrement positively.

**Concurrency, deliberately simple:** lock the character row (`SELECT ... FOR UPDATE`) inside the same transaction rather than build real optimistic-concurrency handling — one account can't realistically run two attacks for the same character at once given the daily battle cap, so this is a safe simplification for v1.0, not a shortcut expected to bite later.

**Where this is computed:** `RewardService.applyRewards`'s existing transaction grows again — after the reward writes, tally which equipped weapons appear at least once in `AttackResult.turns` (now identifiable via `ResolvedAction`'s new item id), decrement and write back `inventory`, all atomic with the reward write. `AttackResponse` likely needs to report any weapon that just broke (hit 0 durability) so the client can surface it — exact packet shape deferred to implementation-prompt time, same as §15/§16's pending packet details.

> **Built as described, 2026-07-16, with the packet field deferred.** No `AttackResponse` "your weapon broke" field this pass — same treatment as §14's crit flag, deferred to the not-yet-built combat screen (M4), which is where it would actually be surfaced. `ResolvedAction.itemId` is populated uniformly for `WEAPON`/`SKILL`/`PET` (`0L` for `PUNCH` and Burned casts), not just weapons: it costs nothing extra now and hands §18's consumable-charge tracking the same capability for free.

## 18. Shop, potions, and pet health — decided 2026-07-15

Fourth slice of the a–k expansion. The shop only makes sense once there are real things to price — §16 (skill points) and §17 (durability) gave it its first two, and this section adds potions and pet health, designed together because both turned out to need the same underlying question answered: what does "restore a resource" mean in a game where a whole battle resolves in one round trip.

**One principle up front: the shop is gold-only.** No premium/real-money currency is sold for anything in it. IAP in this game stays scoped to the account-level pacing levers already decided separately (character slots, daily battle count) — never to buying combat power directly. This is the line that keeps philosophy (3) intact.

**Shop catalog, priced now that the underlying mechanics exist:**
- **Repair** (a targeted service against a specific owned weapon, not a catalog purchase): `5 gold × missingDurability`. Fully repairing a 0/20 weapon costs 100 gold.
- **Skill-restoration scroll**: 50 gold to buy (separate from M4's already-specified gold fee to *use* one).
- **Skill tree reset token**: 1000 gold, consumed to trigger M5's full reset.
- **Pet health repair** (same shape as weapon repair, see below): `5 gold × missingPetHealth`.

**Alternate payment via redeemable tokens — added for the weekly-quest connection.** Repair and pet-health-restore both need to accept a **token in place of gold**, not just a gold price. A "Repair Token" and a "Pet Care Kit" fully restore one weapon/pet for free when redeemed instead of paid — earned from weekly quests once that epic is designed (not yet — quests are next in this sequence). This is deliberately different from the "free daily regen" idea that got walked back: weekly cadence against a ~5-day depletion cycle means the free path arrives roughly once per cycle, a real relief valve without eliminating the reason to ever pay. The exact weekly reward table is deferred to the quest epic; what's locked here is that the repair/restore action itself needs to support two payment paths from the start.

**Potions — a third `SkillType`.** `ACTIVE`/`PASSIVE` (§16) gain a sibling, `CONSUMABLE`. A consumable skill restores a resource (`ResourceType`: `HEALTH`, `MANA`, or `STAMINA`) when a player-set percentage threshold is crossed, using the same `min`/`max`-range-plus-stat-bonus shape as damage, and depletes a `charges` count on use:
```java
public enum ResourceType { HEALTH, MANA, STAMINA }
```
`Skill` gains `ResourceType restoresResource`, `int thresholdPercent`, `int minRestore`, `int maxRestore`, `int charges` — meaningful only when `type() == CONSUMABLE`. This resolves a real question rather than dodging it: HP/Mana/Stamina reset *between* battles (unchanged — no persistent cross-battle HP, per the earlier decision against that direction), but they fluctuate freely *within* one exactly as they always have, so mid-battle healing has genuine purpose without any change to that model.

**Turn resolution, contained to one new check.** Before the existing weapon/skill cascade, walk equipped `CONSUMABLE` skills in priority order; the first whose resource is currently below its threshold and still has `charges > 0` fires — heals, consumes a charge, and becomes that turn's entire action (the normal cascade is skipped that turn; the pet's independent action is unaffected). If none trigger, the existing cascade proceeds completely unchanged. This is the game's first conditional/reactive logic, deliberately isolated to a single early check rather than threaded through the resolver. `CONSUMABLE` skills share §16's 5-slot pool with `ACTIVE`/`PASSIVE` — a slot spent on a safety net is a slot not spent on offense or passives, same tension as everything else in that pool.

**Pet health — mirrors durability's shape exactly, on purpose.** `Pet.healthPoint` finally becomes meaningful as max health; a new `currentHealth` depletes by 1 per battle the pet actually acts in (not per hit — same reasoning as §17's durability). At 0, the pet's bonus action is simply skipped that battle — soft, never a block on attacking at all, matching the same non-blocking principle already working for durability. Repair is gold-priced (above) or token-redeemed. **Explicitly not resolved here:** `Pet.defence` stays exactly as vestigial as it was when `04-starter-content.md` flagged it — this depletion is wear from being used, not damage taken from an opponent, so nothing here gives `defence` a formula to belong to. A real "pets can be targeted and hurt by the opponent" mechanic is a separate, bigger question this section deliberately doesn't answer.

**Persistence — no new migration needed anywhere in this section.** Pet's `currentHealth` and Skill's new `CONSUMABLE` fields are, like durability, embedded JSONB data (`Inventory`/`Loadout` columns), not new relational schema — exactly the forward-compatibility the project plan already banked on. **One consolidation worth stating plainly:** L3's item-grant, §17's durability write, and this section's charge-depletion and pet-health write all ultimately funnel through one primitive — `CharacterDao.updateInventory(long characterId, String inventoryJson)`, a full-blob read-modify-write. `BattleLeavesInventoryAloneTest` (C2) needs exactly **one** deliberate exception carved in for that method, not one per feature — every feature that mutates `inventory` after this point is a different in-memory transformation before the same write call, not a new write path each time.

## 19. Quest system — decided 2026-07-15

Fifth slice of the a–k expansion. Cheaper to build than it looks, because `battle_history` (C1/C3) already records everything a quest needs to check.

**Progress needs no new tracking.** `battle_history` stores `won`, `gold_delta`, `experience_delta`, `elo_delta`, and `created_at` per battle, per character. "Win N battles this week" is `COUNT(*) FROM battle_history WHERE character_id = ? AND won = true AND created_at > periodStart` — computed live, never drifts from reality, nothing to keep in sync. The only new state genuinely needed is **claiming**, since a completed quest shouldn't be repeatably claimed within the same period:
```sql
-- V11__Add_Quest_Claims.sql
CREATE TABLE IF NOT EXISTS quest_claims (
    id SERIAL PRIMARY KEY,
    character_id INTEGER NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    quest_id VARCHAR(64) NOT NULL,
    period_start TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (character_id, quest_id, period_start)
);
```
The `UNIQUE` constraint enforces "at most one claim per character per quest per period" at the database level, same taste already used for `account_identities.user_id UNIQUE` — not left to application logic to get right.

**Unifies with the daily battle cap.** The daily battle cap (project plan §2 item c) never got its own mechanism specified until now — it turns out to want the identical shape: `COUNT(*) FROM battle_history WHERE character_id = ? AND created_at > startOfToday`, checked in `AttackService` before allowing an attack. Same table, same period-boundary computation, reused for both rather than built twice.

**Quest content, v1.0 — three quests total, deliberately small:**
- **Daily** (resets on the same daily boundary as the battle cap): "Win 2 battles today" → 1 skill-restoration scroll.
- **Weekly** (resets Monday UTC): "Win 10 battles this week" → a **choice of 2-3 rewards** (a weapon/pet, a Repair Token, a Pet Care Kit) — the player picks which to claim rather than getting a random one, so the weekly reward always feels earned, not gambled.
- **Repeatable** (no reset, always available): "Win 1 battle" → a small gold amount or a potion charge, **capped at 3 claims per day**. Uncapped would make this the dominant income source and quietly undermine the §8.1 reward-formula tuning; the cap keeps it a bonus, not the main event.

## 20. Account levers: daily battle cap and character slots — decided 2026-07-15

Sixth slice of the a–k expansion. Smaller than the preceding sections — the counting mechanism for the daily battle cap was already settled in §19 (reuse `battle_history`'s period-boundary query); what's new here is the extensibility itself.

**Two different bonus fields on two different tables, deliberately not one shared mechanism.** The daily battle cap is enforced per-character (§19's query is `WHERE character_id = ?`), so its bonus lives on `characters`. Character slots are inherently an account-level resource — slots belong to the account, not any one character — so that bonus lives on `accounts`.

- **Daily battle cap:** `characters.bonus_daily_battles INTEGER NOT NULL DEFAULT 0`. Effective cap = `5 + bonus_daily_battles`. `AttackService` checks `COUNT(*) FROM battle_history WHERE character_id = ? AND created_at > startOfToday` against the effective cap *before* resolving the battle — reject early rather than resolve combat and discard it. The rejection needs a real reason code back to the client (not a silent drop), so the UI can distinguish "daily limit reached" from other failure cases — exact packet/response shape deferred to implementation-prompt time, same treatment as every other pending packet detail in this expansion.
- **Character slots:** `accounts.bonus_character_slots INTEGER NOT NULL DEFAULT 0`. `CharacterService.createCharacter`'s existing hardcoded check becomes `>= (3 + account.bonusCharacterSlots)` in place of `>= 3`.

Both are **permanent additive bonuses**, not temporary boosts — consistent with "count should be extensible" and the permanent-unlock framing already used for skill points (§15).

**Migration V12** (next after V11):
```sql
-- V12__Add_Account_Progression_Levers.sql
ALTER TABLE characters ADD COLUMN bonus_daily_battles INTEGER NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN bonus_character_slots INTEGER NOT NULL DEFAULT 0;
```

**Three grant sources, scoped honestly rather than all resolved here:**
- **IAP** — out of scope for this section; purchase/fulfillment is a platform concern (Epic G/Steam). This section only defines the fields an eventual IAP fulfillment path writes into.
- **Achievement rewards** — a real gap, confirmed by reading `AchievementService`: it is currently read-only (`getPlayerAchievements` only), with no unlock-detection or reward-granting logic anywhere in the codebase. Achievement-triggered slot/battle bonuses depend on that machinery existing, which is the not-yet-designed achievements epic (item j). Flagged as a dependency, not solved here.
- **Quest rewards** — the generic reward-application mechanism already exists (L3/O4's shared `updateInventory`/reward primitives), so a future quest *could* grant either bonus the same way. None of Epic P's three already-locked quests currently do — noted as an available extension point, Epic P left unchanged.

## 21. Ranked ladder — decided 2026-07-15

Seventh slice of the a–k expansion. Elo already updates on every battle today (`characters.elo`, moved by `RewardService.applyRewards` on every attack), and opponent matching already works by finding a real candidate within an Elo band before falling back to a bot (`AttackService.selectOpponent`, §7). Ranked builds on top of both without changing either.

**A second, separate Elo track, not a re-labeling of the existing one.** Normal battles keep moving `characters.elo` exactly as shipped — zero change, zero regression risk to C1-C3. Ranked is opt-in and level-25-gated, so daily-quest/grinding fights never carry competitive stakes unless the player deliberately chooses ranked — consistent with the "daily activities, not hardcore grind, not stressful" philosophy behind this whole expansion.

**Gating and matchmaking.** `AttackRequest` gains `BattleMode mode` (`NORMAL` default, `RANKED`). Server rejects a `RANKED` request when `character.level < 25` — same guardrail style as the existing ownership check, exact rejection-reason packet shape deferred like every other pending packet detail in this expansion. Ranked opponent selection reuses `AttackService`'s existing band→widen→bot algorithm verbatim, keyed off ranked Elo instead of normal Elo and restricted to level-25+ candidates (one new DAO query, same shape as the existing `findOpponentCandidates`). Bot fallback is unchanged — `BotFactory.createBot(rankedElo)` in place of `createBot(elo)`; `BotFactory` needs no code change since it's already Elo-parameterized.

**Ranked Elo is computed live, not stored as a mutable column.** Consistent with how quests and the daily battle cap already compute live from `battle_history` rather than keep duplicate tracking state: `battle_history` gains `battle_mode VARCHAR(16) NOT NULL DEFAULT 'NORMAL'` and a nullable `ranked_elo_delta INTEGER`, set only on `RANKED` rows. A character's current ranked Elo is `1000 + SUM(ranked_elo_delta) WHERE character_id = ? AND battle_mode = 'RANKED'`; standing *as of a past point in time* (needed for the monthly ladder) is the identical sum bounded by `created_at <= t` — no snapshot job, no scheduler, nothing this codebase doesn't already have infrastructure for. Fine at v1.0 scale; would materialize into a real column later only if this becomes a measured hot path.

**Per-battle rewards are unchanged in ranked mode.** Gold/Exp/skill points use the exact same formulas regardless of mode — no new reward-tuning knob, no reopening the K=32/Elo-gap-bonus concerns already flagged in C1-C3's close-out. Ranked's only distinctiveness is the separate Elo track and the ladder it feeds.

**Monthly ladder — first-pass reward tiers, placeholder-caliber like every other number in this expansion:**
- Calendar month, UTC boundary — same convention as the weekly quest.
- Top 1: exclusive title + a rare pet/weapon + a large gold payout.
- Top 2-10: gold + a Repair Token/Pet Care Kit bundle.
- Top 11-100: modest gold.
- Below top 100: no reward — ladder placement itself is the showcase (character page, §22), not a currency faucet.

**Claiming mirrors the quest system exactly.** A claim request re-validates the player's standing server-side against the bounded-SUM computation at claim time (never trusts a client-reported rank), rejects if already claimed for the period:
```sql
-- V13__Add_Ranked_Battle_Mode_And_Ladder_Claims.sql
ALTER TABLE battle_history ADD COLUMN battle_mode VARCHAR(16) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE battle_history ADD COLUMN ranked_elo_delta INTEGER;
CREATE TABLE IF NOT EXISTS ladder_claims (
    id SERIAL PRIMARY KEY,
    character_id INTEGER NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    period_start TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (character_id, period_start)
);
```
Same `UNIQUE`-at-the-database-level taste already used for `quest_claims` and `account_identities.user_id`.

## 22. Achievements & character statistics — redesigned 2026-07-15

Eighth slice of the a–k expansion, and a genuine redesign rather than a patch. Grounding first: `achievement_definitions`/`account_achievements` already exist — 9 seeded achievements, a read endpoint, a client screen — but nothing anywhere in the codebase ever writes to `account_achievements`. Every achievement is permanently locked for every account today; the tables were scaffolded (likely for early UI testing) but unlock-detection was never built. This section replaces that scaffold rather than extending it.

**Scope is per-achievement, not a single global choice.** `achievement_definitions` gains `scope` (`ACCOUNT` or `CHARACTER`). Some seeded achievements are inescapably account-level facts (Day One Survivor, Pioneer — about *when the account was created*, not anything a character did); others are inescapably character-level combat feats (Perfect Storm, First Blood — read directly off `battle_history`, which is keyed by `character_id`). Forcing one scope onto both would be wrong for half the existing content.

**Criteria are declarative, not hand-written per achievement.** `achievement_definitions` gains `criteria_type` (a small enum) and `criteria_params JSONB`, interpreted by one generic, pure `AchievementEvaluator` (Ashley/DB-free, same shape as `ActionResolver`) instead of bespoke Java per achievement. v1.0 vocabulary:
- `TOTAL_WINS {threshold}`, `WIN_STREAK {threshold}`, `FASTEST_WIN_TURNS {maxTurns}` — character scope, evaluated against `battle_history`.
- `CHARACTER_LEVEL {threshold}` — character scope, reuses L1's level-up check.
- `ITEM_ACQUIRED {rarity|category}` — character scope, reuses L3's shared item-grant hook.
- `ACCOUNT_CREATED_BEFORE {date}` — account scope, checked once at account creation.

This turns "add a new achievement" into a data insert later, not a code change — the same direction Epic E eventually takes weapons/skills/pets, arriving here first because achievement content is far simpler (a threshold, not a stat block).

**No stored progress — computed live, same rule as everything else in this expansion.** `progress_data JSONB` is dropped entirely. Counter/streak/threshold criteria are evaluated against `battle_history` and current character/account state at check time, exactly like quests (§19) and the ranked ladder (§21) — nothing to keep in sync, nothing that can drift.

**Unlock ledger, renamed and scope-aware.** `account_achievements` → `achievement_unlocks`, gains a nullable `character_id` (set for `CHARACTER` scope, null for `ACCOUNT` scope). A plain `UNIQUE` can't cover both cases (`NULL` is never self-equal), so two partial indexes replace it:
```sql
CREATE UNIQUE INDEX achv_unlock_account_uq ON achievement_unlocks (account_id, achievement_id) WHERE character_id IS NULL;
CREATE UNIQUE INDEX achv_unlock_character_uq ON achievement_unlocks (account_id, achievement_id, character_id) WHERE character_id IS NOT NULL;
```
Unlock write is `INSERT ... ON CONFLICT DO NOTHING` against the relevant index, atomic with reward application (same transaction-extension pattern as §15/§17/§18/§21) — idempotent by construction, so re-evaluating an already-unlocked achievement is a safe no-op.

**Checkpoints, not an event bus.** This codebase has no scheduler or pub/sub anywhere, and doesn't need one for this — the evaluator is called from a fixed, small set of existing checkpoints: post-battle (inside `RewardService`'s transaction — covers `TOTAL_WINS`/`WIN_STREAK`/`FASTEST_WIN_TURNS`), post-level-up (`CHARACTER_LEVEL`), post-item-grant (`ITEM_ACQUIRED`, via L3's shared capability), post-account-creation (`ACCOUNT_CREATED_BEFORE`, one-time). Exact per-achievement criteria assignment for all 9 existing achievements (which threshold, which category) is implementation-prompt-level content work, not re-derived here.

**Rewards generalize beyond XP.** `achievement_definitions` keeps `xp_reward` and adds `gold_reward`, `badge_id`, `title_id`, `bonus_character_slots`, `bonus_daily_battles` — the last two reuse Epic Q's exact account fields, closing the loop item (j) asked for ("character count upgrade" via achievement). XP/gold target whichever character triggered the unlock; `bonus_character_slots`/`bonus_daily_battles` always apply at the account level regardless of scope. A badge is simply the fact of an unlocked achievement whose definition has a non-null `badge_id` — no separate collection table. Titles need one new mutable field: `characters.equipped_title VARCHAR(50)`, settable only to a title the account has actually unlocked (validated by joining `achievement_unlocks`/`achievement_definitions`).

**Two small additions for the "showcase" pillar:** `points INTEGER NOT NULL DEFAULT 10` (a Gamerscore-style completionist number, distinct from XP, summed into a displayed Achievement Score) and `hidden BOOLEAN NOT NULL DEFAULT FALSE` (secret achievements, shown as "???" until unlocked) and `category VARCHAR(30)` (COMBAT/PROGRESSION/COLLECTION/ONBOARDING, for UI grouping).

**Steam forward-compatibility, noted but not built here.** Steam has no concept of a character — achievements are strictly scoped to (SteamID, App ID); a server-authoritative game syncs via `ISteamGameServerStats::SetUserAchievement`, keyed by SteamID, not the client-only `SetAchievement`. This scope/criteria design doesn't foreclose a later Steam sync layer: a `CHARACTER`-scoped achievement still collapses to one Steam call the first time *any* character on the account satisfies it (idempotent — Steam's own API is documented safe to call repeatedly), while which character did it stays an internal-only fact. No `steam_api_name` column is added now — that's Epic G's call once account-linking (Epic H/M6) actually exists, since nothing can sync before a SteamID is linked to an account.

**Statistics — computed live from `battle_history`, unchanged in spirit from the rest of this expansion.** Total wins/losses, win percentage, current win streak, last 5 matches (opponent name, result, Elo delta, turn count) — all already answerable from existing columns. "Fastest win" needs one new column, `battle_history.turn_count`, populated from `battleEngine.turnNumber()`/`turns.size()` — already computed by `AttackService`, just never persisted before now.

**Character page is a new read-only aggregate, no new persistence of its own:** character info + §15's level/exp + the live battle statistics above + unlocked achievements/badges (filtered to the account, joined against this character where scope is `CHARACTER`) + Achievement Score + equipped title. Exact packet shape deferred to implementation-prompt time, same as every other pending packet detail in this expansion.

**Migration V14:**
```sql
-- V14__Redesign_Achievements_And_Character_Statistics.sql
ALTER TABLE battle_history ADD COLUMN turn_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE achievement_definitions ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'ACCOUNT';
ALTER TABLE achievement_definitions ADD COLUMN category VARCHAR(30);
ALTER TABLE achievement_definitions ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE achievement_definitions ADD COLUMN points INTEGER NOT NULL DEFAULT 10;
ALTER TABLE achievement_definitions ADD COLUMN criteria_type VARCHAR(30) NOT NULL DEFAULT 'ACCOUNT_CREATED_BEFORE';
ALTER TABLE achievement_definitions ADD COLUMN criteria_params JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE achievement_definitions ADD COLUMN gold_reward INTEGER NOT NULL DEFAULT 0;
ALTER TABLE achievement_definitions ADD COLUMN badge_id VARCHAR(50);
ALTER TABLE achievement_definitions ADD COLUMN title_id VARCHAR(50);
ALTER TABLE achievement_definitions ADD COLUMN bonus_character_slots INTEGER NOT NULL DEFAULT 0;
ALTER TABLE achievement_definitions ADD COLUMN bonus_daily_battles INTEGER NOT NULL DEFAULT 0;

ALTER TABLE account_achievements RENAME TO achievement_unlocks;
ALTER TABLE achievement_unlocks DROP COLUMN progress_data;
ALTER TABLE achievement_unlocks ADD COLUMN character_id INTEGER REFERENCES characters (id) ON DELETE CASCADE;
ALTER TABLE achievement_unlocks DROP CONSTRAINT account_achievements_account_id_achievement_id_key;
CREATE UNIQUE INDEX achv_unlock_account_uq ON achievement_unlocks (account_id, achievement_id) WHERE character_id IS NULL;
CREATE UNIQUE INDEX achv_unlock_character_uq ON achievement_unlocks (account_id, achievement_id, character_id) WHERE character_id IS NOT NULL;

ALTER TABLE characters ADD COLUMN equipped_title VARCHAR(50);
```
The dropped-constraint name is a placeholder — the actual auto-generated name from `V1__Initial_Schema.sql`'s `UNIQUE (account_id, achievement_id)` needs confirming against the live schema at implementation time. Re-seeding the 9 existing achievements with real `scope`/`criteria_type`/`criteria_params`/`points` values is content work for that same pass, not re-derived here.

## 23. Character customization — decided 2026-07-15

Ninth and final slice of the a–k expansion, and the smallest — purely cosmetic, no stats, no formulas, no interaction with anything else in this expansion. Name uniqueness, faction selection, and stat-point distribution already exist in `CharacterCreationScreen`/`CharacterService.createCharacter`; this section is only the leftover piece — gender, hair, and skin. Nothing like it exists anywhere in the codebase today (`Character` has no appearance fields at all), and M4 is still placeholder-rendering (CLAUDE.md) — nothing consumes this data yet. It's captured now so character creation doesn't need a second pass once real art lands in M5.

**`characters.appearance JSONB NOT NULL DEFAULT '{}'::jsonb`** — same "flexible now, real content catalog later" reasoning already used for `skill_tree` (§16): storing `{"gender", "hairType", "hairColor", "skinColor"}` as a blob avoids inventing real atlas/sprite IDs before real art exists to back them.

**v1.0 ships a small curated set, not open free text** — validated server-side in `CharacterService.createCharacter` alongside the existing name-uniqueness and slot-count checks, same code path. First pass: 2 genders, 4 skin tones, 4 hair colors, 3 hair types. Same "small curated set, not data-driven yet" precedent already used for starter weapons/skills/pets (`04-starter-content.md`) and the achievement seed data (§22) — expands once Epic E1/M5's real art pipeline exists to define actual options.

**Set once at creation; no edit endpoint in v1.0.** A later "restyle" item is a natural extension of Epic O's shop, not built now.

**Migration V15:**
```sql
-- V15__Add_Character_Appearance.sql
ALTER TABLE characters ADD COLUMN appearance JSONB NOT NULL DEFAULT '{}'::jsonb;
```

**New packets** (shape to be finalized at implementation-prompt time, same treatment as every other pending packet in this expansion): a quest-status request returning each of the three quests' live progress and claimed/unclaimed state for the current period, and a claim request (character id + quest id) that re-validates the criteria server-side — never trusts a client-reported "I completed this" — checks `quest_claims` for an existing row this period, applies the reward via the shared `updateInventory`/reward-application primitives, and inserts the claim row.
