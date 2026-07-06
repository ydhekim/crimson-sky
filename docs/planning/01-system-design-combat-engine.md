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

| Component | Fields | Notes |
|---|---|---|
| `WeaponSlotComponent` | `Weapon equipped` | Bridges `Loadout.weapons()` selection into ECS |
| `SkillSlotComponent` | `Skill equipped` | Bridges `Loadout.skills()` selection |
| `PetSlotComponent` | `Pet equipped`, `int currentHealth` | Pet battle-scoped HP separate from the immutable `Pet` record |
| `BattleStateComponent` | `boolean weaponDepleted`, `int spentMana`, `boolean petUsedThisTurn` | GDD §4 "Battle State (Memory)" — volatile, reset every battle. Lives only on entities inside an active `BattleSession`; never persisted. |
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

## 5. New common records (`common/model` + `common/network/packet`)

```java
public record ResolvedAction(ActionSource source, String label, int frequency, boolean failed) {}
public enum ActionSource { WEAPON, SKILL, PUNCH, PET }

public record CombatActionRequest(long battleId, long characterId, Long skillId /* nullable */) {}
public record CombatActionResponse(long battleId, long turnNumber, Array<ResolvedAction> actions) {}
```

These follow the existing record + `RecordSerializer` convention. **Register them at the end of `KryoConfig.register()`**, after the existing entries — registration order is positional and must stay identical on both sides, per the existing convention documented in `CLAUDE.md`. Do not reorder existing registrations.

## 6. Networking / handler wiring (M2/M3)

Follows the exact existing pattern (`CLAUDE.md` §"Networking: packet flow end-to-end") — no new pattern introduced:

- Client: `GameClient` sends `CombatActionRequest` → server `KryoPacketRouter.route()` dispatches to a new `CombatActionRequestHandler implements RequestHandler<CombatActionRequest>` (server/network/handler) → calls a new `CombatService` (server/service, registered in `ServiceRegistry` like the others) → `CombatService` runs the Ashley engine tick for that battle → returns `CombatActionResponse`.
- Client: response arrives via `NetworkListener` → `PacketHandlerRegistry` dispatches by class → `Consumer` posts onto the render thread and hands the `Array<ResolvedAction>` to whatever the M4 `CombatScreen` needs.

**Security guardrail (non-negotiable, checked against existing code):** `CharacterListRequestHandler` and `DeleteCharacterRequestHandler` already validate `connection.account != null` and scope every DB call by `connection.account.id()` — never trusting a client-supplied account ID. `CombatActionRequestHandler` must follow the identical pattern: validate that `CombatActionRequest.characterId()` actually belongs to `connection.account` before running any combat resolution. Don't trust the client-supplied `characterId`/`battleId` beyond that ownership check.

## 7. Matchmaking (M3)

Minimal viable version, same architectural style as everything else:

- `MatchmakingRequest` / `MatchmakingFoundResponse` packets (common), TCP.
- Server-side in-memory queue (no new DB table needed for the queue itself — it's transient), matched by Elo range (Elo lives with the account/character, see §8).
- On match, server creates a `BattleSession` and returns `MatchmakingFoundResponse` to both clients.
- `BattleSession` lifecycle owned by a new `BattleSessionRegistry`, mirroring how `ServiceRegistry` and `ScreenRouter` already manage lifecycle/caching elsewhere in this codebase — keeps the manual-DI style consistent rather than introducing a new pattern.

**Forward-compatible design decision:** model `BattleSession` participants as `Array<BattleParticipant>` (each holding a character entity reference + its `BattleStateComponent`), not two hardcoded fields (`characterA`/`characterB`). Only 1v1 ships at launch — the matchmaking queue and `TurnOrderSystem` only ever populate two entries for now — but raids and other N-participant content are on the long-term idea list (see project plan §8), and retrofitting a hardcoded two-player battle model later is a much bigger rewrite than starting with a collection that happens to contain two entries today. This costs nothing extra now.

## 8. Persistence additions (M3)

New Flyway migration, next in sequence after V5. Checked against the actual `V1__Initial_Schema.sql`: the table is `characters` (plural), PKs are `SERIAL`/`INTEGER` (not `BIGSERIAL`/`BIGINT`), and there is no `elo` column anywhere yet — it needs to be added, most likely on `accounts` or `characters` alongside `experience`. Starting point:

```sql
-- V6__Battle_History.sql
ALTER TABLE characters ADD COLUMN elo INTEGER NOT NULL DEFAULT 1000;

CREATE TABLE IF NOT EXISTS battle_history (
    id SERIAL PRIMARY KEY,
    character_id INTEGER NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    opponent_character_id INTEGER NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
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
