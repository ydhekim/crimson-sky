package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.AllocateStatPointsRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.AllocateStatPointsResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;

/**
 * Spends a character's earned stat points (Epic L / system design §15).
 *
 * <p>Two guardrails are drops, not answers — the same posture {@link AttackRequestHandler} takes: an
 * unauthenticated connection and a character the connection's account does not own are logged and
 * ignored, never trusting the client-supplied id beyond that check. Validation failures the player can
 * act on (insufficient points, would exceed the stat cap) <i>are</i> answered, so the client can show
 * why the spend was refused.
 *
 * <p>The spend is already committed by the time the success response is sent — {@code newStats} and
 * {@code unspentStatPoints} report the persisted result, not a promise.
 */
public class AllocateStatPointsRequestHandler implements RequestHandler<AllocateStatPointsRequest> {

    private static final Logger log = new Logger("AllocateStatPointsRequestHandler", Logger.DEBUG);

    private final CharacterService characterService;

    public AllocateStatPointsRequestHandler(CharacterService characterService) {
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, AllocateStatPointsRequest request) {
        if (connection.account == null) {
            log.info("Rejected stat-point allocation from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected stat-point allocation: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        ServiceResult<CharacterService.AllocateStatPointsResult> result =
            characterService.allocateStatPoints(connection.account.id(), request.characterId(), request.delta());

        if (result.success()) {
            connection.sendTCP(new AllocateStatPointsResponse(true, result.code().name(),
                result.data().newStats(), result.data().unspentStatPoints()));
        } else {
            connection.sendTCP(new AllocateStatPointsResponse(false, result.code().name(), null, 0));
        }
    }
}
