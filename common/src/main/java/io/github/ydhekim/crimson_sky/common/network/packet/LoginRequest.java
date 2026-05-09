package io.github.ydhekim.crimson_sky.common.network.packet;

public class LoginRequest {
    public String username;
    public String password;

    // Kryo requires a zero-arg constructor
    public LoginRequest() {}

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
