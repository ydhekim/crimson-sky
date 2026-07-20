package io.github.ydhekim.crimson_sky.common.model;

import java.util.List;

/**
 * The read-only aggregate a character page shows (system design §22, Epic S3): the character itself, its live
 * combat statistics, every achievement with its unlock status, the summed achievement score, and the currently
 * equipped title. {@code achievementScore} is the sum of {@code points} across every unlocked entry in
 * {@code achievements}. {@code equippedTitle} is {@code null} when nothing is equipped.
 */
public record CharacterPage(Character character, CharacterStatistics statistics,
                            List<CharacterPageAchievement> achievements, int achievementScore,
                            String equippedTitle) {
}
