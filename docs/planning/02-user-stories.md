# User Stories / Backlog

Last updated: 2026-07-09 (M3 matchmaking pass: B1 marked done — matchmaking queue/pairing, `BattleSessionRegistry`, `server`→`core` wiring, and `CombatActionRequestHandler` now resolving a real turn. Previously: M2 combat-core pass — A1 revisited + A2/A3/A5/A7/B2/B3)

Priority: **P0** blocks the milestone, **P1** important but not blocking, **P2** nice-to-have / can slip.
Size: **XS** (minutes, extends an existing pattern — see Epic K), **S** (~1 session), **M** (~2-3 sessions), **L** (multi-session, consider splitting further before starting).
Status: `todo` / `in-progress` / `done`. Update this file as part of each sprint close-out (see `00-project-plan.md` §9).

---

## Epic A — Combat Simulation Core (server, headless) — M2

**A1.** As the server, I resolve a character's turn action using the GDD cascade (Weapon Draw roll vs STR → Skill Cast roll vs WIS + mana check → fallback Punch), so combat outcomes follow the Mizan rules.
Acceptance criteria: given fixed stats/loadout/RNG seed, `ActionResolutionSystem` deterministically produces the same `ResolvedAction` as hand-computed from the GDD scenarios (see GDD §5 for the three worked examples to use as test fixtures).
Priority: P0. Size: M. Status: done. *(Revisited for the pouch model: `ActionResolver` now walks `Array<Weapon>`/`Array<Skill>` pouches (A7) with the single-item overload retained so the three GDD-scenario tests pass unchanged. The gate roll uses slot 0's `effectiveStrength`/`effectiveWis`; the effective-stat formulas were recalculated to confirm the original scenario outcomes hold under seeds 42/123456789. Server-side invocation still unwired — see B3.)*

**A2.** As the server, I run an independent Insight check each turn to decide whether the pet acts, appended to the Result Set regardless of the character's own action outcome.
Acceptance criteria: pet action frequency scales with `Tameness` + `insight` per GDD §2/§3 Step 2; pet check runs even when the character action was `Burned` (GDD Scenario 3). Concrete `Tameness`-to-modifier mapping and the effective-insight formula are now specced in `01-system-design-combat-engine.md` §4.3 — use those numbers rather than re-deriving.
Priority: P0. Size: M. Status: done. *(Pure `PetResolver` (Insight roll vs `effectiveInsight = insight + tamenessModifier`, frequency `1 + effectiveInsight/30`) + `PetResolutionSystem` writing `PetActionComponent`. `PouchResolutionTest.petActs_evenWhenCharacterCastIsBurned` asserts the pet fires after a Burned cast (GDD Scenario 3); `BattleEngine` also runs the pet decision every turn regardless of the character outcome.)*

**A3.** As the server, I compile the character action + pet action into an ordered Result Set array matching the GDD's `[3x Hammer, 2x Wolf]` shape.
Acceptance criteria: `ResultCompilationSystem` output serializes cleanly into `CombatActionResponse.actions()`. Note: `ResolvedAction` currently has no damage field — system design §4.2 now specs the damage/mitigation/dodge formulas this story (and A5) will need a place to store; adding that field is in scope for this story or a tightly-coupled follow-up, not a separate epic.
Priority: P0. Size: S. Status: done. *(`ResultCompilationSystem` merges `CharacterActionComponent` + `PetActionComponent` into an ordered `TurnResultComponent.actions` via the shared `ResultCompiler` (`[character, pet]`), which serializes directly into `CombatActionResponse.actions()`. `ResolvedAction` gained an `int damage` field (5th component; 4-arg convenience ctor keeps the decision layer damage-agnostic) — filled by `BattleEngine` at application time; the ECS compile step is decision-only, damage 0.)*

**A4.** As a developer, each battle uses a seeded RNG so I can reproduce and unit test any outcome.
Acceptance criteria: seed stored on `BattleSession`; same seed + same inputs → same Result Set across runs.
Priority: P1. Size: S. Status: done. *(Minimal `BattleSession` holds the seed + a `SplittableRandom`; `DeterminismTest` proves same-seed reproducibility through both the pure resolver and the ECS system.)*

**A5.** As the server, I track ephemeral Battle State (spent mana, spent stamina, pet-used-this-turn) that resets when a battle ends and never touches the database.
Acceptance criteria: `BattleStateComponent` holds `spentMana`/`spentStamina`/`petUsedThisTurn` (system design §3 — the field was originally a single `weaponDepleted` boolean; corrected to `spentStamina` once A7's multi-weapon pouch made a single boolean insufficient) and exists only on entities inside an active `BattleSession`; nothing in it is written to Postgres (GDD §4). Damage application, mitigation, dodge, turn priority, and win-condition logic (system design §4.2) will likely need their own system/story once B2 (`BattleSession` with two participants) lands — this story is scoped to the state container itself, not the resolution logic that reads/writes it.
Priority: P0. Size: M. Status: done. *(`BattleStateComponent` (`spentMana`/`spentStamina`/`petUsedThisTurn`, no `weaponDepleted`) added, plus a `StaminaComponent` mirroring `ManaComponent` for the `maxStamina` pool. Populated only inside a battle (`BattleParticipant.fromCharacter`); `BattleEngine` mirrors each spend onto `spent*`. Nothing here is persisted — see the resource-model note in the close-out flags below.)*

**A6.** As a developer, I have a `core` test source set so combat resolution logic can be unit tested headlessly.
Acceptance criteria: `gradlew.bat core:test` runs; at least the three GDD §5 scenarios are encoded as tests, using fixed seeds against `SplittableRandom` (see system design §4/§9).
Priority: **P0** (promoted from P1 — probability-driven logic breaks silently during refactors and is hard to catch by playtesting alone; this is cheap insurance). Size: S. Status: done. *(JUnit 5 test source set added to `core`; the three §5 scenarios (seed 42) plus punch-fallback and mana-boundary edge cases are asserted. Tests cover the character-action column only — pet actions await A2.)* Once the damage/mitigation/stamina formulas (§4.2/§4.4) get their own tests, use `docs/planning/04-starter-content.md`'s items as fixtures rather than inventing new placeholder numbers per test.

**A7.** As a player, I can equip a small priority-ordered set of weapons and active skills ("pouch") rather than exactly one of each, so my loadout has real build depth.
Acceptance criteria: `WeaponSlotComponent`/`SkillSlotComponent` hold an ordered collection instead of a single item; `ActionResolver` walks the list on a successful roll and uses the first weapon the character can afford from their **Stamina** pool (`remainingStamina >= weapon.staminaCost()`) or the first skill they can afford from **Mana** (`currentMana >= skill.manaCost()`) — both fallbacks are the same shape, see system design §4.4. Requires the data-model additions in §4.2 (`Weapon.staminaCost`, `Character.maxStamina`, `BattleStateComponent.spentStamina`) alongside the weapon min/max-range change. Existing A1/A6 tests must still pass against a single-item pouch (regression), plus new tests for multi-item mana- and stamina-driven fallback, including the "nothing affordable" case for both.
Priority: P1. Size: M. Status: done. *(`WeaponSlotComponent`/`SkillSlotComponent` now hold `Array<Weapon>`/`Array<Skill>` (index = priority; ACTIVE-only for skills). `ActionResolver` rolls one gate on slot 0 (`effectiveStrength`/`effectiveWis`) then walks by Stamina/Mana affordability. Data-model additions landed (`Weapon.minAttack/maxAttack/staminaCost`, `Skill.minAttack/maxAttack`, `Pet.minAttack/maxAttack`, `Character.maxStamina` + V6 migration, `BattleStateComponent.spentStamina`). `PouchResolutionTest` covers stamina/mana fallthrough and "nothing affordable" for both branches with `04-starter-content.md` fixtures; A1/A6 regression tests pass unmodified in behavior.)*

---

## Epic B — Matchmaking & Sessions — M3

**B1 — superseded 2026-07-09 by B4.** *Kept for the record, not deleted: the live-queue design below turned out to be the wrong shape for a game with no real-time input (see B4's rationale) — the combat engine it drove (`BattleEngine`/`BattleParticipant`/`BattleSession`/`ActionResolver`, all of A1–A7) is unaffected and fully reused by B4; only the queue/session-lifecycle plumbing below is retired. K2/K3 (the two bugs found in that plumbing) are retired along with it, not fixed — see their close-out notes.*

As a player, I can queue for a match and get paired with an opponent in a similar Elo range.
Acceptance criteria: `MatchmakingRequest`/`MatchmakingFoundResponse` round-trip; pairing prefers closest Elo available, falls back to widening range after a timeout (define timeout value when implementing).
Also closes two gaps B3 deliberately left open, both of which become live the moment this story makes `battleId` resolve to a real `BattleSession`:
- **Participant check.** B3 validates that `CombatActionRequest.characterId` is owned by `connection.account`, but nothing validates `battleId`. Today that is inert (the handler's turn resolution is a logging stub, so `battleId` is never dereferenced). Once a session is looked up by that id, an *owned* character could submit actions into a battle it is not a participant of. The guardrail needs both halves: character belongs to account **and** character is a participant in that battle.
- **Rejections are silently dropped.** `CombatActionRequestHandler` logs and early-returns without sending anything, so a client awaiting a `CombatActionResponse` gets no reply. That matches `DeleteCharacterRequestHandler` for the *unauthenticated* branch, but diverges from it for the *ownership* branch, which still answers with `success=false`. `CombatActionResponse` already exists — decide whether an ownership/participant rejection should send a failure response rather than hang the client.
Priority: P0. Size: M. Status: done. *(`MatchmakingRequest`/`MatchmakingFoundResponse` packets added + registered at the end of `KryoConfig`. `MatchmakingService` (in `ServiceRegistry`) owns a transient in-memory queue: pairs on closest Elo within ±100, lifting the range entirely for an entry that has waited ≥15s (widening is evaluated lazily on the next incoming request rather than by a background timer — with nobody else queued there is nothing to widen into). On a pair it builds a throwaway Ashley `Engine` + randomly-seeded `BattleSession` + two `BattleParticipant`s, registers them in the new `BattleSessionRegistry` (`server/combat`, `AtomicLong` ids, `ActiveBattle` bundle holding the engine plus a characterId→participant map), and pushes `MatchmakingFoundResponse` to **both** connections with each other's character id. `server` now depends on `core` (pulls vis-ui/gdx-freetype onto the server classpath transitively — harmless). New `V7__Add_Character_Elo.sql` + narrow `CharacterDao.getElo`/`findById`; `elo` deliberately stays off the shared `Character` record until C1 needs to write it. `CombatActionRequestHandler` now resolves a real turn: ownership check → participant check → `BattleEngine.resolveTurn()` → `CombatActionResponse` built from the requester's own `TurnResultComponent`, with `BattleSession.end()` + registry eviction the moment `isOver()`. First `server` test source set added (24 tests): matchmaking pairing/widening/queueing/cleanup, registry lifecycle, both handlers' ownership+participant rejections, and an integration test proving matchmaking → session → engine → response carries real `damage`.*

*Both listed gaps closed: the **participant check** is enforced in `CombatService.resolveTurn` (an owned character is rejected unless it is a combatant in that specific `battleId`). **Rejections still drop silently** — decided deliberately, not by omission: `CombatActionResponse` has no success/error field today, and adding one overlaps with the open question of how the opposing client learns a turn's outcome (below), which will likely reshape the packet anyway. Both belong in one decision.*

*Two open design questions raised for close-out, flagged rather than silently answered:* (1) `CombatActionRequest.skillId` is dead — the §4.1–§4.4 cascade is fully probabilistic and gives the player no action to choose, so the field is ignored and every request is treated as "advance this battle by one turn". Removing it from the packet is a design call. (2) `resolveTurn()` resolves **both** combatants' Result Sets in one call, so the response only naturally covers the requester's own side; **how the opposing client learns what happened this turn is undecided** (server push? the other client polls and gets a cached result?). Only the requester's side is implemented. This also leaves both clients currently able to advance the same battle twice by each sending a request.*

**B2.** As the server, I create and track an authoritative `BattleSession` bridging connected clients once matched.
Acceptance criteria: session holds participants as `Array<BattleParticipant>` (not hardcoded `characterA`/`characterB` fields — see system design §7, this leaves raids possible later without a rewrite), a `SplittableRandom` seed, and per-participant `BattleStateComponent`; cleaned up when the battle ends. Only two participants are ever populated at launch.
Priority: P0. Size: L. Status: done. *(`BattleSession` now holds `Array<BattleParticipant>` + `end()` cleanup; `BattleParticipant` wraps an Ashley `Entity` (state in components, incl. its `BattleStateComponent` per §7) with a `fromCharacter` battle-setup factory. `BattleEngine` fixes priority once (speed, seeded-coinflip tiebreak), resolves per-turn Result Sets with per-hit dodge/damage/win-condition, the kill-prevents-counter rule, and the turn-40 cap (HP% → SPD → coinflip). `BattleEngineTest` proves the priority-order kill prevents the counter-hit. Matchmaking-driven session creation is B1 (still todo); this delivered the session model + turn engine it will feed.)*
**Partially superseded 2026-07-09:** "bridging connected clients" no longer applies — B4 resolves a battle synchronously within one request, so nothing tracks a session across multiple requests anymore (`BattleSessionRegistry`/`ActiveBattle` retire with B1). `BattleSession`/`BattleParticipant`/`BattleEngine` themselves are unchanged and still exactly what B4 calls.

**B3.** As the server, I validate that combat-related requests (starting with `CombatActionRequest`) reference a `characterId` owned by `connection.account`, matching the ownership-check pattern already used in `CharacterListRequestHandler`/`DeleteCharacterRequestHandler`.
Acceptance criteria: a request referencing a character not owned by the connection's account is rejected, not silently processed.
**Partially superseded 2026-07-09:** the participant-in-this-battle half (added during B1's close-out) retires along with `battleId` itself — `AttackRequest` (B4) carries no client-supplied battle or opponent id, the server picks the opponent, so there is nothing left for that check to validate. The ownership check (character belongs to account) carries forward unchanged into `AttackRequestHandler`.

**B4.** As a player, I can attack a persisted opponent (or a synthesized bot if none is available) and see the result immediately, without needing anyone else online.
Rationale (replacing B1): this game has no real-time input — no manual actions, no live decisions — so requiring both players present at once bought nothing and imported real-time-matchmaking failure modes (thin-population starvation, Elo-widening unfairness, a "dead game" perception) for a genre that doesn't need synchronicity at all. See system design §7 for the full redesign discussion.
Acceptance criteria:
- `AttackRequest(characterId)` → server picks an opponent: a random persisted character within ±100 Elo, widened to unbounded if none found, then a synthesized bot as the last resort (system design §7's bot-generation approach). Ownership-checked (character belongs to `connection.account`) exactly as B3 established; no participant/battle-id check needed since none is client-supplied.
- The entire battle resolves synchronously in this one call (`BattleEngine` run to completion, not one turn at a time) and `AttackResponse` carries every turn's Result Set, not just the last one — requires closing `BattleEngine.runToCompletion()`'s current gap where only the final turn's `TurnResultComponent` survives (system design §7 flags two implementation options, pick one).
- Bot fights count fully toward Elo/Gold/Exp, identical to a real opponent (decided 2026-07-09) — which makes the Elo-vs-stat-budget calibration in system design §7 load-bearing, not cosmetic.
- Bot-vs-real is never distinguishable from the client's perspective at any layer, not just the UI: `AttackResponse` exposes `opponentDisplayName` (a `String`), never a `characterId` the client could resolve. Whether a fight was against a bot is recorded server-side only (`battle_history.opponent_is_bot`, system design §8) for internal analytics.
- `CombatActionRequest`/`CombatActionResponse` and `MatchmakingRequest`/`MatchmakingFoundResponse` (all four already shipped) are removed, along with `MatchmakingService`, `BattleSessionRegistry`, and `ActiveBattle` — not left dead alongside the new code.
Priority: P0. Size: L. Status: todo.
Priority: P0. Size: S. Status: done. *(`CombatActionRequest`/`CombatActionResponse` packets added + registered at the end of `KryoConfig` (with `ResolvedAction`/`ActionSource`). `CombatActionRequestHandler` → `CombatService.isCharacterOwnedBy` (new `CharacterDao.isOwnedByAccount`, fails closed) rejects unauthenticated connections and non-owned characters with a log + early return, matching `CharacterListRequestHandler`/`DeleteCharacterRequestHandler`. Wired through `KryoPacketRouter`/`ServiceRegistry`. Scoped to the ownership guardrail only — actually running the engine tick server-side needs B1 (matchmaking) + `server`→`core`, deliberately deferred; see close-out flags. Two follow-ups this story left open — validating `battleId` participation, and whether a rejection should answer with a failure response instead of dropping silently — are recorded as acceptance criteria on B1, since both only become reachable once `battleId` resolves to a live session.)*

---

## Epic C — Persistence & Rewards — M3

**C1.** As a player, my Gold, Exp, and Elo update after a battle concludes.
Acceptance criteria: `battle_history` row inserted; `accounts.global_currency` and `characters.experience`/`characters.elo` updated in the same transaction (see system design §8 for the account/character split).
Priority: P0. Size: M. Status: todo.

**C2.** As a player, items "lost" in battle via skills are not actually removed from my permanent inventory.
Acceptance criteria: explicit test that a battle involving item loss leaves `characters.inventory` unchanged (GDD §4 rule).
Priority: P1. Size: S. Status: todo.

**C3.** As a developer, `battle_history` exists so future stats/leaderboard features have data to read.
Acceptance criteria: migration V6 applied cleanly on a fresh DB and on top of V1–V5.
Priority: P2. Size: S. Status: todo.

---

## Epic D — Combat Client & UX — M4

**D1a.** As a player, I see my character's turn actions play back in sequence based on the Result Set, using placeholder visuals (no real art needed yet).
Acceptance criteria: new `CombatScreen` wired through `ScreenFactory`/`ScreenRouter` (same pattern as existing screens); plays back `ResolvedAction`s in order using solid-color `Image` actors + `BitmapFont` labels routed through a `CombatVisualFactory` seam, animated with Scene2D `Action`s (see system design §10) — not raw `ShapeRenderer`.
Priority: P0. Size: L. Status: todo. *(Split into "screen + static display" then "animation/timing" if it drags.)*

**D1b.** As a player, I see real art instead of placeholders once assets exist.
Acceptance criteria: `CombatVisualFactory` swapped to pull `Drawable`s from `AssetLoader`/the real atlas; no changes needed to `CombatScreen` layout, playback, or animation logic. Small story precisely because D1a used the right seam.
Priority: P1 (M5). Size: S. Status: todo.

**D2.** As a player, pet actions are visually distinguished from my own character's actions.
Priority: P1. Size: M. Status: todo.

**D3.** As a player, a failed cast ("Burned") reads clearly as a failure, not a no-op.
Acceptance criteria: distinct visual/audio treatment for `ResolvedAction.failed() == true`.
Priority: P1. Size: S. Status: todo.

**D4.** As a player, I can equip/change my loadout (weapons, skills, pets) before entering matchmaking.
Acceptance criteria: new loadout-management screen reading/writing `Loadout`; the record already exists, only the UI is missing. Screen must let the player set weapon/active-skill **priority order** within the pouch (A7/system design §4.4), and must warn when an equipped weapon's `weight` exceeds the character's `comfortableWeight` (system design §4.3) — e.g. "Too heavy for your Strength — reduced draw chance" — so the tradeoff is visible before the fight, not discovered mid-battle. Server-side save handler must validate every item in the submitted `Loadout` actually exists in the account's `Inventory` before persisting (system design §4.4) — same ownership-guardrail pattern as character/battle IDs, applied to items.
Priority: P0. Size: M. Status: todo.

---

## Epic E — Content & Data — M5

**E1.** As a designer, weapons/skills/pets are data-driven (DB rows or JSON, not hardcoded Java) so balance can be tuned without a redeploy.
Acceptance criteria: `docs/planning/04-starter-content.md` is a seed for this, not a replacement — it exists to unblock A6/A7/E2 test fixtures before this story lands, expect to expand well past its 3 weapons/4 skills/4 pets.
Priority: P1. Size: M. Status: todo.

**E2.** As a player, there's more than one viable build (at least one Physical-path and one Magical-path test character) to validate the symmetric stat design actually feels balanced.
Acceptance criteria: use `docs/planning/04-starter-content.md`'s Longsword-vs-Lightning-Bolt pairing (calibrated against the same compensation heuristic as system design §4.3) as the starting point for this validation, extended with a Monte Carlo pass per §4.2's recommendation rather than manual playtesting alone.
Priority: P1. Size: M. Status: todo.

---

## Epic F — Stretch: Additional platforms — M6

**F1.** As a developer, `android` and `lwjgl3`-equivalent `ios` modules are wired into `settings.gradle`, matching what `README.md` already documents but the repo doesn't yet contain.
Priority: P2. Size: L. Status: todo.

**F2.** As a player using a controller (e.g. Steam Deck), I can navigate every screen without a mouse/keyboard.
Acceptance criteria: gamepad focus-navigation across all Scene2D/VisUI screens; Steam Deck Verified checklist items addressed.
Priority: **Deferred, not in scope for v1.0** (decided during Steam-planning discussion — keyboard/mouse only at launch; revisit as a post-1.0 epic if there's real demand). Size: L. Status: todo (deferred).

---

## Epic G — Steam Release

See `docs/planning/00-project-plan.md` §5.1 and system design §12 for full context. Foundations already in place: `PlatformType.STEAM` exists in the auth model; `construo` already builds native distributables for all three desktop OSes.

**G1.** As a developer, I've validated that a Steamworks JNI wrapper initializes and pumps callbacks correctly inside a `construo`-built executable, before building the full integration on top of it.
Acceptance criteria: a minimal spike — SteamAPI init + one callback (e.g. overlay activation) — works in a locally-built `construo` distributable, not just `gradlew run`. Use the `construo` path, not `enableGraalNative`.
Priority: P0 (do early — Alpha — to de-risk before more Steam work is built on top). Size: S. Status: todo.

**G2.** As a Steam player, I can log in using my Steam identity.
Acceptance criteria: `LoginRequestHandler` handles `PlatformType.STEAM` (currently only `TEST` is implemented, everything else is explicitly rejected); Steam auth session ticket verified server-side.
Priority: P0. Size: M. Status: todo.

**G3.** As a player, my in-game achievement unlocks also show up in Steam's UI/notifications.
Acceptance criteria: existing `AchievementService` stays the source of truth; on unlock, additionally call Steamworks stats API to mirror the unlock.
Priority: P1. Size: S. Status: todo.

**G4.** As the developer, the Steam store page is live well before launch to start accumulating wishlists.
Acceptance criteria: store page published (capsule art, description, at minimum placeholder screenshots) — this is a marketing/non-engineering task, doesn't block or get blocked by other stories, and should start no later than Beta.
Priority: P1. Size: M. Status: todo.

**G5.** As the developer, the game is packaged and submitted through SteamPipe for release.
Acceptance criteria: depot/branch configuration done; build submitted to Valve review at least ~2 weeks before target launch date.
Priority: P0 (RC). Size: M. Status: todo.

**G6 (explicitly out of scope for v1.0).** Steam Cloud save sync.
Rationale: server-authoritative Postgres persistence already covers save-sync; redundant for this game specifically. Status: skipped, revisit only if a real gap is found.

---

## Epic H — Cross-Platform Account Linking — M6

See system design §11 for full schema/flow detail. Needed once mobile ships so players can carry progress between Steam and mobile identities.

**H1.** As a developer, the schema supports one account having multiple linked platform identities.
Acceptance criteria: new `account_identities` join table (`account_id`, `user_id UNIQUE`, `linked_at`) added via migration; login path updated to resolve account via the join. Do this **before** Steam ships, while there's no production data to migrate.
Priority: P0 (pull forward — cheap now, expensive as a live-data migration later). Size: M. Status: todo.

**H2.** As a player, I can generate a short-lived code on one device to link my account to a second platform identity.
Acceptance criteria: `GenerateLinkCodeRequest`/`Response` and `RedeemLinkCodeRequest`/`Response`, following the existing `RequestHandler`/`ServiceRegistry` pattern; redeeming a valid code inserts a row into `account_identities`.
Priority: P0 (M6). Size: M. Status: todo.

**H3.** As a player, if I try to link an identity that already has its own characters/progress, linking is rejected rather than silently merging or losing data.
Acceptance criteria: explicit rejection with a clear reason code if the target identity already owns characters. (Merge policy decided: block, don't merge — see project plan §5.2.)
Priority: P0 (M6). Size: S. Status: todo.

---

## Epic I — Security & Ops Hardening

Cross-cutting; see system design §13 and project plan §7. None of this blocks M2 engineering, all of it must land before real player/Steam traffic exists.

**I1.** As the developer, the client-server connection is encrypted, not plaintext.
Acceptance criteria: TLS (or equivalent) added to the KryoNet TCP/UDP connection; verified auth tokens never cross the network unencrypted.
Priority: P0 (before RC). Size: M. Status: todo.

**I2.** As the developer, DB credentials and server ports are environment-configured, not hardcoded in source.
Acceptance criteria: `DatabaseManager.init()` and `Main.java` read from environment/config rather than literal values (`postgres`/`postgres`, `54555`/`54777`).
Priority: P0 (before RC). Size: S. Status: todo.

**I3.** As the developer, a broken build is caught automatically on push.
Acceptance criteria: minimal GitHub Actions workflow running `gradlew.bat build` (and `test` once a test source set exists).
Priority: P1. Size: S. Status: todo.

---

## Epic J — Future Content / Live-Ops (post-launch, intentionally not detailed yet)

Ideas not yet scoped in detail — clans, seasonal effects on characters, raids, and similar. Per the project plan's live-ops philosophy (§8): ship the core loop first, layer content via post-launch updates based on real player feedback, rather than over-designing these now. Listed here only so they aren't lost, not as ready-to-build stories.

**J1.** Clans — player groups, likely a `clans`/`clan_members` schema addition. No preemptive work needed; nothing in the current design assumes solo-only accounts.
Priority: unscoped. Status: idea.

**J2.** Seasonal effects on characters — time-boxed stat modifiers and/or periodic leaderboard resets. Likely an additive `season_id` on relevant tables; the existing JSONB stat blobs and forward-compatible Jackson config already accommodate this without redesign.
Priority: unscoped. Status: idea.

**J3.** Raids — N-participant PvE content. `BattleSession`'s participant-array design (see B2, system design §7) was chosen specifically so this doesn't require a rewrite of the battle model when it's picked up.
Priority: unscoped. Status: idea.

**J4.** Faction-gated skills (Crimson = crit chance, Skyborn = dodge chance). See `docs/planning/03-lore-and-worldbuilding.md` §6 and system design §14 for the full context — a lore-driven mechanic idea, not yet numerically specced (blocked on the combat-detail pass defining crit/damage formulas first).
Priority: unscoped. Status: idea.

**J5.** Embers as an actual match resource (vs. pure lore flavor for why the Arena/Ember-gathering exists). See lore doc §6. Not decided either way yet.
Priority: unscoped. Status: idea.

**J6.** Faction selection UX — how/when a player picks Crimson Accord vs. Skyborn, and whether it's cosmetic-only or gameplay-binding. Depends on J4 being picked up first. See lore doc §6.
Priority: unscoped. Status: idea.

**J7.** Loadout slot capacity as a progression/monetization lever — start with a medium pouch size (per A7/system design §4.4) and let players unlock additional weapon/skill/passive slots via purchase or achievement rewards. No schema change needed (`Loadout`/`Inventory` are unbounded `Array<T>`); this is a server-side validation rule keyed off an account-level unlock count, whenever it's picked up.
Priority: unscoped. Status: idea.

**J8.** ~~Weapon durability~~ — superseded by Stamina, which is now in scope for A7 (system design §4.2/§4.4) rather than deferred: a shared Stamina pool with a per-weapon `staminaCost` produces the same "this weapon becomes unusable, pouch rotates" outcome as durability would, reusing the existing mana pattern instead of adding new per-item state. Left here only as a record of the earlier idea and why it was resolved differently.
Priority: n/a. Status: superseded by A7.

**J9.** "Break" skill — an active skill that disables a specific opponent weapon (their currently top-priority-and-affordable one) for the rest of the battle. Counterplay layered on top of Stamina-driven rotation (system design §4.4), not a replacement for it. Needs a per-weapon "disabled this battle" check alongside the Stamina-affordability check once picked up — straightforward `Skill` content once A7 lands, not a core-engine change.
Priority: unscoped. Status: idea.

**J10.** "Steal" skill — an active skill that lets the caster temporarily use a specific opponent weapon for the remainder of the battle only (no permanent effect). Same relationship to Stamina/A7 as J9 — additive counterplay, not foundational. Needs two-sided battle-state bookkeeping (defender loses access, attacker gains temporary access), a bit more involved than J9's one-directional disable.
Priority: unscoped. Status: idea.

---

## Epic K — Misc / Polish

A running list of small, one-off changes that extend an already-established pattern in the codebase (a new field alongside existing ones, a UI tweak, a config toggle) rather than introducing new architecture. These skip the full Definition-of-Ready ceremony (no acceptance-criteria essay needed) — they can go straight from a one-line entry here to a direct Claude Code prompt in the same session. Add a line whenever one of these comes up; check it off when done rather than leaving it dangling.

If something that looks small turns out to touch a system still under active design (combat engine, account linking, Steam integration), it belongs in that epic instead, not here — this bucket is only for genuinely isolated, pattern-following changes.

**K1.** Split `volumeMaster` into separate music/SFX volume settings in `AccountSettings`, following the existing `field + withX() + JSONB default + slider in SettingsScreen` pattern (new migration for the JSONB default).
Priority: P2. Size: XS. Status: todo.

**K2.** ~~`MatchmakingRequest`/`MatchmakingFoundResponse` are never registered in `KryoConfig`~~ — **superseded 2026-07-09**, not fixed: both packets are removed entirely by B4's async redesign, along with the `MatchmakingService` code this bug lived in. No action needed beyond making sure B4 doesn't reintroduce it (the new `AttackRequest`/`AttackResponse` must be registered — see system design §5).
Priority: n/a. Status: superseded by B4.

**K3.** ~~`MatchmakingService.dequeue(connection)` exists but nothing calls it~~ — **superseded 2026-07-09**, not fixed: `MatchmakingService` and its queue retire entirely with B4; there is no connection-cleanup problem in a model with no waiting room.
Priority: n/a. Status: superseded by B4.
