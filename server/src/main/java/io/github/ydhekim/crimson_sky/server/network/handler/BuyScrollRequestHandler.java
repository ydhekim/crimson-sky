package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.BuyScrollRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.BuyScrollResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;
import io.github.ydhekim.crimson_sky.server.service.ShopService;
import io.github.ydhekim.crimson_sky.server.service.ShopService.PurchaseResult;

/**
 * Buys one skill-restoration scroll (system design §18). Same guardrail posture as the repair handlers:
 * unauthenticated connections and non-owned characters are logged and dropped; an unaffordable purchase is
 * answered so the client can explain it.
 */
public class BuyScrollRequestHandler implements RequestHandler<BuyScrollRequest> {

    private static final Logger log = new Logger("BuyScrollRequestHandler", Logger.DEBUG);

    private final ShopService shopService;
    private final CharacterService characterService;

    public BuyScrollRequestHandler(ShopService shopService, CharacterService characterService) {
        this.shopService = shopService;
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, BuyScrollRequest request) {
        if (connection.account == null) {
            log.info("Rejected scroll purchase from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected scroll purchase: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        ServiceResult<PurchaseResult> result =
            shopService.buyScroll(connection.account.id(), request.characterId());

        if (result.success()) {
            PurchaseResult data = result.data();
            connection.sendTCP(new BuyScrollResponse(true, result.code().name(),
                data.newCount(), data.remainingGold()));
        } else {
            connection.sendTCP(new BuyScrollResponse(false, result.code().name(), 0, 0L));
        }
    }
}
