package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Client → server request to buy one skill-tree reset token for 1000 gold (system design §18). Buying it
 * is all this does — consuming one to actually trigger the full tree reset is M5's job.
 */
public record BuyResetTokenRequest(long characterId) {
}
