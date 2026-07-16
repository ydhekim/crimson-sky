package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.RepairWeaponRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.RepairWeaponResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;
import io.github.ydhekim.crimson_sky.server.service.ShopService;
import io.github.ydhekim.crimson_sky.server.service.ShopService.RepairWeaponResult;

/**
 * Repairs one owned weapon (system design §18).
 *
 * <p>Two guardrails are drops, not answers — the same posture as {@code LearnSkillNodeRequestHandler}:
 * an unauthenticated connection and a character the connection's account does not own are logged and
 * ignored, never trusting the client-supplied id beyond that check. Refusals the player can act on
 * (unknown weapon, nothing to repair, not enough gold or tokens) <i>are</i> answered so the client can
 * explain them.
 */
public class RepairWeaponRequestHandler implements RequestHandler<RepairWeaponRequest> {

    private static final Logger log = new Logger("RepairWeaponRequestHandler", Logger.DEBUG);

    private final ShopService shopService;
    private final CharacterService characterService;

    public RepairWeaponRequestHandler(ShopService shopService, CharacterService characterService) {
        this.shopService = shopService;
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, RepairWeaponRequest request) {
        if (connection.account == null) {
            log.info("Rejected weapon repair from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected weapon repair: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        ServiceResult<RepairWeaponResult> result = shopService.repairWeapon(
            connection.account.id(), request.characterId(), request.weaponId(), request.useToken());

        if (result.success()) {
            RepairWeaponResult data = result.data();
            connection.sendTCP(new RepairWeaponResponse(true, result.code().name(), data.weapon(),
                data.remainingGold(), data.remainingRepairTokens()));
        } else {
            connection.sendTCP(new RepairWeaponResponse(false, result.code().name(), null, 0L, 0));
        }
    }
}
