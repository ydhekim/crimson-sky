package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.RepairPetRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.RepairPetResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;
import io.github.ydhekim.crimson_sky.server.service.ShopService;
import io.github.ydhekim.crimson_sky.server.service.ShopService.RepairPetResult;

/**
 * Restores one owned pet's health (system design §18). Same guardrail posture as
 * {@code RepairWeaponRequestHandler}: unauthenticated connections and non-owned characters are logged and
 * dropped; every actionable refusal is answered.
 */
public class RepairPetRequestHandler implements RequestHandler<RepairPetRequest> {

    private static final Logger log = new Logger("RepairPetRequestHandler", Logger.DEBUG);

    private final ShopService shopService;
    private final CharacterService characterService;

    public RepairPetRequestHandler(ShopService shopService, CharacterService characterService) {
        this.shopService = shopService;
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, RepairPetRequest request) {
        if (connection.account == null) {
            log.info("Rejected pet repair from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected pet repair: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        ServiceResult<RepairPetResult> result = shopService.repairPet(
            connection.account.id(), request.characterId(), request.petId(), request.useToken());

        if (result.success()) {
            RepairPetResult data = result.data();
            connection.sendTCP(new RepairPetResponse(true, result.code().name(), data.pet(),
                data.remainingGold(), data.remainingPetCareKits()));
        } else {
            connection.sendTCP(new RepairPetResponse(false, result.code().name(), null, 0L, 0));
        }
    }
}
