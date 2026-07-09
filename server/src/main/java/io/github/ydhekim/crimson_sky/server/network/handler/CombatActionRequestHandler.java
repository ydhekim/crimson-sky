package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.CombatActionRequest;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CombatService;

/**
 * Ownership guardrail for combat requests (story B3, system design §6/§13). Follows the identical
 * shape as {@link CharacterListRequestHandler}/{@link DeleteCharacterRequestHandler}: reject an
 * unauthenticated connection, then reject any request whose {@code characterId} is not owned by
 * {@code connection.account} — never trusting the client-supplied id beyond that check. A rejected
 * request is logged and dropped (never silently processed), matching the existing handlers' early-return
 * rejection style.
 *
 * <p>A valid, owned request is acknowledged only in the log for now: actually resolving the turn
 * needs a live {@code BattleSession} from matchmaking (story B1) and the {@code core} combat engine
 * wired into the server — both out of scope this pass (see {@link CombatService}). This handler exists
 * so the ownership boundary is enforced from the very first combat packet, before any turn logic
 * rides on top of it.
 */
public class CombatActionRequestHandler implements RequestHandler<CombatActionRequest> {
    private static final Logger log = new Logger("CombatActionRequestHandler", Logger.DEBUG);
    private final CombatService combatService;

    public CombatActionRequestHandler(CombatService combatService) {
        this.combatService = combatService;
    }

    @Override
    public void handle(GameConnection connection, CombatActionRequest request) {
        if (connection.account == null) {
            log.info("Rejected combat action request from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!combatService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected combat action request: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        // Ownership confirmed. Turn resolution (BattleEngine tick over a matched BattleSession →
        // CombatActionResponse) is pending matchmaking (B1) + server↔core wiring (system design §6).
        log.info("Accepted combat action request for owned character " + request.characterId()
            + " in battle " + request.battleId() + " (Account ID: " + connection.account.id()
            + "). Turn execution pending matchmaking/engine wiring.");
    }
}
