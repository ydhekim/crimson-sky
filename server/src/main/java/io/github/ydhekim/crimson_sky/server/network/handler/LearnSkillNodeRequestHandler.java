package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.LearnSkillNodeRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LearnSkillNodeResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;
import io.github.ydhekim.crimson_sky.server.service.SkillTreeService;
import io.github.ydhekim.crimson_sky.server.service.SkillTreeService.LearnSkillNodeResult;

/**
 * Learns or upgrades a skill-tree node (system design §16).
 *
 * <p>Two guardrails are drops, not answers — the same posture as {@code AllocateStatPointsRequestHandler}:
 * an unauthenticated connection and a character the connection's account does not own are logged and
 * ignored, never trusting the client-supplied id beyond that check. Validation failures the player can
 * act on (unknown node, level/faction gate, rank maxed, insufficient points/gold) <i>are</i> answered so
 * the client can explain the refusal.
 */
public class LearnSkillNodeRequestHandler implements RequestHandler<LearnSkillNodeRequest> {

    private static final Logger log = new Logger("LearnSkillNodeRequestHandler", Logger.DEBUG);

    private final SkillTreeService skillTreeService;
    private final CharacterService characterService;

    public LearnSkillNodeRequestHandler(SkillTreeService skillTreeService, CharacterService characterService) {
        this.skillTreeService = skillTreeService;
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, LearnSkillNodeRequest request) {
        if (connection.account == null) {
            log.info("Rejected skill-tree learn from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected skill-tree learn: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        ServiceResult<LearnSkillNodeResult> result =
            skillTreeService.learnOrUpgrade(connection.account.id(), request.characterId(), request.nodeId());

        if (result.success()) {
            LearnSkillNodeResult data = result.data();
            connection.sendTCP(new LearnSkillNodeResponse(true, result.code().name(), data.node(),
                data.newRank(), data.remainingSkillPoints(), data.remainingGold()));
        } else {
            connection.sendTCP(new LearnSkillNodeResponse(false, result.code().name(), null, 0, 0, 0L));
        }
    }
}
