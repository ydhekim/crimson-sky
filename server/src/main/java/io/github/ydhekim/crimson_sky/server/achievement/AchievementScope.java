package io.github.ydhekim.crimson_sky.server.achievement;

/**
 * Whether an achievement is unlocked once for a whole account or independently per character
 * (system design §22). Stored as {@code achievement_definitions.scope} and decides which partial unique
 * index an unlock row targets.
 */
public enum AchievementScope {
    ACCOUNT, CHARACTER
}
