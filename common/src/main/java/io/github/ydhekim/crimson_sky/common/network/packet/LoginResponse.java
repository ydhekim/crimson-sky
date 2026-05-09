package io.github.ydhekim.crimson_sky.common.network.packet;

public class LoginResponse {
    public boolean success;
    public String message;

    // Kryo requires a zero-arg constructor
    public LoginResponse() {}

    public LoginResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
