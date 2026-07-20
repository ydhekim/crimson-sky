package io.github.ydhekim.crimson_sky.common.model;

/**
 * A character's live ladder standing plus last month's claimable reward (system design §21). Computed on
 * demand, never stored — the same "compute live from battle_history" rule §19/§20 already established.
 *
 * <ul>
 *   <li>{@code currentRankedElo}/{@code currentRank} — this instant's standing, informational only; it can
 *       still move before the month ends and is never what a claim pays against.</li>
 *   <li>{@code lastMonthRank}/{@code rewardTier} — the frozen standing as of the end of the most recently
 *       completed month, and the tier (if any) it falls into. {@code rewardTier} is {@code null} below
 *       rank 100.</li>
 *   <li>{@code claimable}/{@code alreadyClaimed} — whether last month's reward can be taken right now.</li>
 * </ul>
 */
public record LadderStatus(
    boolean rankedEligible,
    int currentRankedElo,
    int currentRank,
    int lastMonthRank,
    String rewardTier,
    boolean claimable,
    boolean alreadyClaimed
) {
}
