package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.SaveAccountSettingsRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.SaveAccountSettingsResponse;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.AccountService;
import io.github.ydhekim.crimson_sky.server.service.UserService;

public class SaveAccountSettingsRequestHandler implements RequestHandler<SaveAccountSettingsRequest> {
    private static final Logger log = new Logger("SaveAccountSettingsRequestHandler", Logger.DEBUG);
    private final AccountService accountService;

    public SaveAccountSettingsRequestHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public void handle(GameConnection connection, SaveAccountSettingsRequest request) {
        if (connection.account == null) {
            log.info("Rejected save-account-settings request from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        long accountId = connection.account.id();

        var result = accountService.saveAccountSettings(accountId, request.accountSettings());

        connection.sendTCP(new SaveAccountSettingsResponse(result.success(), result.code().name()));
    }
}
