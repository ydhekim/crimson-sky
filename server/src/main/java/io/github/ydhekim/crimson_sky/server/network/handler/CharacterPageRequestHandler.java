package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.CharacterPage;
import io.github.ydhekim.crimson_sky.common.network.packet.CharacterPageRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.CharacterPageResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterPageService;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;

/**
 * Returns a character's full page (system design §22, Epic S3). Same guardrail posture as
 * {@code LadderStatusRequestHandler}: an unauthenticated connection and a character the connection's account
 * does not own are logged and dropped, never answered; anything past that is answered so the client can react.
 */
public class CharacterPageRequestHandler implements RequestHandler<CharacterPageRequest> {

    private static final Logger log = new Logger("CharacterPageRequestHandler", Logger.DEBUG);

    private final CharacterPageService characterPageService;
    private final CharacterService characterService;

    public CharacterPageRequestHandler(CharacterPageService characterPageService, CharacterService characterService) {
        this.characterPageService = characterPageService;
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, CharacterPageRequest request) {
        if (connection.account == null) {
            log.info("Rejected character page request from unauthenticated Connection ID: " + connection.getID());
            return;
        }
        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected character page: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id());
            return;
        }

        ServiceResult<CharacterPage> result =
            characterPageService.getCharacterPage(connection.account.id(), request.characterId());
        connection.sendTCP(result.success()
            ? new CharacterPageResponse(true, result.code().name(), result.data())
            : new CharacterPageResponse(false, result.code().name(), null));
    }
}
