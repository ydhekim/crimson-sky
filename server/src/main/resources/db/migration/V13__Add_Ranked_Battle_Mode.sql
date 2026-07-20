ALTER TABLE battle_history ADD COLUMN battle_mode VARCHAR(16) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE battle_history ADD COLUMN ranked_elo_delta INTEGER;
