package io.github.ydhekim.crimson_sky.common.network.packet;

public record SaveAccountSettingsResponse(
    boolean success,
    String message
) {
}
