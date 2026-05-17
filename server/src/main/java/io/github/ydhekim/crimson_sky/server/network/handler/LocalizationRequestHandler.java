package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.LocalizationRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LocalizationResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.LocalizationService;

public class LocalizationRequestHandler implements RequestHandler<LocalizationRequest> {
    private static final Logger log = new Logger("LocalizationRequestHandler", Logger.DEBUG);
    private final LocalizationService localizationService;

    public LocalizationRequestHandler(LocalizationService localizationService) {
        this.localizationService = localizationService;
    }

    @Override
    public void handle(GameConnection connection, LocalizationRequest request) {
        log.info("Received localization request from Connection ID: " + connection.getID() + " for language code: " + request.langCode());
        var result = localizationService.getLanguageBundle(request.langCode());

        if (result.success()) {
            log.info("Successfully processed localization request for Connection ID: " + connection.getID());
        } else {
            log.info("Failed to process localization request for Connection ID: " + connection.getID() + ". Reason: " + result.code().name());
        }

        connection.sendTCP(new LocalizationResponse(result.success(), result.code().name(), result.data()));
    }
}
