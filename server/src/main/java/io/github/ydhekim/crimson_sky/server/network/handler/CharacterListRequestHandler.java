package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.network.packet.CharacterListRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.CharacterListResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;

public class CharacterListRequestHandler implements RequestHandler<CharacterListRequest> {

    private final CharacterService characterService;

    public CharacterListRequestHandler(CharacterService characterService) {
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, CharacterListRequest request) {
        if (connection.account == null) return;

        var result = characterService.getCharacters(connection.account.id());

        connection.sendTCP(new CharacterListResponse(result.success(), result.code().name(), result.data(), connection.account.maxSlots()));
    }
}
