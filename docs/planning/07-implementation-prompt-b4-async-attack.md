# Claude Code prompt — B4: async attack (replaces live matchmaking queue)

Copy everything below the line into Claude Code. Written 2026-07-09, replacing the B1 live-queue design that pass actually implemented — this isn't a bugfix on top of that work, it's a different shape for the same problem. Read `docs/planning/01-system-design-combat-engine.md` §5–§8 first (all rewritten this session) for the full rationale; this prompt is the scoped work order.

---

Replace the live matchmaking queue with an async "attack a persisted opponent" flow: one request, one response, the whole battle resolved synchronously — no waiting room, no multi-request session, no live opposing client.

Read first: `docs/planning/01-system-design-combat-engine.md` §5–§8 (rewritten this session — packet shapes, opponent-selection algorithm, bot-generation approach, the `battle_history` schema note). Also read the current `MatchmakingService.java`, `BattleSessionRegistry.java`/`ActiveBattle.java`, `CombatActionRequestHandler.java`/`CombatService.java` — you're removing/replacing these, not extending them, so know exactly what's there before you start.

## Scope

Implement **B4**. Do not implement **C1–C3** (reward/battle_history persistence) — that's next, and this pass should leave a clean seam for it (see "Handoff to C1" below) without doing its job.

## 1. Remove

- `common/network/packet`: `CombatActionRequest`, `CombatActionResponse`, `MatchmakingRequest`, `MatchmakingFoundResponse` — delete, and remove their `KryoConfig` registrations.
- `server/service`: `MatchmakingService`.
- `server/combat`: `BattleSessionRegistry`, `ActiveBattle`.
- `server/network/handler`: `MatchmakingRequestHandler`, and rewrite (not just rename) `CombatActionRequestHandler`.
- Delete their tests too (`MatchmakingServiceTest`, `MatchmakingRequestHandlerTest`, `BattleSessionRegistryTest`, `CombatActionRequestHandlerTest`) rather than leaving them failing against deleted classes — B4 needs new tests anyway, described below.
- **Keep untouched:** `BattleEngine`, `BattleParticipant`, `BattleSession`, `ActionResolver`, `DamageCalculator`, `PetResolver`, `ResultCompiler` (`core/combat`) and all the ECS components under `core/ecs/component`. None of this cares how it's invoked.

## 2. New packets

```java
public record AttackRequest(long characterId) {}
public record AttackResponse(long battleId, String opponentDisplayName, boolean won,
                             Array<Array<ResolvedAction>> turns) {}
```
Register both at the end of `KryoConfig.register()`. `ResolvedAction` already has its `damage` field from the M2 pass — no change needed there. `opponentDisplayName` is a `String`, never a `characterId` — this is load-bearing for the "never disclose a bot" requirement below, not just a display choice.

## 3. `AttackService` (new, replaces `CombatService`+`MatchmakingService`)

One method, roughly: `AttackResult attack(long characterId)`. Steps:

1. **Opponent selection.** Query for a persisted character within ±100 Elo of `characterId`'s own Elo (reuse `CharacterDao.getElo`), excluding itself, and pick **randomly** among candidates (not closest — closest-every-time makes outcomes too predictable). If none found, widen to unbounded Elo and try again — no waiting, no timeout, resolve within this one call. If still nothing, synthesize a bot (next section).
2. **Battle setup.** Fresh Ashley `Engine`, a `BattleSession` with a freshly-random (not fixed) seed, two `BattleParticipant`s via the existing `fromCharacter` factory, a `BattleEngine` over both — all exactly as `MatchmakingService.startBattle` used to do, just not registered anywhere afterward.
3. **Resolve the whole battle and keep every turn.** `BattleEngine.runToCompletion()` exists today but only leaves the *final* turn's `TurnResultComponent` behind (each `resolveTurn()` call clears and overwrites it) — it will not give you the full log `AttackResponse.turns` needs. Pick one:
   - **(a)** Don't call `runToCompletion()`; call `resolveTurn()` yourself in a loop (respecting `BattleEngine.TURN_CAP`), copying each acting participant's `TurnResultComponent.actions` out (defensive copy, same reasoning the old `CombatService.resultSetOf` used) immediately after each call, before the next call overwrites it. Stop when `isOver()`.
   - **(b)** Add an accumulating per-participant turn history directly to `BattleEngine` (e.g. an `Array<Array<ResolvedAction>>` appended to inside `resolveTurn()`/`writeTurnResult`), so `runToCompletion()` can hand back the full log itself.

   (a) touches less already-tested code; (b) gives a cleaner call site. Your call — either is fine, just don't silently drop turns.
4. **Build the response** from the requesting character's own side: `won` = did the requester's `BattleParticipant` end up as `engine.winner()`; `turns` = that side's per-turn Result Sets, collected per step 3. The opponent's own turn log is not sent — there's no opposing client to send it to.
5. **Return an internal result, not just the packet** — see "Handoff to C1" below.

## 4. Bot generation

When no real persisted opponent qualifies at all: build a synthetic `Character` (never persisted to the DB) using `04-starter-content.md`'s weapons/skills/pets, assembled into one of a handful of hardcoded archetype templates (e.g. a STR/Warhammer-leaning "tank," an INT/Meteor-leaning "nuker," a SPD/dodge-leaning build) chosen at random per fight, with total stat points scaled to roughly match the requesting character's own Elo (a simple linear or step function from Elo → stat budget is fine for a first pass — this is exactly the kind of thing that needs a tuning pass once it's live, same caveat as every other number in this doc). Generate a display name for it (not the real-player-facing exposure concern — this is what fills `opponentDisplayName`).

**Non-negotiable:** nothing about the bot should be distinguishable from the client's side — no field, no timing difference, no naming convention a curious player could reverse-engineer. Track "was this a bot" only in the internal result object from step 3.5 above, never in anything serialized to the client.

## 5. `AttackRequestHandler`

Ownership check only (character belongs to `connection.account`) — exactly `CombatActionRequestHandler`'s old first guardrail, reused as-is. There is no second "is this character a participant in battleId" check anymore — `AttackRequest` carries no battle/opponent id the client could misuse, the server always picks the opponent itself.

## 6. Handoff to C1 (don't implement, but don't block it either)

C1 (reward persistence — Gold/Exp/Elo application, `battle_history` row) needs to know, per attack: who fought, who won, whether the opponent was a bot, and (eventually) the Elo/Gold/Exp deltas to apply. Design `AttackService`'s return type as an internal result object carrying all of that (`characterId`, `won`, `opponentIsBot`, the resolved `turns`) — not just the `AttackResponse` network packet, which deliberately omits the bot flag. That way C1 can wrap a call to this service with persistence logic without needing to change this pass's code. Don't build the `battle_history` table or apply any Elo/Gold/Exp delta yourself — that's explicitly C1's job (system design §8's `V8__Battle_History.sql`, including the `opponent_is_bot` and nullable `opponent_character_id` columns already speced there).

## Testing

- Opponent selection: finds a candidate within range; widens when none in range; falls back to a bot when the table is empty (or nothing at all qualifies); never selects the requester itself.
- Bot generation: produces a usable `Character` (valid stats, a non-empty loadout) at a few different Elo inputs — doesn't need to assert exact numbers, just that nothing crashes and the archetype/stat-budget scaling moves in the right direction as Elo increases.
- Full-battle resolution: an attack against a fixed-seed opponent produces a multi-turn `turns` array (length > 1 for at least one test case) with real `damage` values, not just a single final turn.
- Ownership rejection still holds (reuse the existing test pattern/fixtures where they still apply).
- Confirm by inspection (or a serialization round-trip test if convenient) that nothing in `AttackResponse`'s wire format can reveal `opponentIsBot`.

## Definition of done

`gradlew.bat build` and `gradlew.bat server:test`/`core:test` pass. Update `docs/planning/02-user-stories.md` — B4 to `done` with a close-out note in the existing style; confirm B1/B2/B3's "superseded" annotations and K2/K3 still read correctly against what actually landed (adjust wording if reality diverged from the plan, per the working agreement in `CLAUDE.md`).
