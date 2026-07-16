package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Client → server request to buy one skill-restoration scroll for 50 gold (system design §18). Gold-only:
 * the shop sells nothing for premium currency. Separate from the gold fee M4 charges to <i>use</i> a scroll.
 */
public record BuyScrollRequest(long characterId) {
}
