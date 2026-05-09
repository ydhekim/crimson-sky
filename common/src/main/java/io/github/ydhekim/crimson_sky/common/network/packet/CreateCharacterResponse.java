package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Character;

public class CreateCharacterResponse {
    public boolean success;
    public String message;
    public Character character;

    public CreateCharacterResponse() {}

    public CreateCharacterResponse(boolean success, String message, Character character) {
        this.success = success;
        this.message = message;
        this.character = character;
    }
}
