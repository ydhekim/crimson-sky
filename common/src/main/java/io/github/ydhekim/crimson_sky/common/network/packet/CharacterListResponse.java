package io.github.ydhekim.crimson_sky.common.network.packet;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Character;

public class CharacterListResponse {
    public boolean success;
    public String message;
    public Array<Character> characters;
    public int maxCharacterSlots;

    public CharacterListResponse() {}

    public CharacterListResponse(boolean success, String message, Array<Character> characters, int maxCharacterSlots) {
        this.success = success;
        this.message = message;
        this.characters = characters;
        this.maxCharacterSlots = maxCharacterSlots;
    }
}
