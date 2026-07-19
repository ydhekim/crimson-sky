package io.github.ydhekim.crimson_sky.common.model;

/**
 * One quest's live status for a character (system design §19, Epic P). Computed on demand — never stored —
 * from {@code battle_history} (the win count) and {@code quest_claims} (the claim state), so it can never
 * drift from reality.
 *
 * <p>Lives in {@code common} rather than as a server-side result record because it rides inside a
 * {@code QuestStatusResponse}'s {@code Array} across the wire, the same way {@code ResolvedAction} rides
 * inside an {@code AttackResponse} — Kryo registration (in {@code common}) can only see {@code common} types.
 *
 * <ul>
 *   <li>{@code currentWins}/{@code targetWins} — progress toward the quest's win requirement this period.</li>
 *   <li>{@code claimable} — the reward can be taken right now ({@code currentWins >= targetWins} and the
 *       period's own not-yet-exhausted check passes).</li>
 *   <li>{@code alreadyClaimed} — the period's claim allowance is spent: this exact period for a daily/weekly
 *       quest, or today's cap reached for the repeatable one.</li>
 *   <li>{@code claimsRemainingToday} — how many more times it can be claimed today: {@code 0} or {@code 1}
 *       for a daily/weekly quest (one per period), counting down from the cap for the repeatable one.</li>
 * </ul>
 */
public record QuestProgress(
    String questId,
    String description,
    int currentWins,
    int targetWins,
    boolean claimable,
    boolean alreadyClaimed,
    int claimsRemainingToday
) {
}
