package io.github.ydhekim.crimson_sky.server.combat;

/**
 * What one resolved attack paid its attacker (story C1, system design §8.1) — the deltas actually
 * applied, not the resulting totals. Only the attacker's side is ever rewarded: an async attack is
 * one-sided, so the opponent (real or bot) is never credited or debited.
 *
 * <p>C1 accumulates raw numbers only. {@code expDelta} feeds {@code characters.experience} and nothing
 * else — there is deliberately no level-up threshold or stat-growth consequence anywhere in the
 * codebase, and this record does not introduce one.
 */
public record RewardOutcome(int goldDelta, long expDelta, int eloDelta) {

    /**
     * The payout for a battle whose reward transaction failed. The fight itself already resolved and is
     * still reported to the player — a zero payout is a bug to notice in the logs, not a reason to hide
     * a battle that happened.
     */
    public static RewardOutcome none() {
        return new RewardOutcome(0, 0L, 0);
    }
}
