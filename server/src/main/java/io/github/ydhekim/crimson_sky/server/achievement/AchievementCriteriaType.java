package io.github.ydhekim.crimson_sky.server.achievement;

/**
 * The six v1.0 unlock conditions (system design §22). Each names the shape of
 * {@code achievement_definitions.criteria_params} it reads and which fact set
 * ({@link CharacterAchievementFacts}/{@link AccountAchievementFacts}) can satisfy it —
 * {@link AchievementEvaluator} is the single place that mapping lives.
 */
public enum AchievementCriteriaType {
    TOTAL_WINS,
    WIN_STREAK,
    FASTEST_WIN_TURNS,
    CHARACTER_LEVEL,
    ITEM_ACQUIRED,
    ACCOUNT_CREATED_BEFORE
}
