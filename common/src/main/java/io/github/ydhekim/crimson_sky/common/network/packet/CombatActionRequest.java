package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Client → server request to resolve one combat turn for the requester's character in a battle
 * (system design §5/§6). {@code skillId} is nullable (a client hint at which skill to prefer; the
 * server remains authoritative). The server must validate that {@code characterId} belongs to the
 * connection's account before doing anything with it (B3 ownership guardrail, §6).
 */
public record CombatActionRequest(
    long battleId,
    long characterId,
    Long skillId
) {
}
