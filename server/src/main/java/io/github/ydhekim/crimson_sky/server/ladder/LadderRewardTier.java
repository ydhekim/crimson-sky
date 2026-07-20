package io.github.ydhekim.crimson_sky.server.ladder;

/** Monthly ladder reward tiers by rank, first-pass numbers (system design §21). */
public enum LadderRewardTier {
    TOP_1(1, 1),
    TOP_2_10(2, 10),
    TOP_11_100(11, 100);

    private final int minRank;
    private final int maxRank;

    LadderRewardTier(int minRank, int maxRank) {
        this.minRank = minRank;
        this.maxRank = maxRank;
    }

    /** The tier {@code rank} falls into, or {@code null} below rank 100 (no reward, per §21). */
    public static LadderRewardTier forRank(int rank) {
        for (LadderRewardTier tier : values()) {
            if (rank >= tier.minRank && rank <= tier.maxRank) {
                return tier;
            }
        }
        return null;
    }
}
