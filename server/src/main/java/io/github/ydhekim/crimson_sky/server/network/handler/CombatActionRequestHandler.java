package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.CombatActionRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.CombatActionResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CombatService;

import java.util.Optional;

/**
 * Resolves one combat turn for the requester (stories B3 + B1, system design §6).
 *
 * <p>Two guardrails run before anything is resolved, both fail-closed and both logged-and-dropped in
 * the style of {@link CharacterListRequestHandler}/{@link DeleteCharacterRequestHandler}:
 * <ol>
 *   <li>the connection must be authenticated, and {@code characterId} must be owned by
 *       {@code connection.account} — never trusting the client-supplied id beyond that check;</li>
 *   <li>that character must actually be a participant in {@code battleId} — ownership alone would
 *       otherwise let a player submit actions into someone else's battle (B1).</li>
 * </ol>
 *
 * <p>{@code request.skillId()} is ignored: the resolution cascade (§4.1–§4.4) chooses actions
 * probabilistically from stats and pouch priority order, so there is no player-chosen action for it
 * to name. See {@link CombatService} for that and for the still-open question of how the *opposing*
 * client learns this turn's outcome — this handler answers the requester only.
 *
 * <p><b>Rejections are dropped, not answered</b> — a rejected request sends no packet back, matching
 * the pre-existing behaviour of this handler and of the unauthenticated branch elsewhere. Whether a
 * rejection should instead answer with a failure {@code CombatActionResponse} is left open on purpose:
 * the packet has no success/error field today, and adding one overlaps with the undecided
 * turn-notification shape above. Both belong in the same design decision.
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

        Optional<CombatActionResponse> response = combatService.resolveTurn(request.battleId(), request.characterId());
        if (response.isEmpty()) {
            log.info("Dropped combat action request for character " + request.characterId()
                + " in battle " + request.battleId() + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        log.info("Resolved turn " + response.get().turnNumber() + " of battle " + request.battleId()
            + " for character " + request.characterId() + " (Connection ID: " + connection.getID() + ")");
        connection.sendTCP(response.get());
    }
}
