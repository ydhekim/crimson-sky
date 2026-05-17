package io.github.ydhekim.crimson_sky.common.network.packet;

import java.util.Map;

public record LocalizationResponse(
    boolean success,
    String message,
    Map<String, String> translations
) {
}
