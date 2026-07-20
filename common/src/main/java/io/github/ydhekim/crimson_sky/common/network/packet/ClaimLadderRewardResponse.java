package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.LadderClaimResult;

/** Server → client reply to a {@link ClaimLadderRewardRequest} (system design §21, Epic R3). */
public record ClaimLadderRewardResponse(boolean success, String message, LadderClaimResult result) {
}
