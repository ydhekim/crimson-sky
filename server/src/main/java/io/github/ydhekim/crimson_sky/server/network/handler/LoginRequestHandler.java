package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.PlatformType;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginResponse;
import io.github.ydhekim.crimson_sky.server.database.entity.Account;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.ServiceResult;
import io.github.ydhekim.crimson_sky.server.service.UserService;

public class LoginRequestHandler implements RequestHandler<LoginRequest> {
    private static final Logger log = new Logger("LoginRequestHandler", Logger.DEBUG);
    private final UserService userService;

    public LoginRequestHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(GameConnection connection, LoginRequest request) {
        log.info("Received login request from Connection ID: " + connection.getID() + " for platform: " + request.platformType());

        if (request.platformType() == PlatformType.TEST) {
            handleTestLogin(connection, request);
        } else {
            log.info("Rejected login request from Connection ID: " + connection.getID() + ". Platform " + request.platformType() + " not yet supported.");
            connection.sendTCP(new LoginResponse(false, "Platform " + request.platformType() + " not yet supported.", 0, 0));
        }
    }

    private void handleTestLogin(GameConnection connection, LoginRequest request) {
        ServiceResult<Account> result = userService.loginTestUser(request.platformType(), request.identityToken());

        if (result.success()) {
            Account account = result.data();
            // CRITICAL: Bind the account to the network session
            connection.account = account;
            log.info("Login successful for Connection ID: " + connection.getID() + ". Bound to Account ID: " + account.id());
            connection.sendTCP(new LoginResponse(true, "Login successful", account.id(), account.maxSlots()));
        } else {
            log.info("Login failed for Connection ID: " + connection.getID() + ". Reason: " + result.code().name());
            connection.sendTCP(new LoginResponse(false, result.code().name(), 0, 0));
        }
    }
}
