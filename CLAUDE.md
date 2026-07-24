# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Planning & roadmap

Before starting a story, check `docs/planning/`:

- `00-project-plan.md` — build status, milestone roadmap, release phases (Pre-Alpha → Post-1.0), Steam/account-linking tracks, sprint/working agreement, pre-launch hardening checklist.
- `01-system-design-combat-engine.md` — how the Mizan Combat Engine (GDD) maps onto ECS components/systems/packets; also covers the M4 placeholder-rendering approach, M6 cross-platform account linking schema, and Steam/security technical notes. Includes an open TCP-vs-UDP decision that should be resolved before combat networking is built.
- `02-user-stories.md` — the backlog, grouped by epic (A–K: combat core, matchmaking, persistence, combat UI, content, platforms, Steam release, account linking, security/ops hardening, future live-ops ideas, misc/polish). Pick up work from here; update a story's status when it's done. Epic K (misc/polish) is for small changes that extend an existing pattern — those can skip straight to a direct prompt without the full backlog ceremony.

As of this writing, the "meta" layer (auth, characters, achievements, settings, localization) is built; the actual combat simulation (ECS systems, matchmaking, reward persistence, combat UI) has not been started yet — that's Milestones M2–M5. Two non-negotiable guardrails carried forward from planning, worth knowing even without opening the docs: (1) any new handler that acts on a specific character/battle must validate ownership against `connection.account`, matching the pattern already in `CharacterListRequestHandler`/`DeleteCharacterRequestHandler` — never trust a client-supplied ID beyond that check; (2) the KryoNet connection is currently plaintext and DB credentials/ports are hardcoded — both must move to encrypted transport / environment config before real player traffic (Epic I), not before.

## Project

Crimson Sky is a server-authoritative, deterministic 2D multiplayer combat game built with Java 17, LibGDX, and Ashley ECS. It's a [gdx-liftoff](https://github.com/libgdx/gdx-liftoff)-generated multi-module Gradle project.

## Commands

Use `gradlew.bat` on Windows (or `./gradlew` in Bash). Module-specific tasks take a `moduleName:` prefix.

- `gradlew.bat lwjgl3:run` — run the desktop client
- `gradlew.bat server:run` — run the game server (headless, needs a local PostgreSQL instance — see Database below)
- `gradlew.bat build` — build sources and archives of every module
- `gradlew.bat clean` — remove all `build/` folders
- `gradlew.bat test` — run unit tests. The `core` module has a JUnit 5 test source set (`core/src/test/java`, combat resolution); other modules have none yet. Use `gradlew.bat core:test` to run just the combat tests.
- `gradlew.bat lwjgl3:jar` — build a runnable jar (output in `lwjgl3/build/libs`)
- `--continue` to keep running after a task fails, `--offline` to use cached dependencies only

Only `common`, `core`, `server`, and `lwjgl3` are wired up in `settings.gradle` — the README mentions `android`/`ios` platforms but they don't exist in this repo yet.

## Architecture

### Module boundaries

- **common** — Shared, platform-agnostic code used by both client and server: `model/` (Java records like `Character`, `Stats`, `Loadout`, `Inventory`) and `network/packet/` (Java record request/response DTOs). Also owns `KryoConfig`, which is the single source of truth for Kryo serializer registration.
- **core** — LibGDX client application logic: screens (Scene2D/VisUI), the Ashley ECS layer, the KryoNet client, asset loading. Platform-agnostic (no LWJGL-specific code) so it could back Android/iOS later.
- **server** — Standalone headless LibGDX application (`HeadlessApplication`, no rendering) that owns auth, matchmaking-adjacent networking, and persistence: KryoNet server, JDBI DAOs, PostgreSQL via Flyway migrations.
- **lwjgl3** — Desktop launcher only; wires `core` into an LWJGL3 window (`Lwjgl3Launcher`) and builds native distributables via the `construo` plugin.

### Networking: packet flow end-to-end

Client and server share packet definitions from `common/network/packet` and both call `KryoConfig.register(kryo)` — **registration order must stay identical on both sides** since Kryo IDs are positional. Adding a new packet type means: add the record to `common/network/packet`, register it (client and server share the same `KryoConfig`), then wire both ends:

- **Client → Server**: `GameClient`/`KryoClient` (core) sends a packet → server's `KryoServer`/`GameConnection` receives it → `KryoPacketRouter.route()` (server) dispatches by packet class to a `RequestHandler<T>` (Strategy pattern, `server/network/handler/*`) → handler calls into a `*Service` (server/service) → DAO (JDBI, server/database/dao) → Postgres.
- **Server → Client**: handler sends a response packet back over the `GameConnection` → client's `NetworkListener` receives it → `PacketHandlerRegistry` (core/network) dispatches by packet class to a registered `Consumer`, posted onto the GDX render thread via `Gdx.app.postRunnable` → the relevant `Screen`/`NetworkListener` implementation updates UI state.

To add a new request/response pair: create the two packet records + register in `KryoConfig`, add a `RequestHandler` implementation and wire it into `KryoPacketRouter`/`KryoPacketRouterFactory` (server), and register a handler `Consumer` in `PacketHandlerRegistry` (client).

Server composition is factory-driven (`NetworkServerFactory`, `PacketRouterFactory`) and assembled once in `ServerBootstrap`, which also owns the shutdown hook (stops the `GameServer`, then closes `DatabaseManager`). `ServiceRegistry` is the manual DI container binding each `*Service` to its on-demand JDBI DAO.

### Database

`DatabaseManager` (server) is a singleton that configures HikariCP, runs Flyway migrations from `server/src/main/resources/db/migration` (`V{n}__Description.sql`), then builds a `Jdbi` instance with `SqlObjectPlugin` + `Jackson2Plugin` + `PostgresPlugin` and snake_case column mapping. The JDBC URL/credentials are currently hardcoded in `DatabaseManager.init()` (expects a local `crimsonsky` Postgres database) — do not add new config plumbing for this without being asked, but do flag it if asked to touch this code. New schema changes go in a new `V{n+1}__...sql` migration file; never edit an already-applied migration.

### ECS (Ashley)

Simulation state lives strictly in ECS components (`core/ecs/component/*`, plain data bags — `HealthComponent`, `StatsComponent`, `InventoryComponent`, etc.). `CharacterMapper` bridges the `common` model records (`Character`, etc.) into ECS entities — this is the only place that should translate between DTO/record land and ECS land. Don't put LibGDX rendering types (`Texture`, `Sprite`) into simulation components; those belong in client-only visual components.

Ashley **systems** live in `core/ecs/system/*` (`ActionResolutionSystem` is the first — the combat cascade), accessing components via `ComponentMapper.getFor(...)`. Combat is discrete/turn-based: a battle ticks `engine.update(...)` once **per turn**, not per render frame, and simulation logic never reads render delta. Keep the probability/decision logic itself in a pure, Ashley-free class (e.g. `core/combat/ActionResolver`) that the system delegates to, so it stays headless-unit-testable — every roll draws from the battle's seeded `SplittableRandom` on `BattleSession` (`core/combat`) for reproducibility. See `docs/planning/01-system-design-combat-engine.md` §4/§4.1 for the concrete roll/frequency rules.

### Screens & UI (client)

Screens are created through `ScreenFactory` (switch over `ScreenType`) and navigated/cached through `ScreenRouter` (`navigateTo` reuses a cached `Screen` instance per type, `clearScreen` disposes and evicts one). Both are constructed once in `CrimsonSky.create()` and handed to screens via constructor injection — there's no service locator/singleton for these. UI is built with VisUI/Scene2D `Table` layouts against VisUI's bundled default skin; the custom Turkish-capable font is registered onto `VisUI.getSkin()` as `default-font` in `CrimsonSky.initializeUI()` before any screen loads (no `uiskin.json` or texture atlases are shipped — see the asset-policy convention below).

### Conventions to follow (already established in this codebase)

- Manual dependency injection everywhere (constructor injection); no reflection-based DI framework.
- Networking packets and shared models are Java `record`s, serialized with Kryo's `RecordSerializer`.
- Prefer `com.badlogic.gdx.utils` collections (`Array`, `ObjectMap`) over `java.util` in LibGDX-facing code (core/server-shared code); plain `java.util` is fine in server-only DB/service code.
- Fixed-timestep simulation on the server; render-thread delta time is only for interpolation/UI, never simulation logic. The render loop drains accumulated real time in fixed steps — `while (accumulator >= FIXED_STEP) { engine.update(FIXED_STEP); }` — so the simulation advances deterministically regardless of frame rate.
- New packet/handler pairs follow the existing Strategy pattern (`RequestHandler<T>` server-side, `Consumer<T>` registration client-side) rather than growing an if/else chain.
- The client font is a FreeType (`.ttf`) font generated at load with an explicit Turkish character set (`AssetLoader.TURKISH_CHARS`) layered on `FreeTypeFontGenerator.DEFAULT_CHARS`, so glyphs like ğ/ş/ı/İ/ö/ü/ç render correctly.
- No real art/audio assets exist in the repo yet: all placeholder rendering is code-generated (solid-color `Pixmap`-backed textures via `TextureFactory`, plus VisUI's own bundled skin styles) rather than shipped image/atlas files — the sole exception is one Turkish-capable font, `assets/fonts/Quicksand-Regular.ttf`. Backgrounds, texture atlases, and `uiskin.json` were removed in the M4 foundation cleanup, so don't go looking for them.
- A new `UI_*` localization key (a plain label/button string, as opposed to a `MessageCode`) must be seeded into `localization_keys`/`localization_values` (see any `V2`/`V20`-shaped migration) in the *same* change that introduces its `getLanguageManager().get("KEY")` call site — never landed as a follow-up. Unlike `MessageCode` values, which `MessageCodeLocalizationCoverageTest` checks automatically at build time, plain `UI_*` keys have no equivalent coverage test (a deliberate choice — see `docs/planning/30-implementation-prompt-missing-ui-localization.md` — replicating that test would need a cross-module dependency this project doesn't otherwise have), so an unseeded key fails silently at runtime as a literal `!KEY!` string on screen, not at build time. Eight keys already slipped through this way once (`UI_BTN_RETRY`, `UI_BTN_SAVE`, `UI_LBL_ACHIEVEMENTS`, `UI_LBL_FULLSCREEN`, `UI_LBL_LANGUAGE`, `UI_LBL_SETTINGS`, `UI_LBL_VOLUME`, `UI_MSG_NO_ACHIEVEMENTS`) before anyone looked closely enough at a real running screen to notice.
