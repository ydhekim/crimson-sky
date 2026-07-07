# User Stories / Backlog

Last updated: 2026-07-06 (revised same day: added Epics G–J for Steam, account linking, security/ops, and future live-ops content; promoted A6; updated B2 and D1)

Priority: **P0** blocks the milestone, **P1** important but not blocking, **P2** nice-to-have / can slip.
Size: **XS** (minutes, extends an existing pattern — see Epic K), **S** (~1 session), **M** (~2-3 sessions), **L** (multi-session, consider splitting further before starting).
Status: `todo` / `in-progress` / `done`. Update this file as part of each sprint close-out (see `00-project-plan.md` §9).

---

## Epic A — Combat Simulation Core (server, headless) — M2

**A1.** As the server, I resolve a character's turn action using the GDD cascade (Weapon Draw roll vs STR → Skill Cast roll vs WIS + mana check → fallback Punch), so combat outcomes follow the Mizan rules.
Acceptance criteria: given fixed stats/loadout/RNG seed, `ActionResolutionSystem` deterministically produces the same `ResolvedAction` as hand-computed from the GDD scenarios (see GDD §5 for the three worked examples to use as test fixtures).
Priority: P0. Size: M. Status: done. *(Core resolution cascade + `ActionResolutionSystem` implemented and unit-tested against the three GDD scenarios. Server-side invocation — a handler/service running the engine tick — is still unwired because `server` does not yet depend on `core`; that closes with §6/B3, see system design §4.)*

**A2.** As the server, I run an independent Insight check each turn to decide whether the pet acts, appended to the Result Set regardless of the character's own action outcome.
Acceptance criteria: pet action frequency scales with `Tameness` + `insight` per GDD §2/§3 Step 2; pet check runs even when the character action was `Burned` (GDD Scenario 3).
Priority: P0. Size: M. Status: todo.

**A3.** As the server, I compile the character action + pet action into an ordered Result Set array matching the GDD's `[3x Hammer, 2x Wolf]` shape.
Acceptance criteria: `ResultCompilationSystem` output serializes cleanly into `CombatActionResponse.actions()`.
Priority: P0. Size: S. Status: todo.

**A4.** As a developer, each battle uses a seeded RNG so I can reproduce and unit test any outcome.
Acceptance criteria: seed stored on `BattleSession`; same seed + same inputs → same Result Set across runs.
Priority: P1. Size: S. Status: done. *(Minimal `BattleSession` holds the seed + a `SplittableRandom`; `DeterminismTest` proves same-seed reproducibility through both the pure resolver and the ECS system.)*

**A5.** As the server, I track ephemeral Battle State (weapon depletion, spent mana, pet-used-this-turn) that resets when a battle ends and never touches the database.
Acceptance criteria: `BattleStateComponent` exists only on entities inside an active `BattleSession`; nothing in it is written to Postgres (GDD §4).
Priority: P0. Size: M. Status: todo.

**A6.** As a developer, I have a `core` test source set so combat resolution logic can be unit tested headlessly.
Acceptance criteria: `gradlew.bat core:test` runs; at least the three GDD §5 scenarios are encoded as tests, using fixed seeds against `SplittableRandom` (see system design §4/§9).
Priority: **P0** (promoted from P1 — probability-driven logic breaks silently during refactors and is hard to catch by playtesting alone; this is cheap insurance). Size: S. Status: done. *(JUnit 5 test source set added to `core`; the three §5 scenarios (seed 42) plus punch-fallback and mana-boundary edge cases are asserted. Tests cover the character-action column only — pet actions await A2.)*

---

## Epic B — Matchmaking & Sessions — M3

**B1.** As a player, I can queue for a match and get paired with an opponent in a similar Elo range.
Acceptance criteria: `MatchmakingRequest`/`MatchmakingFoundResponse` round-trip; pairing prefers closest Elo available, falls back to widening range after a timeout (define timeout value when implementing).
Priority: P0. Size: M. Status: todo.

**B2.** As the server, I create and track an authoritative `BattleSession` bridging connected clients once matched.
Acceptance criteria: session holds participants as `Array<BattleParticipant>` (not hardcoded `characterA`/`characterB` fields — see system design §7, this leaves raids possible later without a rewrite), a `SplittableRandom` seed, and per-participant `BattleStateComponent`; cleaned up when the battle ends. Only two participants are ever populated at launch.
Priority: P0. Size: L. Status: todo. *(Consider splitting into "create session" and "end/cleanup session" before starting.)*

**B3.** As the server, I validate that combat-related requests (starting with `CombatActionRequest`) reference a `characterId` owned by `connection.account`, matching the ownership-check pattern already used in `CharacterListRequestHandler`/`DeleteCharacterRequestHandler`.
Acceptance criteria: a request referencing a character not owned by the connection's account is rejected, not silently processed.
Priority: P0. Size: S. Status: todo.

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
Acceptance criteria: new loadout-management screen reading/writing `Loadout`; the record already exists, only the UI is missing.
Priority: P0. Size: M. Status: todo.

---

## Epic E — Content & Data — M5

**E1.** As a designer, weapons/skills/pets are data-driven (DB rows or JSON, not hardcoded Java) so balance can be tuned without a redeploy.
Priority: P1. Size: M. Status: todo.

**E2.** As a player, there's more than one viable build (at least one Physical-path and one Magical-path test character) to validate the symmetric stat design actually feels balanced.
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

---

## Epic K — Misc / Polish

A running list of small, one-off changes that extend an already-established pattern in the codebase (a new field alongside existing ones, a UI tweak, a config toggle) rather than introducing new architecture. These skip the full Definition-of-Ready ceremony (no acceptance-criteria essay needed) — they can go straight from a one-line entry here to a direct Claude Code prompt in the same session. Add a line whenever one of these comes up; check it off when done rather than leaving it dangling.

If something that looks small turns out to touch a system still under active design (combat engine, account linking, Steam integration), it belongs in that epic instead, not here — this bucket is only for genuinely isolated, pattern-following changes.

**K1.** Split `volumeMaster` into separate music/SFX volume settings in `AccountSettings`, following the existing `field + withX() + JSONB default + slider in SettingsScreen` pattern (new migration for the JSONB default).
Priority: P2. Size: XS. Status: todo.
