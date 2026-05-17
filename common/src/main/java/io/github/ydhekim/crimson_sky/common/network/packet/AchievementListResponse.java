package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.AccountAchievement;

import java.util.List;

public class AchievementListResponse {
    public boolean success;
    public String message;
    public List<AccountAchievement> achievements;

    public AchievementListResponse() {
    }

    public AchievementListResponse(boolean success, String message, List<AccountAchievement> achievements) {
        this.success = success;
        this.message = message;
        this.achievements = achievements;
    }
}
