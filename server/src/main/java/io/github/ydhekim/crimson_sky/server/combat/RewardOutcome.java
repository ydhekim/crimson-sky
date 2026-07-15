package io.github.ydhekim.crimson_sky.server.combat;

/**
 * What one resolved attack paid its attacker (story C1 + Epic L / system design §8.1, §15) — the deltas
 * actually applied, not the resulting totals. Only the attacker's side is ever rewarded: an async attack
 * is one-sided, so the opponent (real or bot) is never credited or debited.
 *
 * <p>The currency deltas ({@code goldDelta}/{@code expDelta}/{@code eloDelta}) are C1. Epic L adds the
 * progression fields: {@code skillPointsGained} (per battle, win or lose), {@code levelsGained} and
 * {@code statPointsGained} (non-zero only when this battle's exp crossed one or more level thresholds),
 * and {@code bonusRewardGranted} — the name of an item granted by the every-10-level milestone roll, or
 * {@code null} when no milestone was crossed or its roll missed.
 */
public record RewardOutcome(
    int goldDelta, long expDelta, int eloDelta,
    int skillPointsGained,
    int levelsGained, int statPointsGained,
    String bonusRewardGranted) {

    /**
     * The payout for a battle whose reward transaction failed. The fight itself already resolved and is
     * still reported to the player — a zero payout is a bug to notice in the logs, not a reason to hide
     * a battle that happened.
     */
    public static RewardOutcome none() {
        return new RewardOutcome(0, 0L, 0, 0, 0, 0, null);
    }
}
