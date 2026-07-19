package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Client → server request for the live status of all three quests for one character (system design §19,
 * Epic P). The server re-derives progress from {@code battle_history} and the claim state from
 * {@code quest_claims} — the client's word on completion is never trusted. Ownership of {@code characterId}
 * is validated against the connection's account.
 */
public record QuestStatusRequest(long characterId) {
}
