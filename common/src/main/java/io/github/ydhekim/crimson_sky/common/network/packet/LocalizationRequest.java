package io.github.ydhekim.crimson_sky.common.network.packet;

public class LocalizationRequest {
    public String langCode;

    public LocalizationRequest() {
    }

    public LocalizationRequest(String langCode) {
        this.langCode = langCode;
    }

}
