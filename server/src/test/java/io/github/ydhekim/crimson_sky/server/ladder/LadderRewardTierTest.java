package io.github.ydhekim.crimson_sky.server.ladder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The pure rank → tier mapping (system design §21). No DB, no Ashley — just the tier boundaries, checked at
 * each edge so a fencepost slip (say, rank 100 falling out of the reward band, or rank 11 into the top-10
 * one) is caught.
 */
class LadderRewardTierTest {

    @Test
    void rankOneIsTop1() {
        assertEquals(LadderRewardTier.TOP_1, LadderRewardTier.forRank(1));
    }

    @Test
    void ranksTwoThroughTenAreTop2To10() {
        assertEquals(LadderRewardTier.TOP_2_10, LadderRewardTier.forRank(2), "the low edge of the tier");
        assertEquals(LadderRewardTier.TOP_2_10, LadderRewardTier.forRank(10), "the high edge of the tier");
    }

    @Test
    void ranksElevenThroughOneHundredAreTop11To100() {
        assertEquals(LadderRewardTier.TOP_11_100, LadderRewardTier.forRank(11), "the low edge of the tier");
        assertEquals(LadderRewardTier.TOP_11_100, LadderRewardTier.forRank(100), "rank 100 still earns a reward");
    }

    @Test
    void rankBelowOneHundredIsNoReward() {
        assertNull(LadderRewardTier.forRank(101), "rank 101 is the first with no reward (§21)");
        assertNull(LadderRewardTier.forRank(5000));
    }
}
