package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.CharacterListRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.CharacterListResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;

public class CharacterListRequestHandler implements RequestHandler<CharacterListRequest> {
    private static final Logger log = new Logger("CharacterListRequestHandler", Logger.DEBUG);
    private final CharacterService characterService;

    public CharacterListRequestHandler(CharacterService characterService) {
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, CharacterListRequest request) {
        if (connection.account == null) {
            log.info("Rejected character list request from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        log.info("Received character list request from Connection ID: " + connection.getID() + ", Account ID: " + connection.account.id());
        var result = characterService.getCharacters(connection.account.id());

        if (result.success()) {
            log.info("Successfully fetched character list for Connection ID: " + connection.getID() + ", Account ID: " + connection.account.id());
        } else {
            log.info("Failed to fetch character list for Connection ID: " + connection.getID() + ", Account ID: " + connection.account.id() + ". Reason: " + result.code().name());
        }

        connection.sendTCP(new CharacterListResponse(result.success(), result.code().name(), result.data(), connection.account.maxSlots()));
    }
}
