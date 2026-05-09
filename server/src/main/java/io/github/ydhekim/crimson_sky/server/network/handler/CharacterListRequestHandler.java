package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.network.packet.CharacterListRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.CharacterListResponse;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;

public class CharacterListRequestHandler implements RequestHandler<CharacterListRequest> {

    private final CharacterDao characterDao;

    public CharacterListRequestHandler(CharacterDao characterDao) {
        this.characterDao = characterDao;
    }

    @Override
    public void handle(GameConnection connection, CharacterListRequest request) {
        if (connection.user == null) return;

        Array<Character> characters = characterDao.getCharactersByUserId(connection.user.getId());
        connection.sendTCP(new CharacterListResponse(true, "Characters loaded", characters, 3));
    }
}
