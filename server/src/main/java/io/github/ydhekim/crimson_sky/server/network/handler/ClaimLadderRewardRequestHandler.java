package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.LadderClaimResult;
import io.github.ydhekim.crimson_sky.common.network.packet.ClaimLadderRewardRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.ClaimLadderRewardResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.LadderService;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;

/**
 * Claims the previous month's ladder reward (system design §21, Epic R3). Same guardrail posture as
 * {@code ClaimQuestRequestHandler}: an unauthenticated connection and a non-owned character are logged and
 * dropped; every actionable refusal (not ranked-eligible, no reward this rank, already claimed) is answered
 * so the client can explain it.
 */
public class ClaimLadderRewardRequestHandler implements RequestHandler<ClaimLadderRewardRequest> {

    private static final Logger log = new Logger("ClaimLadderRewardRequestHandler", Logger.DEBUG);

    private final LadderService ladderService;
    private final CharacterService characterService;

    public ClaimLadderRewardRequestHandler(LadderService ladderService, CharacterService characterService) {
        this.ladderService = ladderService;
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, ClaimLadderRewardRequest request) {
        if (connection.account == null) {
            log.info("Rejected ladder claim from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected ladder claim: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        ServiceResult<LadderClaimResult> result =
            ladderService.claim(connection.account.id(), request.characterId());

        if (result.success()) {
            connection.sendTCP(new ClaimLadderRewardResponse(true, result.code().name(), result.data()));
        } else {
            connection.sendTCP(new ClaimLadderRewardResponse(false, result.code().name(), null));
        }
    }
}
