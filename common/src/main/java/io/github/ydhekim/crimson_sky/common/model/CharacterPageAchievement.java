package io.github.ydhekim.crimson_sky.common.model;

/**
 * One achievement as a character's page shows it (system design §22). Same shape as {@link AccountAchievement}
 * plus the S1 columns ({@code points}/{@code badgeId}/{@code hidden}/{@code category}) minus
 * {@code xpReward}/{@code iconId} (not needed for this view). {@code hidden} is carried as data only — nothing
 * server-side masks a hidden-and-locked achievement's title/desc based on it (no established "???" convention
 * yet; that's M4 work). {@code isUnlocked} follows the same account-vs-character OR-by-scope rule the unlock
 * engine writes against. {@code unlockedAt} is {@code null} for a still-locked achievement.
 */
public record CharacterPageAchievement(String keyName, String titleLocKey, String descLocKey, int points,
                                       String badgeId, boolean hidden, String category,
                                       boolean isUnlocked, String unlockedAt) {
}
