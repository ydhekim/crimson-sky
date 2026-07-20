package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Client → server request to equip an unlocked title, or clear it ({@code titleId == null}). Ownership of
 * {@code characterId} and unlock-ownership of {@code titleId} are both validated server-side.
 */
public record SetEquippedTitleRequest(long characterId, String titleId) {
}
