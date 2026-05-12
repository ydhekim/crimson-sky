package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.PlatformType;
import java.util.Map;

public class LoginRequest {
    public PlatformType platformType;
    public String identityToken;
    public String clientVersion;
    public String deviceId;
    public Map<String, String> metadata;

    public LoginRequest() {}

    public LoginRequest(PlatformType platformType, String identityToken, String clientVersion, String deviceId, Map<String, String> metadata) {
        this.platformType = platformType;
        this.identityToken = identityToken;
        this.clientVersion = clientVersion;
        this.deviceId = deviceId;
        this.metadata = metadata;
    }
}
