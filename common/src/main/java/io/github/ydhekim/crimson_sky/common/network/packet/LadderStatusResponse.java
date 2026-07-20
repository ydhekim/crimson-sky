package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.LadderStatus;

/** Server → client reply to a {@link LadderStatusRequest} (system design §21, Epic R3). */
public record LadderStatusResponse(boolean success, String message, LadderStatus status) {
}
