package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.DeleteCharacterRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.DeleteCharacterResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;

public class DeleteCharacterRequestHandler implements RequestHandler<DeleteCharacterRequest> {
    private static final Logger log = new Logger("DeleteCharacterRequestHandler", Logger.DEBUG);
    private final CharacterService characterService;

    public DeleteCharacterRequestHandler(CharacterService characterService) {
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, DeleteCharacterRequest request) {
        if (connection.account == null) {
            log.info("Rejected delete character request from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        log.info("Received delete character request for name '" + request.name() + "' from Connection ID: " + connection.getID() + ", Account ID: " + connection.account.id());
        var result = characterService.deleteCharacter(connection.account.id(), request.name());

        if (result.success()) {
            log.info("Successfully deleted character '" + request.name() + "' for Connection ID: " + connection.getID() + ", Account ID: " + connection.account.id());
        } else {
            log.info("Failed to delete character '" + request.name() + "' for Connection ID: " + connection.getID() + ", Account ID: " + connection.account.id() + ". Reason: " + result.code().name());
        }

        connection.sendTCP(new DeleteCharacterResponse(result.success(), result.code().name(), request.name()));
    }
}
