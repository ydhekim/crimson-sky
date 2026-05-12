package io.github.ydhekim.crimson_sky.network;

import io.github.ydhekim.crimson_sky.common.network.packet.*;

public interface NetworkListener {
    default void onConnected() {}
    default void onDisconnected() {}
    default void onLoginResponse(LoginResponse response) {}
    default void onCharacterListResponse(CharacterListResponse response) {}
    default void onCreateCharacterResponse(CreateCharacterResponse response) {}
    default void onDeleteCharacterResponse(DeleteCharacterResponse response) {}
}
