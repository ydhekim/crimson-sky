package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Stats;

/**
 * Client → server request to spend earned stat points (Epic L / system design §15). {@code delta} is how
 * many points the player wants to add to each of the eight stats in one round trip — a batch, not a
 * point-at-a-time drip. The server validates ownership of {@code characterId}, that {@code sum(delta)} is
 * within the character's unspent-stat-point balance, and that no resulting stat exceeds
 * {@link Stats#MAX_STAT_VALUE}, then applies it atomically and decrements the balance.
 */
public record AllocateStatPointsRequest(long characterId, Stats delta) {
}
