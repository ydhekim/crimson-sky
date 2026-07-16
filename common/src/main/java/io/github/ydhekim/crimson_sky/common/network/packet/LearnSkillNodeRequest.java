package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Client → server request to learn or upgrade a skill-tree node (system design §16). "Learn" and
 * "upgrade rank" are the same action — whichever rank comes next for {@code nodeId}. The server
 * validates ownership of {@code characterId}, that the node exists, the level/faction gate, that the
 * node isn't already at max rank, and that the character can afford the next rank's skill-point and
 * gold cost, then applies it atomically.
 */
public record LearnSkillNodeRequest(long characterId, String nodeId) {
}
