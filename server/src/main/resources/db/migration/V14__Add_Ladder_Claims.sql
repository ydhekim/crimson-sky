-- Monthly ranked ladder claims (system design §21, Epic R3). Ladder standing is computed live off
-- battle_history (ranked Elo, §21) exactly like quest progress; the only new state a claim needs is the
-- ledger row that stops the same month's tier reward being claimed twice. Same UNIQUE-at-the-database-level
-- taste as quest_claims (V11).
CREATE TABLE IF NOT EXISTS ladder_claims (
    id SERIAL PRIMARY KEY,
    character_id INTEGER NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    period_start TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (character_id, period_start)
);
