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

## 14. Open design question: faction-gated skills (crit vs. dodge)

Raised during lore/worldbuilding pass (`docs/planning/03-lore-and-worldbuilding.md` §4/§6) — not scoped or built, flagged here so it isn't lost and so it lands in the right document when picked up.

The lore's two-faction identity (Crimson Accord = certainty/submission, Skyborn = evasion/defiance) suggests a natural mechanical split: a Crimson-aligned skill that raises critical-hit chance, a Skyborn-aligned skill that raises dodge chance, kept in deliberate balance so neither faction is strictly stronger. This maps onto stats the GDD already defines rather than introducing new ones — STR/INT already governs "Damage Floor & High-Tier Action Weight" (a natural home for crit), and SPD already governs "Dodge Chance and Turn Priority." Recommended implementation shape, **once picked up**: faction-gated `Skill` records through the existing Loadout/Skill system (`common/model`), not a new mechanic layer — consistent with this codebase's preference for extending an established pattern over inventing one.

**Update — partially unblocked by the §4.2 damage-detail pass. Corrected to match the current (range-based) formula** — an earlier version of this note referenced the pre-§4.4 flat-`itemPower` formula, which no longer exists; this now matches `rawDamage = randomInt(item.minAttack, item.maxAttack) + statBonus` (§4.2) exactly. Damage's random component is now the `randomInt(item.minAttack, item.maxAttack)` draw itself, which gives "critical hit" a natural, cheap definition: a hit is a **crit** when that draw lands in the top band of the item's range (e.g. ≥ 80% of the way from `minAttack` to `maxAttack`), applying a flat bonus multiplier (e.g. ×1.5) to that hit's `finalDamage` (applied after mitigation, alongside `statBonus` either way — order doesn't matter for a flat multiplier). Under that definition:
- **Crimson's skill** adds an independent flat chance (its own tunable %, via a separate d100 check) to force any hit into a crit regardless of the natural roll — a clean, single-number knob.
- **Skyborn's skill** adds a flat percentage-point bonus directly to the existing `dodgeChance` formula (§4.2) — same knob shape, mirrored. Recommend keeping both skills' bonuses subject to the same 75% dodge ceiling (§4.2) rather than letting Skyborn's skill exceed it, so the global cap stays meaningful; revisit only if Skyborn otherwise feels underpowered relative to Crimson in practice.
- **Still open:** the actual bonus magnitude (what %, exactly) needs the same Monte Carlo validation as everything else in §4.2 — the two skills have different *felt* impact (crit is flashy/offensive, dodge is a negation/defensive feel) even once their expected-value contribution is tuned equal, so simulate before committing to a number rather than guessing a symmetric percentage.

**Data model gap (flag for Claude Code, not renamed here):** `Faction` (`common/model/Faction.java`) already exists and is already wired onto `Character.faction` — but it only has placeholder values `A`/`B`. Once faction identity is picked up, rename to match the lore doc's naming (`CRIMSON_ACCORD`/`SKYBORN` or similar) — this is a pure rename plus a DB/save-data check for any existing rows referencing the old values, not a redesign.
