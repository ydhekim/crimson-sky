package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.model.PlatformType;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginResponse;
import io.github.ydhekim.crimson_sky.server.database.entity.Account;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;
import io.github.ydhekim.crimson_sky.server.service.UserService;

public class LoginRequestHandler implements RequestHandler<LoginRequest> {

    private final UserService userService;

    public LoginRequestHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(GameConnection connection, LoginRequest request) {
        System.out.println("Received login request for platform: " + request.platformType + " with token: " + request.identityToken);

        if (request.platformType == PlatformType.TEST) {
            handleTestLogin(connection, request);
        } else {
            // TODO: Implement real OAuth validation for APPLE, GOOGLE, STEAM
            connection.sendTCP(new LoginResponse(false, "Platform " + request.platformType + " not yet supported.", 0, 0));
        }
    }

    private void handleTestLogin(GameConnection connection, LoginRequest request) {
        ServiceResult<Account> result = userService.loginTestUser(request.platformType, request.identityToken);

        if (result.success()) {
            Account account = result.data();
            // CRITICAL: Bind the account to the network session
            connection.account = account;
            connection.sendTCP(new LoginResponse(true, "Login successful", account.id(), account.maxSlots()));
        } else {
            connection.sendTCP(new LoginResponse(false, result.code().name(), 0, 0));
        }
    }
}
