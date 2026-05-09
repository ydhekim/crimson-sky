package io.github.ydhekim.crimson_sky.common.network.packet;

public class SignUpResponse {
    public boolean success;
    public String message;

    // Kryo requires a zero-arg constructor
    public SignUpResponse() {}

    public SignUpResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
