package io.github.ydhekim.crimson_sky.common.model;

/**
 * The shared leveling formula (system design §15): {@code expNeededForLevel(L) = 8×L² − 8}, anchored so
 * level 1 needs 0 cumulative exp. Extracted from {@code RewardService} (server-only, package-private)
 * so the client can compute XP-progress display without duplicating — and risking drift from — the
 * same formula the server uses to decide actual level-ups.
 */
public final class LevelCurve {
    private LevelCurve() {}

    public static final int LEVEL_CAP = 50;

    public static long expNeededForLevel(int level) {
        return 8L * level * level - 8L;
    }
}
