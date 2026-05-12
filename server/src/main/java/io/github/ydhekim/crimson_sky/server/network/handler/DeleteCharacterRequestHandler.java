package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.network.packet.DeleteCharacterRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.DeleteCharacterResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;

public class DeleteCharacterRequestHandler implements RequestHandler<DeleteCharacterRequest> {

    private final CharacterService characterService;

    public DeleteCharacterRequestHandler(CharacterService characterService) {
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, DeleteCharacterRequest request) {
        if (connection.account == null) return;

        var result = characterService.deleteCharacter(connection.account.id(), request.name);
        connection.sendTCP(new DeleteCharacterResponse(result.success(), result.code().name(), request.name));
    }
}
