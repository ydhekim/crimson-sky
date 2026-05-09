package io.github.ydhekim.crimson_sky.common.network.packet;

public class DeleteCharacterResponse {
    public boolean success;
    public String message;
    public String deletedCharacterName;

    public DeleteCharacterResponse() {}

    public DeleteCharacterResponse(boolean success, String message, String deletedCharacterName) {
        this.success = success;
        this.message = message;
        this.deletedCharacterName = deletedCharacterName;
    }
}
