package io.github.ydhekim.crimson_sky.server.network.handler;

import io.github.ydhekim.crimson_sky.common.network.packet.SignUpRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.SignUpResponse;
import io.github.ydhekim.crimson_sky.server.database.dao.UserDao;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;

public class SignUpRequestHandler implements RequestHandler<SignUpRequest> {

    private final UserDao userDao;

    public SignUpRequestHandler(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public void handle(GameConnection connection, SignUpRequest request) {
        System.out.println("Received sign up request for user: " + request.username);

        boolean success = userDao.createUser(request.username, request.password);

        SignUpResponse response = new SignUpResponse();
        response.success = success;
        response.message = success ? "Sign up successful" : "Username already exists or error occurred";

        connection.sendTCP(response);
    }
}
