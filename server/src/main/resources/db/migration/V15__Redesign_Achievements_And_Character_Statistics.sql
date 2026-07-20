-- S1/S2 (system design §22): turn the V4 achievement scaffold into an evaluable content model, and give
-- battle_history the one new fact the FASTEST_WIN_TURNS criterion needs to be computed at all.

-- FASTEST_WIN_TURNS' own input. Existing rows default to 0; the fastest-win query excludes turn_count = 0
-- precisely so those pre-column rows never masquerade as an unbeatable 0-turn win.
ALTER TABLE battle_history ADD COLUMN turn_count INTEGER NOT NULL DEFAULT 0;

-- The declarative criteria vocabulary (§22). Every column carries a default so the 10 V4 rows remain valid
-- the instant this runs; the content UPDATE below then replaces the placeholder defaults with real values.
ALTER TABLE achievement_definitions ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'ACCOUNT';
ALTER TABLE achievement_definitions ADD COLUMN category VARCHAR(30);
ALTER TABLE achievement_definitions ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE achievement_definitions ADD COLUMN points INTEGER NOT NULL DEFAULT 10;
ALTER TABLE achievement_definitions ADD COLUMN criteria_type VARCHAR(30) NOT NULL DEFAULT 'ACCOUNT_CREATED_BEFORE';
ALTER TABLE achievement_definitions ADD COLUMN criteria_params JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE achievement_definitions ADD COLUMN gold_reward INTEGER NOT NULL DEFAULT 0;
ALTER TABLE achievement_definitions ADD COLUMN badge_id VARCHAR(50);
ALTER TABLE achievement_definitions ADD COLUMN title_id VARCHAR(50);
ALTER TABLE achievement_definitions ADD COLUMN bonus_character_slots INTEGER NOT NULL DEFAULT 0;
ALTER TABLE achievement_definitions ADD COLUMN bonus_daily_battles INTEGER NOT NULL DEFAULT 0;

-- V1's account_achievements becomes achievement_unlocks: unlocks are now scoped either to the whole account
-- (character_id IS NULL) or to a single character (character_id IS NOT NULL). progress_data is gone — a v1.0
-- criterion is either satisfied or not, evaluated live, with no partial-progress blob to persist.
ALTER TABLE account_achievements RENAME TO achievement_unlocks;
ALTER TABLE achievement_unlocks DROP COLUMN progress_data;
ALTER TABLE achievement_unlocks ADD COLUMN character_id INTEGER REFERENCES characters (id) ON DELETE CASCADE;

-- V1's inline UNIQUE (account_id, achievement_id) auto-named {table}_{col1}_{col2}_key by Postgres. It can't
-- express the two-scope rule, so it's replaced by two partial unique indexes: one unlock per account for an
-- account-scope achievement, one per character for a character-scope one.
ALTER TABLE achievement_unlocks DROP CONSTRAINT account_achievements_account_id_achievement_id_key;
CREATE UNIQUE INDEX achv_unlock_account_uq ON achievement_unlocks (account_id, achievement_id) WHERE character_id IS NULL;
CREATE UNIQUE INDEX achv_unlock_character_uq ON achievement_unlocks (account_id, achievement_id, character_id) WHERE character_id IS NOT NULL;

-- First-pass content: assign real scope/criteria/points to the 10 V4-seeded rows so none is stuck on the
-- migration defaults. UNBROKEN and TWO_SHADOWS map to combat milestones (their flavor — near-death recovery,
-- pet-bonding — has no matching v1.0 criteria type); see the S1/S2 prompt's fit notes.
UPDATE achievement_definitions SET scope = 'ACCOUNT', criteria_type = 'ACCOUNT_CREATED_BEFORE',
    criteria_params = '{"date":"2026-12-31"}'::jsonb, points = 10, category = 'ONBOARDING', hidden = FALSE
    WHERE key_name = 'PIONEER_OF_CRIMSON_SKY';
UPDATE achievement_definitions SET scope = 'ACCOUNT', criteria_type = 'ACCOUNT_CREATED_BEFORE',
    criteria_params = '{"date":"2026-08-01"}'::jsonb, points = 25, category = 'ONBOARDING', hidden = FALSE
    WHERE key_name = 'DAY_ONE_SURVIVOR';
UPDATE achievement_definitions SET scope = 'CHARACTER', criteria_type = 'CHARACTER_LEVEL',
    criteria_params = '{"threshold":10}'::jsonb, points = 25, category = 'PROGRESSION', hidden = FALSE
    WHERE key_name = 'A_NEW_LEGEND_RISES';
UPDATE achievement_definitions SET scope = 'CHARACTER', criteria_type = 'TOTAL_WINS',
    criteria_params = '{"threshold":1}'::jsonb, points = 10, category = 'COMBAT', hidden = FALSE
    WHERE key_name = 'FIRST_BLOOD';
UPDATE achievement_definitions SET scope = 'CHARACTER', criteria_type = 'WIN_STREAK',
    criteria_params = '{"threshold":3}'::jsonb, points = 30, category = 'COMBAT', hidden = FALSE
    WHERE key_name = 'UNBROKEN';
UPDATE achievement_definitions SET scope = 'CHARACTER', criteria_type = 'ITEM_ACQUIRED',
    criteria_params = '{"rarity":"COMMON"}'::jsonb, points = 10, category = 'COLLECTION', hidden = FALSE
    WHERE key_name = 'THE_FIRST_CRY_OF_STEEL';
UPDATE achievement_definitions SET scope = 'CHARACTER', criteria_type = 'CHARACTER_LEVEL',
    criteria_params = '{"threshold":5}'::jsonb, points = 15, category = 'PROGRESSION', hidden = FALSE
    WHERE key_name = 'THE_FIRST_WHISPER_OF_SKIES';
UPDATE achievement_definitions SET scope = 'CHARACTER', criteria_type = 'TOTAL_WINS',
    criteria_params = '{"threshold":10}'::jsonb, points = 40, category = 'COMBAT', hidden = TRUE
    WHERE key_name = 'TWO_SHADOWS_IN_THE_VOID';
UPDATE achievement_definitions SET scope = 'CHARACTER', criteria_type = 'WIN_STREAK',
    criteria_params = '{"threshold":5}'::jsonb, points = 50, category = 'COMBAT', hidden = FALSE
    WHERE key_name = 'THE_PERFECT_STORM';
UPDATE achievement_definitions SET scope = 'CHARACTER', criteria_type = 'FASTEST_WIN_TURNS',
    criteria_params = '{"maxTurns":3}'::jsonb, points = 75, category = 'COMBAT', hidden = TRUE
    WHERE key_name = 'GHOST_OF_THE_SKIES';
