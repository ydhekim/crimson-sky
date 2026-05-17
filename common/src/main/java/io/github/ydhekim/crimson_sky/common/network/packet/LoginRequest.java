package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.PlatformType;

import java.util.Map;

public record LoginRequest(
    PlatformType platformType,
    String identityToken,
    String clientVersion,
    String deviceId,
    Map<String, String> metadata
) {
}
