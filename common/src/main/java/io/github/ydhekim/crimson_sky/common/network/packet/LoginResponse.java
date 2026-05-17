package io.github.ydhekim.crimson_sky.common.network.packet;

public record LoginResponse(
    boolean success,
    String message,
    long accountId,
    int maxSlots
) {
}
