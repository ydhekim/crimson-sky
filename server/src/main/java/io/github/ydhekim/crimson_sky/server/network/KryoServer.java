package io.github.ydhekim.crimson_sky.server.network;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import io.github.ydhekim.crimson_sky.common.network.KryoConfig;
import io.github.ydhekim.crimson_sky.common.network.packet.*;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.database.dao.UserDao;
import io.github.ydhekim.crimson_sky.server.network.handler.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class KryoServer implements GameServer {
    private Server server;

    // Command Pattern / Strategy Pattern for dynamic request handling
    private final Map<Class<?>, RequestHandler<?>> handlers = new HashMap<>();

    public KryoServer(UserDao userDao, CharacterDao characterDao) {
        // Registering handlers
        handlers.put(LoginRequest.class, new LoginRequestHandler(userDao));
        handlers.put(SignUpRequest.class, new SignUpRequestHandler(userDao));
        handlers.put(CharacterListRequest.class, new CharacterListRequestHandler(characterDao));
        handlers.put(CreateCharacterRequest.class, new CreateCharacterRequestHandler(characterDao));
        handlers.put(DeleteCharacterRequest.class, new DeleteCharacterRequestHandler(characterDao));
    }

    @Override
    public void start(int tcpPort, int udpPort) throws IOException {
        server = new Server() {
            protected Connection newConnection() {
                return new GameConnection();
            }
        };

        KryoConfig.register(server.getKryo());
        setupListeners();

        server.start();
        server.bind(tcpPort, udpPort);
        System.out.println("Server started on TCP: " + tcpPort + ", UDP: " + udpPort);
    }

    private void setupListeners() {
        server.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                GameConnection gameConn = (GameConnection) connection;

                // OCP (Open-Closed Principle): We don't need a massive if/else chain anymore.
                @SuppressWarnings("unchecked")
                RequestHandler<Object> handler = (RequestHandler<Object>) handlers.get(object.getClass());

                if (handler != null) {
                    handler.handle(gameConn, object);
                }
            }

            @Override
            public void connected(Connection connection) {
                System.out.println("Client connected: " + connection.getRemoteAddressTCP());
            }

            @Override
            public void disconnected(Connection connection) {
                System.out.println("Client disconnected: " + connection.getRemoteAddressTCP());
            }
        });
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
