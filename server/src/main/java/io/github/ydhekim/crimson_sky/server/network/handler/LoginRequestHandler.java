package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.network.packet.LoginRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginResponse;
import io.github.ydhekim.crimson_sky.server.database.dao.UserDao;
import io.github.ydhekim.crimson_sky.server.database.entity.User;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;

public class LoginRequestHandler implements RequestHandler<LoginRequest> {

    private final UserDao userDao;

    public LoginRequestHandler(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public void handle(GameConnection connection, LoginRequest request) {
        System.out.println("Received login request for user: " + request.username);

        User user = userDao.getUserByUsername(request.username);
        boolean success = false;
        if (user != null) {
            success = userDao.verifyPassword(request.username, request.password);
        }

        if (success) {
            connection.user = user;
        }

        LoginResponse response = new LoginResponse();
        response.success = success;
        response.message = success ? "Login successful" : "Invalid username or password";

        connection.sendTCP(response);
    }
}
