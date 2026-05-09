package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.network.packet.CreateCharacterRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.CreateCharacterResponse;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;

public class CreateCharacterRequestHandler implements RequestHandler<CreateCharacterRequest> {

    private final CharacterDao characterDao;

    public CreateCharacterRequestHandler(CharacterDao characterDao) {
        this.characterDao = characterDao;
    }

    @Override
    public void handle(GameConnection connection, CreateCharacterRequest request) {
        if (connection.user == null) return;

        if (characterDao.getCharacterCount(connection.user.getId()) >= 3) {
            connection.sendTCP(new CreateCharacterResponse(false, "Maximum characters reached", null));
            return;
        }

        boolean success = characterDao.createCharacter(connection.user.getId(), request.name);
        if (success) {
            connection.sendTCP(new CreateCharacterResponse(true, "Character created", null));
        } else {
            connection.sendTCP(new CreateCharacterResponse(false, "Failed to create character", null));
        }
    }
}
