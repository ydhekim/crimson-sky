package io.github.ydhekim.crimson_sky.common.network.packet;

public class CreateCharacterRequest {
    public String name;

    public CreateCharacterRequest() {}

    public CreateCharacterRequest(String name) {
        this.name = name;
    }
}
