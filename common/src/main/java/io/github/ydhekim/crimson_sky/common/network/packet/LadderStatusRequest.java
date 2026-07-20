package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Client → server request for a character's live monthly ladder standing (system design §21, Epic R3). The
 * server re-derives standing from {@code battle_history}'s ranked Elo track — the client's word is never
 * trusted. Ownership of {@code characterId} is validated against the connection's account.
 */
public record LadderStatusRequest(long characterId) {
}
