package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.BuyResetTokenRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.BuyResetTokenResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;
import io.github.ydhekim.crimson_sky.server.service.ShopService;
import io.github.ydhekim.crimson_sky.server.service.ShopService.PurchaseResult;

/**
 * Buys one skill-tree reset token (system design §18). Same guardrail posture as the repair handlers:
 * unauthenticated connections and non-owned characters are logged and dropped; an unaffordable purchase is
 * answered so the client can explain it.
 */
public class BuyResetTokenRequestHandler implements RequestHandler<BuyResetTokenRequest> {

    private static final Logger log = new Logger("BuyResetTokenRequestHandler", Logger.DEBUG);

    private final ShopService shopService;
    private final CharacterService characterService;

    public BuyResetTokenRequestHandler(ShopService shopService, CharacterService characterService) {
        this.shopService = shopService;
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, BuyResetTokenRequest request) {
        if (connection.account == null) {
            log.info("Rejected reset-token purchase from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected reset-token purchase: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        ServiceResult<PurchaseResult> result =
            shopService.buyResetToken(connection.account.id(), request.characterId());

        if (result.success()) {
            PurchaseResult data = result.data();
            connection.sendTCP(new BuyResetTokenResponse(true, result.code().name(),
                data.newCount(), data.remainingGold()));
        } else {
            connection.sendTCP(new BuyResetTokenResponse(false, result.code().name(), 0, 0L));
        }
    }
}
