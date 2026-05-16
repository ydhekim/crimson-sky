package io.github.ydhekim.crimson_sky.common.network.packet;

import java.util.Map;

public class LocalizationResponse {
    public boolean success;
    public String message;
    public Map<String, String> translations;

    public LocalizationResponse() {}

    public LocalizationResponse(boolean success, String message, Map<String, String> translations) {
        this.success = success;
        this.message = message;
        this.translations = translations;
    }
}
