package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Client → server request to claim the previous month's ladder reward (system design §21, Epic R3). The
 * server re-validates the standing live against {@code battle_history} and the claim guard against
 * {@code ladder_claims} — it never trusts a client-reported rank.
 */
public record ClaimLadderRewardRequest(long characterId) {
}
