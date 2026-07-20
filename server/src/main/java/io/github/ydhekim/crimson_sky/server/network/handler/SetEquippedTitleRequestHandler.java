package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.SetEquippedTitleRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.SetEquippedTitleResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterPageService;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;

/**
 * Equips (or clears) a character's title (system design §22, Epic S4). Same guardrail posture as
 * {@code CharacterPageRequestHandler}: an unauthenticated connection and a not-owned character are logged and
 * dropped; anything past that is answered so the client can react to an accept or a {@code TITLE_NOT_UNLOCKED}.
 */
public class SetEquippedTitleRequestHandler implements RequestHandler<SetEquippedTitleRequest> {

    private static final Logger log = new Logger("SetEquippedTitleRequestHandler", Logger.DEBUG);

    private final CharacterPageService characterPageService;
    private final CharacterService characterService;

    public SetEquippedTitleRequestHandler(CharacterPageService characterPageService, CharacterService characterService) {
        this.characterPageService = characterPageService;
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, SetEquippedTitleRequest request) {
        if (connection.account == null) {
            log.info("Rejected set-title request from unauthenticated Connection ID: " + connection.getID());
            return;
        }
        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected set-title: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id());
            return;
        }

        ServiceResult<String> result = characterPageService.setEquippedTitle(
            connection.account.id(), request.characterId(), request.titleId());
        connection.sendTCP(new SetEquippedTitleResponse(result.success(), result.code().name(),
            result.success() ? result.data() : null));
    }
}
