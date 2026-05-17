package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.AccountAchievement;

import java.util.List;

public record AchievementListResponse(
    boolean success,
    String message,
    List<AccountAchievement> achievements
) {
}
