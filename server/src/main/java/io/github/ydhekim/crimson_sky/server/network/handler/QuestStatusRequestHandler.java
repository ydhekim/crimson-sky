package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.QuestProgress;
import io.github.ydhekim.crimson_sky.common.network.packet.QuestStatusRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.QuestStatusResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.QuestService;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;

/**
 * Returns a character's live quest status (system design §19, Epic P). Same guardrail posture as
 * {@code RepairWeaponRequestHandler}: an unauthenticated connection and a character the connection's account
 * does not own are logged and dropped, never answered; anything past that is answered so the client can react.
 */
public class QuestStatusRequestHandler implements RequestHandler<QuestStatusRequest> {

    private static final Logger log = new Logger("QuestStatusRequestHandler", Logger.DEBUG);

    private final QuestService questService;
    private final CharacterService characterService;

    public QuestStatusRequestHandler(QuestService questService, CharacterService characterService) {
        this.questService = questService;
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, QuestStatusRequest request) {
        if (connection.account == null) {
            log.info("Rejected quest status from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected quest status: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        ServiceResult<Array<QuestProgress>> result =
            questService.getStatus(connection.account.id(), request.characterId());

        if (result.success()) {
            connection.sendTCP(new QuestStatusResponse(true, result.code().name(), result.data()));
        } else {
            connection.sendTCP(new QuestStatusResponse(false, result.code().name(), null));
        }
    }
}
