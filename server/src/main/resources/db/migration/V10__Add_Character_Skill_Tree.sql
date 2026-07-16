-- Skill tree spend state (system design §16): a node-id → current-rank map per character, stored as a
-- JSONB blob like stats/inventory/loadout. Defaults to '{}' — a fresh character has learned nothing.
ALTER TABLE characters ADD COLUMN skill_tree JSONB NOT NULL DEFAULT '{}'::jsonb;
