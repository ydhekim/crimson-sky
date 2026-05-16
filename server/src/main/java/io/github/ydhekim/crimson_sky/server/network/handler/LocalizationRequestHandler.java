package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.network.packet.LocalizationRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LocalizationResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.LocalizationService;

public class LocalizationRequestHandler implements RequestHandler<LocalizationRequest> {
    private final LocalizationService localizationService;

    public LocalizationRequestHandler(LocalizationService localizationService) {
        this.localizationService = localizationService;
    }

    @Override
    public void handle(GameConnection connection, LocalizationRequest request) {
        // Localization should be accessible before login, so we don't check for account == null
        var result = localizationService.getLanguageBundle(request.langCode);

        connection.sendTCP(new LocalizationResponse(result.success(), result.code().name(), result.data()));
    }
}
