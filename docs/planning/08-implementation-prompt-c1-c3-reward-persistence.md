# Claude Code prompt — C1–C3: reward persistence (Elo/Gold/Exp + battle_history)

Copy everything below the line into Claude Code. Written 2026-07-10, building directly on the B4 async-attack pass (`AttackService`/`AttackResult`/`AttackRequestHandler`), which was deliberately built with a seam for this story and verified working.

---

Wrap the existing `AttackService.attack()` flow with reward persistence: write a `battle_history` row and atomically update the winner's/loser's Gold, Exp, and Elo, without touching how a battle itself is resolved.

Read first: `docs/planning/01-system-design-combat-engine.md` §8 and the new §8.1 (reward formulas, decided 2026-07-10) — both rewritten/added this session. Also read the current `AttackService.java`, `AttackResult.java`, `AttackRequestHandler.java` (server), and `AttackResponse.java`/`AttackRequest.java` (common) — you're wrapping these, not modifying their core logic.

## Scope

Implement **C1**, **C2**, and **C3**. Do not touch matchmaking/opponent-selection logic, `BattleEngine`/combat resolution, or add any leveling mechanic — `Character.level`/`experience` currently have no level-up threshold or consequence anywhere in the codebase, and that stays true after this pass. C1 accumulates raw numbers only.

## 1. Migration — `V8__Battle_History.sql`

Exact schema from system design §8 (already checked against `V1__Initial_Schema.sql`'s actual conventions — `SERIAL`/`INTEGER` PKs, not `BIGSERIAL`):

```sql
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

`accounts.global_currency` already exists (`V1__Initial_Schema.sql`, `BIGINT DEFAULT 0`) — no migration needed for that column.

## 2. Reward formulas (system design §8.1 — implement exactly, don't re-derive)

```
expectedScore = 1 / (1 + 10^((opponentElo - myElo) / 400))
actualScore = won ? 1 : 0
eloDelta = round(32 * (actualScore - expectedScore))

On a win:
  goldDelta = 25 + max(0, round((opponentElo - myElo) * 0.1))
  expDelta  = 50 + max(0, round((opponentElo - myElo) * 0.2))

On a loss:
  goldDelta = 5
  expDelta  = 10
```

`opponentElo` is the real opponent's current Elo when `opponentIsBot` is false. When `opponentIsBot` is true, treat `opponentElo` as equal to the attacker's own Elo (consistent with `BotFactory` already calibrating a bot's stat budget to the attacker's Elo) — this gives `expectedScore ≈ 0.5` and needs no separate bot-Elo tracking. Only the attacker's own Gold/Exp/Elo are ever updated — the opponent side (real or bot) is never written to, win or lose, since this is an async one-sided attack, not a symmetric matchmaking session.

## 3. New DAO methods (atomic increments, not read-then-write)

`CharacterDao` — add:
```java
@SqlUpdate("UPDATE characters SET experience = experience + :expDelta, elo = elo + :eloDelta WHERE id = :characterId")
void addExperienceAndElo(@Bind("characterId") long characterId, @Bind("expDelta") long expDelta, @Bind("eloDelta") int eloDelta);
```

`AccountDao` — add:
```java
@SqlUpdate("UPDATE accounts SET global_currency = global_currency + :goldDelta WHERE id = :accountId")
void addGlobalCurrency(@Bind("accountId") long accountId, @Bind("goldDelta") int goldDelta);
```

New `BattleHistoryDao` (new file, same package/conventions as the other DAOs):
```java
@SqlUpdate("INSERT INTO battle_history (character_id, opponent_character_id, opponent_is_bot, gold_delta, experience_delta, elo_delta) " +
    "VALUES (:characterId, :opponentCharacterId, :opponentIsBot, :goldDelta, :expDelta, :eloDelta)")
void insert(@Bind("characterId") long characterId, @Bind("opponentCharacterId") Long opponentCharacterId,
            @Bind("opponentIsBot") boolean opponentIsBot, @Bind("goldDelta") int goldDelta,
            @Bind("expDelta") long expDelta, @Bind("eloDelta") int eloDelta);
```

## 4. `RewardService` (new)

**Important JDBI correctness point:** `CharacterService`/`AccountService` wrap `onDemand()`-proxied DAOs, where each method call opens its own connection — using two `onDemand` DAOs sequentially does **not** give you one atomic transaction across `characters` and `accounts`. `RewardService` must take the raw `Jdbi` instance (from `DatabaseManager.getInstance().getJdbi()`, wired via `ServiceRegistry` same as everywhere else) and open one `jdbi.useTransaction(handle -> { ... })` block, attaching fresh `CharacterDao`/`AccountDao`/`BattleHistoryDao` instances via `handle.attach(...)` *inside* that block. All three writes (character update, account update, battle_history insert) happen through those handle-attached DAOs so they commit or roll back together.

Shape:
```java
public record RewardOutcome(int goldDelta, long expDelta, int eloDelta) {}

public class RewardService {
    public RewardService(Jdbi jdbi, CharacterService characterService) { ... }

    /** Computes and atomically applies rewards for the attacker's side of an already-resolved attack. */
    public RewardOutcome applyRewards(AttackResult result) { ... }
}
```

`applyRewards`:
1. Load the attacker's `Character` via `characterService.getCharacter(result.characterId())` — needed for `accountId()` (Gold lives on `accounts`, not `characters`).
2. Load the attacker's current Elo via `characterService.getElo(result.characterId())`.
3. Resolve `opponentElo`: attacker's own Elo if `result.opponentIsBot()`, otherwise look up the opponent's Elo (`characterService.getElo(result.opponentCharacterId())` — it's a real persisted character at this point, this should succeed).
4. Compute `eloDelta`/`goldDelta`/`expDelta` per the formulas above.
5. Open one `jdbi.useTransaction(...)`, write the `characters` update, the `accounts` update, and the `battle_history` insert through handle-attached DAOs.
6. Return the computed `RewardOutcome`.

**Failure handling:** the battle has already resolved successfully by the time this runs — if the reward transaction throws, log the error clearly (character id, battle id) and return a zero `RewardOutcome` rather than propagating the exception up into dropping the whole attack response. The player should still see their battle result even if reward persistence hiccups; a silent zero-reward battle is a bug to notice in logs, not a reason to hide the fight that already happened.

## 5. Wire it into the response

`AttackResponse` (common) needs the three deltas added so the client can actually show them — right now nothing in the wire format carries rewards at all, which is a real gap, not an oversight to leave in place:

```java
public record AttackResponse(long battleId, String opponentDisplayName, boolean won,
                              Array<Array<ResolvedAction>> turns,
                              int goldDelta, long expDelta, int eloDelta) {}
```

Update `AttackResult.toResponse()` to take the `RewardOutcome` and fold its fields in:
```java
public AttackResponse toResponse(RewardOutcome outcome) {
    return new AttackResponse(battleId, opponentDisplayName, won, turns,
        outcome.goldDelta(), outcome.expDelta(), outcome.eloDelta());
}
```
No `KryoConfig` change needed — `AttackResponse` is already registered; `RecordSerializer` picks up the new components automatically. Update `AttackRequestHandler` to call `rewardService.applyRewards(result)` after `attackService.attack(...)` succeeds, then `result.get().toResponse(outcome)`. Wire `RewardService` into `ServiceRegistry` the same way every other service is constructed there.

## 6. C2 — item-loss-not-persisted regression test

There is currently no skill or mechanic in the game that actually causes item loss in battle (Break/Steal are deferred, per Epic J) — so this can't be tested end-to-end against a real item-loss scenario yet. Don't invent one. Instead, write a regression test that locks in the property C2 actually cares about: running a full `AttackService.attack()` call never issues any write to a character's `inventory` column. Concretely — capture the attacker's `inventory` JSON before the call and assert it's byte-for-byte identical after, alongside asserting `RewardService` only ever touches `experience`/`elo`/`global_currency`/`battle_history`. This is genuinely testable today and is the correct scope for C2 until an item-loss skill exists to test against directly.

## Testing

- `RewardService`: win against a same-Elo opponent produces the expected flat-base deltas; win against a higher-Elo opponent produces a larger Elo/Gold/Exp gain; loss produces the flat 5/10 consolation Gold/Exp and a negative Elo delta; a bot fight's Elo delta matches the "opponent Elo == attacker's own Elo" rule (~50/50 expected score).
- Transaction atomicity: simulate a failure partway through (e.g. a DAO throwing) and confirm neither the `characters` nor `accounts` row changed — nothing partially committed.
- `battle_history` row shape: `opponent_character_id` is `NULL` for a bot fight and populated for a real one; `opponent_is_bot` matches.
- C2's regression test described above.
- Existing B4 tests (`AttackServiceTest`, `AttackRequestHandlerTest`, `BotFactoryTest`) must still pass unmodified — this pass wraps `AttackService`, it doesn't change it.

## Definition of done

`gradlew.bat build` and `gradlew.bat server:test`/`core:test` pass. Update `docs/planning/02-user-stories.md` — C1, C2, C3 all to `done` with close-out notes in the existing style. If anything about the Elo/Gold/Exp formula felt wrong once real numbers were flowing through a test (e.g. deltas feel too large/small), flag it back rather than silently adjusting the constants — they're first-pass values explicitly meant for a future tuning pass, not a decision to relitigate quietly mid-implementation.
