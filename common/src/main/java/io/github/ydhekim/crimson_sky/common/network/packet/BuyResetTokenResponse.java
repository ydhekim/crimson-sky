package io.github.ydhekim.crimson_sky.common.network.packet;

/**
 * Server → client outcome of a {@link BuyResetTokenRequest} (system design §18). On success,
 * {@code resetTokenCount} is how many the character now holds and {@code remainingGold} the wallet after
 * the spend. On failure ({@code success == false}, e.g. {@code SHOP_GOLD_INSUFFICIENT}), {@code message}
 * carries the {@code MessageCode} name and both numbers are {@code 0}.
 */
public record BuyResetTokenResponse(boolean success, String message, int resetTokenCount, long remainingGold) {
}
