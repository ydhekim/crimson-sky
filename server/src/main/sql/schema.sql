-- schema.sql

-- Drop tables if they exist (for development/reset)
-- DROP TABLE IF EXISTS characters;
-- DROP TABLE IF EXISTS users;

-- Users table for authentication
CREATE TABLE IF NOT EXISTS users
(
    id
    SERIAL
    PRIMARY
    KEY,
    username
    VARCHAR
(
    50
) UNIQUE NOT NULL,
    password_hash VARCHAR
(
    255
) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                             );

-- Characters table for storing character data
CREATE TABLE IF NOT EXISTS characters
(
    id
    SERIAL
    PRIMARY
    KEY,
    user_id
    INTEGER
    REFERENCES
    users
(
    id
) ON DELETE CASCADE,
    name VARCHAR
(
    50
) UNIQUE NOT NULL,
    level INTEGER NOT NULL DEFAULT 1,
    experience BIGINT NOT NULL DEFAULT 0,
    max_health INTEGER NOT NULL,
    max_mana INTEGER NOT NULL,
    base_defence INTEGER NOT NULL,
    base_attack_power INTEGER NOT NULL,

    -- Stats
    strength INTEGER NOT NULL,
    dexterity INTEGER NOT NULL,
    vitality INTEGER NOT NULL,
    intelligence INTEGER NOT NULL,
    wisdom INTEGER NOT NULL,
    spirit INTEGER NOT NULL,
    speed INTEGER NOT NULL,
    insight INTEGER NOT NULL,

    created_at TIMESTAMP
  WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
      );

-- Create index for faster lookups by user_id
CREATE INDEX IF NOT EXISTS idx_characters_user_id ON characters(user_id);
