package io.github.ydhekim.crimson_sky.server.database.entity;

import io.github.ydhekim.crimson_sky.server.achievement.AchievementCriteriaType;
import io.github.ydhekim.crimson_sky.server.achievement.AchievementScope;
import org.jdbi.v3.json.Json;

import java.util.Map;

/**
 * A row of {@code achievement_definitions} in its evaluable form (system design §22, V15) — the shape the
 * unlock engine reads, distinct from the client-facing {@code AccountAchievement} DTO the read endpoint
 * serves. {@code scope}/{@code criteriaType} map their VARCHAR columns to enums by name; {@code criteriaParams}
 * is the JSONB criteria blob, deserialized by the Jackson plugin (numbers arrive as Integer/Long/Double, so
 * {@link io.github.ydhekim.crimson_sky.server.achievement.AchievementEvaluator} reads them via Number).
 */
public record AchievementDefinitionEntity(
    long id,
    String keyName,
    AchievementScope scope,
    AchievementCriteriaType criteriaType,
    @Json Map<String, Object> criteriaParams,
    int xpReward,
    int goldReward,
    String badgeId,
    String titleId,
    int bonusCharacterSlots,
    int bonusDailyBattles,
    int points,
    boolean hidden,
    String category
) {
}
