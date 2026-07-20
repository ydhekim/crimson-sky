package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.LadderStatus;
import io.github.ydhekim.crimson_sky.common.network.packet.LadderStatusRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LadderStatusResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.LadderService;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;

/**
 * Returns a character's live monthly ladder standing (system design §21, Epic R3). Same guardrail posture as
 * {@code QuestStatusRequestHandler}: an unauthenticated connection and a character the connection's account
 * does not own are logged and dropped, never answered; anything past that is answered so the client can react.
 */
public class LadderStatusRequestHandler implements RequestHandler<LadderStatusRequest> {

    private static final Logger log = new Logger("LadderStatusRequestHandler", Logger.DEBUG);

    private final LadderService ladderService;
    private final CharacterService characterService;

    public LadderStatusRequestHandler(LadderService ladderService, CharacterService characterService) {
        this.ladderService = ladderService;
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, LadderStatusRequest request) {
        if (connection.account == null) {
            log.info("Rejected ladder status from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected ladder status: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        ServiceResult<LadderStatus> result =
            ladderService.getStatus(connection.account.id(), request.characterId());

        if (result.success()) {
            connection.sendTCP(new LadderStatusResponse(true, result.code().name(), result.data()));
        } else {
            connection.sendTCP(new LadderStatusResponse(false, result.code().name(), null));
        }
    }
}
