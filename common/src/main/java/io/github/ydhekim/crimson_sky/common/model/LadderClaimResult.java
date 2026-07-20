package io.github.ydhekim.crimson_sky.common.model;

/** The outcome of a successful ladder claim (system design §21) — mirrors QuestClaimResult's shape. */
public record LadderClaimResult(
    String rewardTier,
    long remainingGold,
    int repairTokenCount,
    int petCareKitCount,
    String itemGranted
) {
}
