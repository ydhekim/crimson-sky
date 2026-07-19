package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.QuestClaimResult;

/**
 * Server → client outcome of a {@link ClaimQuestRequest} (system design §19, Epic P). On success,
 * {@code result} reports what the reward left the character holding (gold and consumable counts). On failure
 * ({@code success == false}), {@code message} is the {@code MessageCode} name explaining why
 * ({@code QUEST_NOT_FOUND}, {@code QUEST_NOT_COMPLETE}, {@code QUEST_ALREADY_CLAIMED},
 * {@code QUEST_DAILY_CLAIM_CAP_REACHED}, {@code QUEST_INVALID_REWARD_CHOICE}) and {@code result} is
 * {@code null}.
 */
public record ClaimQuestResponse(boolean success, String message, QuestClaimResult result) {
}
