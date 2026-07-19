package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.QuestClaimResult;
import io.github.ydhekim.crimson_sky.common.network.packet.ClaimQuestRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.ClaimQuestResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.QuestService;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;

/**
 * Claims a completed quest's reward (system design §19, Epic P). Same guardrail posture as
 * {@code RepairWeaponRequestHandler}: an unauthenticated connection and a non-owned character are logged and
 * dropped; every actionable refusal (unknown quest, not complete, already claimed, cap reached, invalid
 * reward choice) is answered so the client can explain it.
 */
public class ClaimQuestRequestHandler implements RequestHandler<ClaimQuestRequest> {

    private static final Logger log = new Logger("ClaimQuestRequestHandler", Logger.DEBUG);

    private final QuestService questService;
    private final CharacterService characterService;

    public ClaimQuestRequestHandler(QuestService questService, CharacterService characterService) {
        this.questService = questService;
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, ClaimQuestRequest request) {
        if (connection.account == null) {
            log.info("Rejected quest claim from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected quest claim: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        ServiceResult<QuestClaimResult> result = questService.claim(
            connection.account.id(), request.characterId(), request.questId(), request.rewardChoice());

        if (result.success()) {
            connection.sendTCP(new ClaimQuestResponse(true, result.code().name(), result.data()));
        } else {
            connection.sendTCP(new ClaimQuestResponse(false, result.code().name(), null));
        }
    }
}
