package io.github.ydhekim.crimson_sky.server.network;

import com.badlogic.gdx.utils.Logger;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import io.github.ydhekim.crimson_sky.common.network.KryoConfig;

import java.io.IOException;

public class KryoServer implements GameServer {
    private static final Logger log = new Logger("KryoServer", Logger.DEBUG);
    private Server server;
    private final PacketRouter packetRouter;

    public KryoServer(PacketRouter packetRouter) {
        this.packetRouter = packetRouter;
    }

    @Override
    public void start(int tcpPort, int udpPort) throws IOException {
        log.info("Initializing KryoServer...");
        server = new Server() {
            protected Connection newConnection() {
                return new GameConnection();
            }
        };

        KryoConfig.register(server.getKryo());
        setupListeners();

        try {
            server.start();
            server.bind(tcpPort, udpPort);
            log.info("KryoServer successfully started and bound to TCP: " + tcpPort + ", UDP: " + udpPort);
        } catch (IOException e) {
            log.error("Failed to bind KryoServer to ports TCP: " + tcpPort + ", UDP: " + udpPort, e);
            throw e;
        }
    }

    private void setupListeners() {
        server.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                GameConnection gameConn = (GameConnection) connection;
                packetRouter.route(gameConn, object);
            }

            @Override
            public void connected(Connection connection) {
                log.info("Client connected. Connection ID: " + connection.getID() + ", Remote Address: " + connection.getRemoteAddressTCP());
            }

            @Override
            public void disconnected(Connection connection) {
                log.info("Client disconnected. Connection ID: " + connection.getID());
            }
        });
    }

    @Override
    public void stop() {
        if (server != null) {
            log.info("Stopping KryoServer...");
            server.stop();
            log.info("KryoServer stopped.");
        }
    }
}
