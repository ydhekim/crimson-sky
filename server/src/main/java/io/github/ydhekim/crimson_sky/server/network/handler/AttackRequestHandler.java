package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.AttackRequest;
import io.github.ydhekim.crimson_sky.server.combat.AttackResult;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.AttackService;

import java.util.Optional;

/**
 * Resolves a whole battle for the requester (story B4, system design §6/§7).
 *
 * <p>One guardrail, the same one {@link CharacterListRequestHandler}/{@link DeleteCharacterRequestHandler}
 * already apply: the connection must be authenticated and {@code characterId} must belong to
 * {@code connection.account}. The old "is this character a participant in {@code battleId}" check is
 * gone with the packet that made it necessary — {@link AttackRequest} carries no battle or opponent id
 * for a client to misuse, since the server picks the opponent itself.
 *
 * <p>{@link AttackResult#toResponse()} is what reaches the client: the internal result's
 * {@code opponentIsBot} flag and the opponent's real character id are dropped there and never
 * serialized (§7).
 */
public class AttackRequestHandler implements RequestHandler<AttackRequest> {

    private static final Logger log = new Logger("AttackRequestHandler", Logger.DEBUG);

    private final AttackService attackService;

    public AttackRequestHandler(AttackService attackService) {
        this.attackService = attackService;
    }

    @Override
    public void handle(GameConnection connection, AttackRequest request) {
        if (connection.account == null) {
            log.info("Rejected attack request from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!attackService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected attack request: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        Optional<AttackResult> result = attackService.attack(request.characterId());
        if (result.isEmpty()) {
            log.info("Dropped attack request for character " + request.characterId()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        log.info("Resolved battle " + result.get().battleId() + " for character " + request.characterId()
            + " (Connection ID: " + connection.getID() + ")");
        connection.sendTCP(result.get().toResponse());
    }
}
