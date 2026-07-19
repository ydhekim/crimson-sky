package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Sent instead of {@link AttackResponse} when an {@link AttackRequest} is rejected before any battle
 * resolves — currently only the daily battle cap (system design §20). {@code reason} is a
 * {@code MessageCode} name, the same convention {@link CreateCharacterResponse#message()} uses.
 */
public record AttackRejectedResponse(String reason) {
}
