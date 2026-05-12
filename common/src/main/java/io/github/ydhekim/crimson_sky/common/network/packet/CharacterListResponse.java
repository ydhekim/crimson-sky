package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Character;

import java.util.List;

public class CharacterListResponse {
    public boolean success;
    public String message;
    public List<Character> characters;
    public int maxCharacterSlots;

    public CharacterListResponse() {}

    public CharacterListResponse(boolean success, String message, List<Character> characters, int maxCharacterSlots) {
        this.success = success;
        this.message = message;
        this.characters = characters;
        this.maxCharacterSlots = maxCharacterSlots;
    }
}
