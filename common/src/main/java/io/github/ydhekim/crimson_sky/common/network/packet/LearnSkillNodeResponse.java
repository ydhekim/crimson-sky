package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Skill;

/**
 * Server → client outcome of a {@link LearnSkillNodeRequest} (system design §16). On success,
 * {@code node} is the granted {@link Skill} at its new rank (its {@code passiveMagnitude} already
 * rank-scaled), {@code newRank} the resulting rank, and {@code remainingSkillPoints}/
 * {@code remainingGold} the balances after the spend. On failure ({@code success == false}),
 * {@code message} carries the {@code MessageCode} name explaining why, {@code node} is {@code null},
 * and the numeric fields are {@code 0}.
 */
public record LearnSkillNodeResponse(boolean success, String message, Skill node, int newRank,
                                     int remainingSkillPoints, long remainingGold) {
}
