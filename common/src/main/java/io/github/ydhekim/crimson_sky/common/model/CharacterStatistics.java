package io.github.ydhekim.crimson_sky.common.model;

import java.util.List;

/**
 * A character's aggregate combat statistics for its page (system design §22). {@code fastestWinTurns} is
 * {@code null} when the character has never won (same nullability as {@code AchievementEvaluator}'s own fact
 * of the same name). {@code winPercentage} is {@code 0.0} when {@code totalWins + totalLosses == 0} — never
 * a divide-by-zero.
 */
public record CharacterStatistics(int totalWins, int totalLosses, double winPercentage,
                                  int currentWinStreak, Integer fastestWinTurns, List<RecentMatch> recentMatches) {
}
