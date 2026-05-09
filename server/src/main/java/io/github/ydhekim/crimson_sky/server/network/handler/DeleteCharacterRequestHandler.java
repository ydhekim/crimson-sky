package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.network.packet.DeleteCharacterRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.DeleteCharacterResponse;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;

public class DeleteCharacterRequestHandler implements RequestHandler<DeleteCharacterRequest> {

    private final CharacterDao characterDao;

    public DeleteCharacterRequestHandler(CharacterDao characterDao) {
        this.characterDao = characterDao;
    }

    @Override
    public void handle(GameConnection connection, DeleteCharacterRequest request) {
        if (connection.user == null) return;

        boolean success = characterDao.deleteCharacter(connection.user.getId(), request.name);
        connection.sendTCP(new DeleteCharacterResponse(success, success ? "Character deleted" : "Failed to delete character", request.name));
    }
}
