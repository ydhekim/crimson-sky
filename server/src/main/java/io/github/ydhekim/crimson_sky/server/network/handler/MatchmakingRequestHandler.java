package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.MatchmakingRequest;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CombatService;
import io.github.ydhekim.crimson_sky.server.service.MatchmakingService;

/**
 * Entry point to the matchmaking queue (story B1). Enforces the same ownership guardrail as
 * {@link CombatActionRequestHandler} before touching the queue: an unauthenticated connection, or a
 * {@code characterId} not owned by {@code connection.account}, is logged and dropped — a client must
 * never be able to queue (and therefore commit to a battle with) a character it does not own.
 *
 * <p>Nothing is sent back on a successful enqueue: the response a client waits for is
 * {@code MatchmakingFoundResponse}, pushed by {@code MatchmakingService} to both sides once a pairing
 * actually happens, which may be immediately or many seconds later.
 */
public class MatchmakingRequestHandler implements RequestHandler<MatchmakingRequest> {

    private static final Logger log = new Logger("MatchmakingRequestHandler", Logger.DEBUG);

    private final MatchmakingService matchmakingService;
    private final CombatService combatService;

    public MatchmakingRequestHandler(MatchmakingService matchmakingService, CombatService combatService) {
        this.matchmakingService = matchmakingService;
        this.combatService = combatService;
    }

    @Override
    public void handle(GameConnection connection, MatchmakingRequest request) {
        if (connection.account == null) {
            log.info("Rejected matchmaking request from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!combatService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected matchmaking request: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        log.info("Received matchmaking request for character " + request.characterId()
            + " from Connection ID: " + connection.getID() + ", Account ID: " + connection.account.id());
        matchmakingService.enqueue(connection, request.characterId());
    }
}
