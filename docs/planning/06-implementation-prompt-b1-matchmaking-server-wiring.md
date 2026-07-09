# Claude Code prompt — B1 matchmaking + server↔core wiring

Copy everything below the line into Claude Code. Written 2026-07-09 after verifying the M2 combat-core pass (A1–A7/B2/B3) against spec — that work is solid and this builds directly on it. Grounded in the actual current code (`BattleEngine`/`BattleParticipant`/`BattleSession` in `core/combat`, `CombatActionRequestHandler`/`CombatService` in `server`), not just the docs.

---

Wire matchmaking and the server→core dependency so a `CombatActionRequest` can actually run a turn through the already-built `BattleEngine`, instead of stopping at the ownership check it does today.

Read first: `docs/planning/01-system-design-combat-engine.md` §6–§8, `docs/planning/02-user-stories.md` B1/B3/C1, and the current `CombatActionRequestHandler.java`/`CombatService.java` (both explicitly stop short right now — read their doc comments, they say exactly what's deferred to this pass).

## Scope

Implement **B1** (matchmaking) and the server↔core wiring B3's close-out note deferred. Do **not** implement C1–C3 (reward persistence) — that's next after this, and needs its own pass since it touches Gold/Exp/Elo application, which is a different concern from getting a battle running at all.

## 1. `server` → `core` dependency

Add `api project(':core')` to `server/build.gradle`, matching the existing `api project(':common')` line. Note: `core` depends on `vis-ui`/`gdx-freetype` (client-only UI libraries) — this pulls those onto the server's classpath too. That's expected and harmless (nothing server-side calls into UI code), not something to work around; just don't be surprised by it in the dependency tree.

## 2. Elo column (needed for B1's pairing logic)

New migration `V7__Add_Character_Elo.sql` (next after `V6__Add_Character_Stamina.sql`):
```sql
ALTER TABLE characters ADD COLUMN elo INTEGER NOT NULL DEFAULT 1000;
```
**Scoping call, deliberate:** do **not** add `elo` to the shared `Character` record (`common/model`) in this pass — that record is used everywhere (`CharacterMapper`, `CharacterCreationScreen`, `CharacterEntity`, existing tests) and widening its blast radius isn't needed yet. Instead add a narrow `CharacterDao.getElo(long characterId)` (returns `int`), used only by matchmaking's pairing logic. Story C1 (reward persistence) is where `Character.elo` will actually need to become client-visible and get written to — bring it onto the shared record then, not now.

## 3. Matchmaking packets + handler + queue

- `common/network/packet`: `MatchmakingRequest(long characterId)` / `MatchmakingFoundResponse(long battleId, long opponentCharacterId)` — same record + `RecordSerializer` convention as `CombatActionRequest`/`Response`. Register both at the end of `KryoConfig.register()`, after the existing entries (registration order is positional, per `CLAUDE.md` — do not reorder anything already there).
- `MatchmakingRequestHandler implements RequestHandler<MatchmakingRequest>` (server/network/handler): same ownership guardrail shape as `CombatActionRequestHandler` — reject unauthenticated connections and any `characterId` not owned by `connection.account`, before doing anything else.
- A queue-owning service (new `MatchmakingService`, registered in `ServiceRegistry` like the others): an in-memory queue of waiting `(GameConnection, characterId, elo)` entries — no new DB table, the queue itself is transient (system design §7). On a new request: scan the queue for the closest Elo match; pair and remove both if one is found within a range (pick a starting threshold, e.g. ±100, and widen it after a timeout — exact timeout value is your call, note it in a comment rather than agonizing over it, this is explicitly a "define when implementing" item per B1's acceptance criteria). If nothing matches, enqueue and wait.
- On a pair: create a fresh Ashley `Engine`, load both matched characters (full `Character` objects — you'll need `CharacterService`/`CharacterDao` to fetch both, not just IDs), build two `BattleParticipant`s via `BattleParticipant.fromCharacter(engine, character)`, add both to a new `BattleSession(seed)` (pick the seed however you like — a fresh random seed per battle, not a fixed one, this isn't a test), wrap in a `BattleEngine(engine, session)`, and register the whole bundle under a new battle id.

## 4. `BattleSessionRegistry`

New class, mirroring how `ServiceRegistry`/`ScreenRouter` already manage lifecycle/caching elsewhere in this codebase (system design §7) — don't introduce a new pattern for this. Holds a map from `battleId` (generate however's simplest — an `AtomicLong` counter is fine) to whatever bundle a battle needs to resolve later turns (at minimum the `BattleEngine` and a way to map each side's `characterId` back to its `BattleParticipant`, since `CombatActionRequestHandler` will need to find "which side is this request for"). On `BattleEngine.isOver()`, call `BattleSession.end()` and remove the entry from the registry — don't leak finished battles.

On a successful pairing, `MatchmakingService` sends `MatchmakingFoundResponse` to **both** connections (not just the requester) — each gets the *other* character's id as `opponentCharacterId`.

## 5. Wire `CombatActionRequestHandler` to actually resolve a turn

After the existing ownership check passes: look up the battle via `BattleSessionRegistry.get(request.battleId())`. If it doesn't exist (unknown or already-ended battle), reject and log, same style as the ownership rejection. If it exists, call `battleEngine.resolveTurn()` and build a `CombatActionResponse` from the requesting character's own `BattleParticipant.turnResult()`.

**Two things I'm deliberately not deciding here — flag them back to me rather than quietly picking an answer:**
- `CombatActionRequest` already has a nullable `skillId` field from before the pouch/priority-order design existed. Nothing in the resolution algorithm (§4.1–§4.4) lets a player choose an action — it's fully probabilistic based on stats/pouch order. For this pass, **ignore `skillId` entirely** (don't wire it to anything) and treat every request as a pure "advance this battle by one turn" signal. If that field should be removed from the packet entirely, that's a design call, not yours to make silently — flag it.
- `resolveTurn()` resolves **both** combatants' Result Sets in one call (it's not "my turn, then your turn" as two separate requests). That means the response only naturally covers the requester's own side — **how the other player's client learns what happened this turn isn't decided yet** (a second push packet? the other client polls with its own `CombatActionRequest` and gets a cached result for a turn that already happened? something else?). Don't invent a networking pattern to solve this — implement the requester's-own-side response only, and leave a clear TODO/comment plus flag it back explicitly. This is a real open design question, not an oversight to code around.

## Testing

- `MatchmakingService`: pairing within threshold, widening after timeout, rejecting/queuing when no match, cleanup after a match is found (paired connections leave the queue).
- `BattleSessionRegistry`: register → look up → end → look-up-after-end returns nothing.
- An integration-style test (or as close as the existing test setup allows without a live DB/network) exercising: two characters matched → `CombatActionRequest` from one side → response contains a real, non-empty Result Set with `damage` populated — proving the whole chain (matchmaking → session → engine → response) actually connects, not just each piece in isolation.
- Existing B3 ownership-rejection tests/behavior must still hold for both the new `MatchmakingRequestHandler` and the extended `CombatActionRequestHandler`.

## Definition of done

`gradlew.bat build` and `gradlew.bat core:test`/`server:test` pass (create a `server` test source set if one doesn't exist yet — this is the first server-side logic complex enough to need one). Update `docs/planning/02-user-stories.md` status for B1 to `done` with a close-out note in the same style as B2/B3's existing ones. Flag the two open items above explicitly in your final summary, not just in code comments — I need to fold them into the system design doc during close-out per the working agreement in `CLAUDE.md`.
