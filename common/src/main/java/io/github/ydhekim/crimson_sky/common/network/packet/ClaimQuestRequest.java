package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Client → server request to claim a completed quest's reward (system design §19, Epic P). The server
 * re-validates completion live against {@code battle_history} and the claim guard against
 * {@code quest_claims} — it never trusts a client-reported "I finished this".
 *
 * <p>{@code rewardChoice} is meaningful only for the weekly quest, whose reward is a player's choice between
 * a Repair Token and a Pet Care Kit (the {@code ShopService} consumable keys); it is {@code null}/ignored for
 * the daily and repeatable quests, which have no choice to make.
 */
public record ClaimQuestRequest(long characterId, String questId, String rewardChoice) {
}
