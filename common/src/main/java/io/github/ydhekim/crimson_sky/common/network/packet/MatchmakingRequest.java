package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Client → server request to enter the matchmaking queue with one of the account's characters
 * (system design §7, story B1). The server validates that {@code characterId} belongs to the
 * connection's account before queueing it — the same ownership guardrail
 * {@code CombatActionRequest} is subject to (§6/§13).
 */
public record MatchmakingRequest(
    long characterId
) {
}
