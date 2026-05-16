CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE IF NOT EXISTS localization_keys
(
    id         SERIAL PRIMARY KEY,
    key_name   VARCHAR(100) UNIQUE NOT NULL,
    group_type VARCHAR(20)
    );

CREATE TABLE IF NOT EXISTS localization_values
(
    id         SERIAL PRIMARY KEY,
    key_id     INTEGER REFERENCES localization_keys (id) ON DELETE CASCADE,
    lang_code  VARCHAR(10) NOT NULL,
    text_value TEXT        NOT NULL,
    UNIQUE (key_id, lang_code)
    );

CREATE TABLE IF NOT EXISTS users
(
    id             SERIAL PRIMARY KEY,
    platform_type  VARCHAR(20)  NOT NULL,
    identity_token VARCHAR(100) NOT NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (platform_type, identity_token)
    );

CREATE TABLE IF NOT EXISTS accounts
(
    id              SERIAL PRIMARY KEY,
    user_id         INTEGER UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    max_slots       INTEGER   DEFAULT 3,
    global_currency BIGINT    DEFAULT 0,
    settings        JSONB     DEFAULT '{
      "volume_master": 0.5,
      "language": "en_US",
      "fullscreen": true
    }'::jsonb,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS achievement_definitions
(
    id            SERIAL PRIMARY KEY,
    key_name      VARCHAR(50) UNIQUE NOT NULL,
    title_loc_key INTEGER REFERENCES localization_keys (id),
    desc_loc_key  INTEGER REFERENCES localization_keys (id),
    xp_reward     INTEGER DEFAULT 0,
    icon_id       VARCHAR(50)
    );

CREATE TABLE IF NOT EXISTS account_achievements
(
    id             SERIAL PRIMARY KEY,
    account_id     INTEGER REFERENCES accounts (id) ON DELETE CASCADE,
    achievement_id INTEGER REFERENCES achievement_definitions (id),
    progress_data  JSONB     DEFAULT '{}'::jsonb,
    unlocked_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (account_id, achievement_id)
    );

CREATE TABLE IF NOT EXISTS characters
(
    id             SERIAL PRIMARY KEY,
    account_id     INTEGER REFERENCES accounts (id) ON DELETE CASCADE,
    name           CITEXT UNIQUE NOT NULL,
    faction        VARCHAR(20)   NOT NULL CHECK (faction IN ('A', 'B')),
    level          INTEGER                DEFAULT 1,
    experience     BIGINT                 DEFAULT 0,

    max_hp         INTEGER                DEFAULT 100,
    max_mp         INTEGER                DEFAULT 100,
    base_def       INTEGER                DEFAULT 10,
    base_atk       INTEGER                DEFAULT 10,

    stats          JSONB         NOT NULL DEFAULT '{
      "strength": 5,
      "intelligence": 5,
      "wisdom": 5,
      "dexterity": 5,
      "vitality": 5,
      "spirit": 5,
      "speed": 5,
      "insight": 5
    }'::jsonb,
    inventory      JSONB                  DEFAULT '{
      "weapons": [],
      "skills": [],
      "pets": []
    }'::jsonb,
    loadout JSONB                  DEFAULT '{
      "weapons": [],
      "skills": [],
      "pets": []
    }'::jsonb,

    created_at     TIMESTAMP              DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP              DEFAULT CURRENT_TIMESTAMP
    );
