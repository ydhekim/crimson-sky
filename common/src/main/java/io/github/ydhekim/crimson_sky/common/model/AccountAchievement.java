package io.github.ydhekim.crimson_sky.common.model;

public record AccountAchievement(
    String keyName,
    String titleLocKey,
    String descLocKey,
    int xpReward,
    String iconId,
    boolean isUnlocked,
    String unlockedAt) {
}
