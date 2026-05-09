package io.github.ydhekim.crimson_sky.common.network.packet;

public class DeleteCharacterRequest {
    public String name; // The name of the character to delete

    public DeleteCharacterRequest() {}

    public DeleteCharacterRequest(String name) {
        this.name = name;
    }
}
