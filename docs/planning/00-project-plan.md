# Crimson Sky — Project Plan

Last updated: 2026-07-21 (M3.5 progression/economy in progress — Epics L, M, N, O, P, Q, R, S all done; Epic T (final epic of the a–k expansion) spec'd, implementation pending)

## 1. Vision

Crimson Sky is a server-authoritative, deterministic 2D multiplayer combat game (Java 17, LibGDX, Ashley ECS). Its centerpiece is the **Mizan Combat Engine**: a probability-based, turn-based simulation where every turn produces a *Result Set* — a combination of the character's resolved action (weapon / skill / fallback punch) and an independent pet action, driven by symmetric Physical/Magical stats (STR/INT, DEX/WIS, VIT/SPI) plus neutral multipliers (SPD, INS). See `Mizan_Combat_Engine_GDD_v4.pdf` for the authoritative design spec and `docs/planning/01-system-design-combat-engine.md` for how it maps onto this codebase.

The narrative counterpart to that technical spec is `docs/planning/03-lore-and-worldbuilding.md` — moderate-depth worldbuilding (not a campaign) that explains, among other things, why the combat engine is in-fiction called "Mizan" (the Scale) and why the game's two-faction identity (Crimson Accord vs. Skyborn) is baked into the title itself. In-game presentation stays light/punchy (see that doc §5); the mythic register is background canon, not the voice players see turn-to-turn.

## 2. Current status (as of this plan)

Built and working (per `git log` and source tree):

- Auth: login flow, `AccountDao`/`UserDao`, `AccountService`/`UserService`.
- Character management: create / list / delete, `CharacterDao`, `CharacterService`, `CharacterMapper` (record → ECS entity).
- Account settings: load/save flow end-to-end.
- Achievements: backend + `AchievementsScreen`.
- Localization: request/response + `LanguageManager`.
- Networking skeleton: KryoNet client/server, `KryoPacketRouter` + `RequestHandler` (server), `PacketHandlerRegistry` (client) — Strategy-pattern dispatch, all over a single TCP connection.
- Persistence: PostgreSQL via Flyway, migrations V1–V5 (schema, localization, menu labels, achievements, account settings).
- Client shell: `MainMenuScreen`, `CharactersScreen`, `CharacterCreationScreen`, `SettingsScreen`, `AchievementsScreen`, `ConnectionScreen`, routed via `ScreenFactory`/`ScreenRouter`.
- ECS foundation: base components only (`HealthComponent`, `ManaComponent`, `StatsComponent`, `BaseStatsComponent`, `InventoryComponent`, `LoadoutComponent`, `LevelComponent`, `NameComponent`, `IdComponent`) and `CharacterMapper`. **No Ashley `System`/`IteratingSystem` classes exist yet.**
- Data models: `Character`, `Stats`, `Weapon`, `Skill`, `Pet`, `Loadout`, `Inventory` records already shaped to match the GDD's stat mapping.

Not yet built (this is the actual game):

- The Mizan Combat Engine simulation itself (no ECS systems, no cascading decision logic, no Result Set).
- Pet resolution logic (Insight check).
- Matchmaking / opponent pairing.
- Combat networking (no combat packets registered in `KryoConfig` yet).
- Reward persistence (Gold/Exp/Elo) — no `battle_history`-type table yet.
- Combat screen / Result Set animation on the client.
- Loadout equip flow in the UI (the `Loadout` record exists, but no screen manages it yet).
- Data-driven weapon/skill/pet content (currently no seed data or admin tooling).

**Bottom line:** the "meta" layer (accounts, characters, menus, persistence plumbing) is solid. The actual combat game — the reason this project exists — hasn't been started. That's the focus of the roadmap below.

## 3. Roadmap

| Milestone | Goal | Depends on |
|---|---|---|
| M1 — Meta layer (done) | Auth, characters, settings, achievements, localization | — |
| M2 — Combat Engine core (done) | Server-side ECS systems implementing GDD v4 cascading logic + pet check + Result Set, headless-testable | M1 |
| M3 — Matchmaking & persistence (done) | Queue/pairing → async attacks (B4), `BattleSession`, reward persistence (Gold/Exp/Elo, C1–C3) | M2 |
| M3.5 — Progression & economy | Leveling/stat/skill points, skill tree, quests, shop, loadout constraints (weight capacity, durability), ranked ladder, achievements/character page/statistics, account levers (daily battle cap, extensible slots), character-creation customization — design fully locked 2026-07-15 (system design §15–§23, Epics L–T). **In progress:** Epics L (leveling/stat/skill points), M (skill tree), N (loadout constraints) and O (shop/potions/pet health — O1/O2/O3 done, O4 blocked on Epic P) all landed; quests (P), ranked ladder, and the remaining epics still todo. See §2's 2026-07-10 scope note and §8 | M3 |
| M4 — Combat client | `CombatScreen`, Result Set animation/playback, loadout equip UI | M2, deliberately sequenced **after** M3.5 — server/core progression systems are meant to be solid before UI/UX work resumes (2026-07-10 decision) |
| M5 — Content & balance | Data-driven weapons/skills/pets, tuning pass, more than one test character build | M2–M4 |
| M6 — Mobile (iOS/Android) | Wire up the platforms the README already documents but `settings.gradle` doesn't include yet, **plus** cross-platform account linking so progress carries over from the Steam version | M4, and the account-linking schema work in §5 |

Detailed stories for M2–M5 are in `docs/planning/02-user-stories.md`. System design for M2/M3/M3.5 is in `docs/planning/01-system-design-combat-engine.md`.

**2026-07-10 scope decision (M3.5):** a deliberately larger v1.0 than "tight core loop only" — extensible account/character slots and daily battle count (both monetizable levers), full character-creation customization (stats/faction already existed; gender/hair/skin color/type are new), a skill tree (passive and active nodes, gated by level + skill points + gold), a shop, weekly/daily/repeatable quests, weapon durability as a persistent gold-sink layered on top of Stamina (not a replacement — Stamina governs in-battle usability, durability governs cross-battle wear), a total-loadout weight capacity as a hard gate (distinct from §4.3's existing soft per-item `comfortableWeight` penalty), a ranked ladder unlocking at level 25 with monthly rewards, and achievements/badges/character-page/statistics. Confirmed as "all in v1.0" rather than staged, on the reasoning that these together are what make the game attractive at launch without yet building the heavier Epic J ideas (clans/seasons/raids), which remain deferred exactly as §8 describes. All nine slices are now fully designed and locked (system design §15–§23; backlog Epics L, M, N, O, P, Q, R, S, T in `02-user-stories.md`), as of 2026-07-15. **Implementation began Epic-L-first as planned and, as of 2026-07-17, Epics L (leveling/stat/skill points), M (skill tree), N (loadout constraints — weight gate + durability), and O (shop/potions/pet health) are all done.** O4's earning side is the only part of Epic O still open, blocked on the not-yet-built quest epic (P). Quests (P), account levers (Q), ranked ladder (R), and the remaining epics (S–T) are still todo — see `02-user-stories.md` for per-story status.

## 4. Release phases

Milestones are the engineering unit of work; phases are the coarser label for talking about the project's maturity (to yourself, playtesters, or a Steam store page) — standard indie-dev vocabulary, mapped onto the milestones above:

| Phase | Maps to | Exit criteria |
|---|---|---|
| Pre-Alpha | M2 + M3 | Full combat loop works end-to-end with **zero** rendering — proven via unit tests and logs, no screen needed. |
| Alpha | M4 | A human can play a full match start-to-finish and see it happen, even with placeholder (flat-color) visuals. Feature-complete, not content-complete. |
| Beta | M5 | Real assets replace placeholders through the `CombatVisualFactory` seam (see system design doc); weapons/skills/pets are data-driven. Focus shifts from building systems to balancing and bugfixing. |
| Release Candidate | Stabilization pass on top of Beta | No new features — just fixes from playtesting, plus Steam submission logistics (see §5). |
| Post-1.0 | M6 (mobile) + deferred scope | Mobile platforms, cross-platform account linking, and anything explicitly deferred (see §5 and §7). |

## 5. Parallel tracks

Two workstreams run alongside the milestones above rather than gating them — noting them here so they don't get lost in the middle of an engineering sprint.

### 5.1 Steam release (Epic G in the backlog)

- **Foundations already in place, no rework needed:** the auth model already has a `PlatformType.STEAM` value (schema/model anticipated this), and the `lwjgl3` module's default packaging path (`construo`) already builds native distributables for `linuxX64`, `macM1`, `macX64`, and `winX64` — exactly the artifact shape Steam distribution needs. Use the `construo` (bundled-JVM) path for the Steam build, not the opt-in `enableGraalNative` path — a normal bundled JVM is a much safer combination with a JNI-based Steamworks wrapper than GraalVM native-image is.
- **Alpha:** a small de-risking spike — get the Steamworks JNI wrapper initializing and pumping callbacks inside a `construo`-built executable. De-risk only, not full integration.
- **Beta:** implement the `STEAM` branch of the existing login flow (`LoginRequestHandler` currently only handles `PlatformType.TEST` and rejects everything else); mirror achievement unlocks to Steam's stats API (the existing DB-backed achievement system stays the source of truth); get the Steam store page live — wishlists compound over time, and this doesn't consume any engineering time, so it should run as its own lane starting here rather than waiting until the end.
- **Release Candidate:** SteamPipe depot/branch configuration, submit to Valve review with at least ~2 weeks lead time before the target launch date.
- **Explicitly deprioritized:** Steam Cloud saves (the game is already server-authoritative with everything in Postgres, so save-sync is already solved) and Steam Deck/controller support for v1.0 (deferred to a post-1.0 epic if there's demand — would require a gamepad focus-navigation pass across every Scene2D/VisUI screen).

### 5.2 Cross-platform account linking (feeds into M6, Epic H in the backlog)

Once mobile ships, players will want the same progress whether they log in via Steam or via Apple/Google on mobile. The **current schema can't do this** — `accounts.user_id` is a strict one-to-one unique FK to `users`, so a second platform identity today would create a completely separate account with separate characters.

- **Schema fix:** replace the 1:1 relationship with an `account_identities` join table (`account_id`, `user_id` unique, `linked_at`), so one account can have multiple linked identities. Do this migration **before Steam ships**, while there's no production data yet — a clean migration now versus a live-data migration later.
- **Linking flow:** an in-session link code — generate a code while authenticated on device A, redeem it while authenticated on device B. Reuses the existing `RequestHandler`/`ServiceRegistry` pattern; no dependency on Steam/Apple/Google's own account-linking APIs.
- **Merge policy (decided):** block linking if both identities already have separate progress — no true merge, no conflict resolution logic. A player with progress on both platforms picks one to continue on. The full linking UX itself is still scoped to M6; only the schema change is pulled forward.

See `docs/planning/01-system-design-combat-engine.md` §10–11 for the technical detail on both tracks.

## 6. Dev workflow while assets and a live DB aren't ready

- **No assets yet:** M2/M3 (the entire combat engine, matchmaking, persistence) need zero assets — it's all data-in/data-out ECS logic. M4 (combat screen) uses placeholder rendering: Scene2D `Image` actors backed by solid-color textures (not raw `ShapeRenderer` draws) and `BitmapFont`/`Label` for action text, routed through one `CombatVisualFactory` lookup, animated with Scene2D `Action`s. That keeps the later real-asset swap a data change, not a rewrite of `CombatScreen`.
- **No need to stand up Postgres to build the combat engine:** write `ActionResolutionSystem`/`PetResolutionSystem`/`ResultCompilationSystem` logic as plain JUnit tests using in-memory `Character`/`Stats`/`Loadout` fixtures (see story A6) — no `ServerBootstrap`, no `ServiceRegistry`, no live DB involved. Only spin up a real Postgres instance once testing the actual network round-trip end-to-end (Epic B/C).

## 7. Pre-launch hardening checklist

Flagged during a technical design review — not urgent today, but must happen before real player data/Steam traffic exists:

- **Transport encryption:** the KryoNet connection (checked `KryoServer`) is currently plaintext TCP/UDP. Fine for local dev; not acceptable once real Steam/Apple/Google auth tokens cross the open internet. Add TLS or an equivalent encrypted channel before RC.
- **Config extraction:** `DatabaseManager.init()` hardcodes DB credentials (`postgres`/`postgres`) and `Main.java` hardcodes the TCP/UDP ports. Move both to environment-based configuration before going live.
- **New-handler guardrail:** any new request handler that acts on a specific character (starting with the upcoming `CombatActionRequestHandler`) must validate that the requested `characterId` belongs to `connection.account`, following the pattern already established in `CharacterListRequestHandler`/`DeleteCharacterRequestHandler`. Don't trust client-supplied IDs beyond that check.
- **Testing given RNG-based combat:** story A6 (unit tests for combat resolution) is promoted to **P0** — probability-driven logic is exactly the kind of code that breaks silently during a refactor and is hard to catch by playtesting alone. Use the GDD's three worked scenarios as fixed-seed test fixtures. Also make a deliberate choice of RNG implementation (e.g. `SplittableRandom` over plain `Random`, for better statistical distribution while staying seedable) rather than defaulting silently.
- **Lower-priority, worth being aware of:** `DatabaseManager`'s singleton pattern may make future service-layer testing more awkward — not worth refactoring now, revisit if Epic C testing gets painful. No CI currently exists — even a minimal `gradlew build` (and `test`, once they exist) GitHub Action on push would catch a broken merge early, especially useful once a lot of changes are coming through Claude Code.

## 8. Future content & live-ops philosophy

Clans, seasonal effects, raids, and other ideas beyond the core loop are intentionally **not** detailed in the current backlog. Shipping a tight, well-tested core combat loop first and layering content via post-launch updates — based on real player feedback rather than upfront speculation — is the standard, lower-risk approach for a live multiplayer game, not a shortcut. Fully designing clans/seasons/raids before the core loop is even proven to be fun would be higher risk, not lower.

**This still holds — the 2026-07-10 M3.5 expansion (§3) is a different tier of decision, not an exception to this principle.** Progression (leveling, skill tree, quests, shop, ranked ladder, achievements) is being pulled into v1.0 deliberately, as the systems that make the core loop feel like a complete game at launch. Clans/seasons/raids (Epic J) are a step beyond that — content layered *on top of* a proven core loop — and stay exactly as deferred as before.

- These stay as loosely-sketched future epics (see Epic J in the backlog) rather than detailed user stories, since the details will change once there's real player data anyway.
- One architectural decision is worth making now, cheaply, rather than retrofitting later: `BattleSession` (Epic B, M3) models its participants as a list/array of combatants rather than hardcoding exactly two. 1v1 is all that ships at launch, but this leaves raids (N participants) possible later without a rewrite of the battle session model.
- Clans and seasons need no preemptive schema work — the JSONB stat/inventory/loadout columns and the already-forward-compatible Jackson config (`FAIL_ON_UNKNOWN_PROPERTIES = false`) mean additions like a `season_id` column or a `clans` table later are additive migrations, not redesigns.

## 9. Agile process (solo-dev, adapted)

Full Scrum ceremonies don't make sense for a one-person project — this is a lightweight Kanban-with-sprints hybrid, tuned for a Cowork (planning) + Claude Code (execution) split:

- **Sprint length:** 1 week. Long enough to finish 2–4 small/medium stories, short enough that the backlog doesn't go stale.
- **Backlog:** lives in `docs/planning/02-user-stories.md`, one flat list grouped by epic, each story tagged with priority (P0/P1/P2) and size (S/M/L). No separate ticketing tool needed while solo; if this grows a team later, migrate stories into GitHub Issues at that point (repo already has a `github` remote: `ydhekim/crimson-sky`).
- **Definition of Ready:** a story is ready to hand to Claude Code when it has acceptance criteria and, if it touches the combat engine, a pointer to the relevant section of the system design doc.
- **Definition of Done:** code compiles (`gradlew.bat build`), the story's acceptance criteria are manually verified (or unit-tested where the logic is deterministic, e.g. combat resolution), `docs/planning/02-user-stories.md` status is updated, and `CLAUDE.md` is updated if the change introduces a new convention.
- **Weekly loop:**
  1. **Planning (Cowork):** pick the next 2–4 stories from the top of the backlog, confirm they're Ready, break down any that are too big.
  2. **Build (Claude Code):** implement one story at a time in the project folder, following `CLAUDE.md` conventions (ECS discipline, manual DI, fixed-timestep, packet registration order, etc.).
  3. **Review (you):** playtest / read the diff.
  4. **Close-out (Cowork):** mark stories done in the backlog, update system design doc if the implementation diverged from the plan, restock the backlog if it's getting thin.
- **Working agreement between the two tools:** Cowork owns `docs/planning/*.md` and `CLAUDE.md` structure; Claude Code owns the actual Java. If Claude Code makes an architectural decision while implementing (e.g. how RNG seeding works), that decision gets reflected back into the system design doc during close-out so the two stay in sync.
