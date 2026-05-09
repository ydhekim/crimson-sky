package io.github.ydhekim.crimson_sky.common.network.packet;

public class SignUpRequest {
    public String username;
    public String password;

    // Kryo requires a zero-arg constructor
    public SignUpRequest() {}

    public SignUpRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
