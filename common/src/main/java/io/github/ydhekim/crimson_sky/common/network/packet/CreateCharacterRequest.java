package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Character;

public class CreateCharacterRequest {
    public Character character;

    public CreateCharacterRequest() {}

    public CreateCharacterRequest(Character character) {
        this.character = character;
    }
}
