package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.network.packet.SaveLoadoutRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.SaveLoadoutResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;

/**
 * Saves a character's equipped loadout (system design §4.4/§16) — the capability that makes a learned
 * passive actually equippable.
 *
 * <p>Two guardrails are drops, not answers, the same posture as the other character-scoped handlers: an
 * unauthenticated connection and a character the connection's account does not own are logged and
 * ignored. Actionable validation failures (an item not owned, too many skill slots) <i>are</i> answered
 * so the client can explain the refusal.
 */
public class SaveLoadoutRequestHandler implements RequestHandler<SaveLoadoutRequest> {

    private static final Logger log = new Logger("SaveLoadoutRequestHandler", Logger.DEBUG);

    private final CharacterService characterService;

    public SaveLoadoutRequestHandler(CharacterService characterService) {
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, SaveLoadoutRequest request) {
        if (connection.account == null) {
            log.info("Rejected loadout save from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected loadout save: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        ServiceResult<Loadout> result =
            characterService.saveLoadout(connection.account.id(), request.characterId(), request.loadout());

        if (result.success()) {
            connection.sendTCP(new SaveLoadoutResponse(true, result.code().name(), result.data()));
        } else {
            connection.sendTCP(new SaveLoadoutResponse(false, result.code().name(), null));
        }
    }
}
