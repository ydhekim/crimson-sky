package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.network.packet.CreateCharacterRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.CreateCharacterResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;

public class CreateCharacterRequestHandler implements RequestHandler<CreateCharacterRequest> {

    private final CharacterService characterService;

    public CreateCharacterRequestHandler(CharacterService characterService) {
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, CreateCharacterRequest request) {
        if (connection.account == null) return;

        var result = characterService.createCharacter(connection.account.id(), request.character);

        connection.sendTCP(new CreateCharacterResponse(result.success(), result.code().name(), request.character));

    }
}
