-- Quest system (system design §19, Epic P). Two prerequisites for the same slice:
--
-- 1. `battle_history.won` — §19's live-progress query ("Win N battles ...") is
--    `COUNT(*) FROM battle_history WHERE character_id = ? AND won = true AND created_at > periodStart`,
--    but V8 never recorded a win/loss boolean. Win/loss was only reconstructable by comparing `gold_delta`
--    against a currently-true numeric coincidence (a loss is exactly 5 gold, a win >= 25) — exactly the kind
--    of implicit coupling that breaks the day those reward constants are tuned. Record the real value instead.
--    `DEFAULT FALSE` means pre-migration rows read as losses for quest-counting; harmless pre-alpha (no real
--    player history) and self-correcting once new rows carry the real outcome.
--
-- 2. `quest_claims` — progress is computed live from `battle_history`, so the only new state a quest needs is
--    the claim: a completed quest must not be repeatably claimed within the same period. The `UNIQUE` triple
--    enforces "at most one claim per character per quest per period" at the database level (the same taste as
--    `account_identities.user_id UNIQUE`), not left to application logic. The repeatable quest gives each claim
--    its own `period_start` (its own `claimed_at` moment) so those rows never collide; its "at most 3/day" cap
--    is a `claimed_at`-windowed COUNT instead (see `QuestClaimDao`).
ALTER TABLE battle_history
    ADD COLUMN won BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS quest_claims
(
    id           SERIAL PRIMARY KEY,
    character_id INTEGER     NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    quest_id     VARCHAR(64) NOT NULL,
    period_start TIMESTAMP   NOT NULL,
    claimed_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (character_id, quest_id, period_start)
);
