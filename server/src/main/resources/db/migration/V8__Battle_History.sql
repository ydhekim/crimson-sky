-- Reward persistence for a resolved attack (system design §8/§8.1, story C1). One row per attack, from
-- the attacker's side only: an async attack is one-sided, so the opponent (real or bot) is never
-- credited or debited and never gets a row of its own.
--
-- `opponent_character_id` is nullable because a bot opponent has no row in `characters` at all
-- (system design §7). `opponent_is_bot` is recorded server-side for analytics/bot-calibration tuning
-- and is never serialized to the client — a bot must stay indistinguishable from a real opponent.
--
-- The gold wallet lives on `accounts.global_currency` (V1), not on `characters`, so applying a reward
-- touches both tables; the deltas are mirrored here so a battle's payout can be audited without
-- reconstructing it from the two running totals.
CREATE TABLE IF NOT EXISTS battle_history
(
    id                    SERIAL PRIMARY KEY,
    character_id          INTEGER NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    opponent_character_id INTEGER REFERENCES characters (id) ON DELETE CASCADE, -- NULL when opponent_is_bot
    opponent_is_bot       BOOLEAN NOT NULL DEFAULT FALSE,
    gold_delta            INTEGER NOT NULL,
    experience_delta      BIGINT  NOT NULL,
    elo_delta             INTEGER NOT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
