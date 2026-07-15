package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Stats;

/**
 * Server → client outcome of an {@link AllocateStatPointsRequest} (Epic L / system design §15). On
 * success, {@code newStats} is the character's merged stat block and {@code unspentStatPoints} the
 * balance left after the spend. On failure ({@code success == false}), {@code message} carries the
 * {@code MessageCode} name explaining why — {@code STAT_POINTS_INSUFFICIENT} or {@code STAT_CAP_EXCEEDED}
 * — and {@code newStats} is {@code null} with a {@code 0} balance.
 */
public record AllocateStatPointsResponse(boolean success, String message, Stats newStats, int unspentStatPoints) {
}
