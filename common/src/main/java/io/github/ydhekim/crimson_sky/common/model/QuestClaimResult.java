package io.github.ydhekim.crimson_sky.common.model;

/**
 * The outcome of a successful quest claim (system design §19, Epic P): what the reward left the character
 * holding. Rides inside a {@code ClaimQuestResponse}, so — like {@link QuestProgress} — it lives in
 * {@code common} to be serializable by the shared Kryo config.
 *
 * <p>All three consumable counts and the gold balance are reported on every claim regardless of which quest
 * paid, so the client never has to infer an unchanged balance: a daily claim bumps {@code scrollCount}, a
 * weekly claim bumps whichever token was chosen, a repeatable claim bumps {@code remainingGold}; the other
 * fields simply carry the character's current holdings.
 */
public record QuestClaimResult(
    String questId,
    long remainingGold,
    int scrollCount,
    int repairTokenCount,
    int petCareKitCount
) {
}
