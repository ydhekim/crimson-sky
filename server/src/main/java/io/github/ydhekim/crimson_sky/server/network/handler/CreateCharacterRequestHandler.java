package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.CreateCharacterRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.CreateCharacterResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;

public class CreateCharacterRequestHandler implements RequestHandler<CreateCharacterRequest> {
    private static final Logger log = new Logger("CreateCharacterRequestHandler", Logger.DEBUG);
    private final CharacterService characterService;

    public CreateCharacterRequestHandler(CharacterService characterService) {
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, CreateCharacterRequest request) {
        if (connection.account == null) {
            log.info("Rejected create character request from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        log.info("Received create character request for name '" + request.character().name() + "' from Connection ID: " + connection.getID() + ", Account ID: " + connection.account.id());
        var result = characterService.createCharacter(connection.account.id(), connection.account.maxSlots(), request.character());

        if (result.success()) {
            log.info("Successfully created character '" + request.character().name() + "' for Connection ID: " + connection.getID() + ", Account ID: " + connection.account.id());
        } else {
            log.info("Failed to create character '" + request.character().name() + "' for Connection ID: " + connection.getID() + ", Account ID: " + connection.account.id() + ". Reason: " + result.code().name());
        }

        connection.sendTCP(new CreateCharacterResponse(result.success(), result.code().name(), request.character()));

    }
}
