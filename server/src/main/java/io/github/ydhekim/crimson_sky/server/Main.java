package io.github.ydhekim.crimson_sky.server;

import io.github.ydhekim.crimson_sky.server.database.DatabaseManager;
import io.github.ydhekim.crimson_sky.server.network.GameServer;
import io.github.ydhekim.crimson_sky.server.network.KryoPacketRouter;
import io.github.ydhekim.crimson_sky.server.network.KryoServer;
import io.github.ydhekim.crimson_sky.server.network.PacketRouter;
import io.github.ydhekim.crimson_sky.server.service.ServiceRegistry;

public class Main {
    private static final int TCP_PORT = 54555;
    private static final int UDP_PORT = 54777;

    public static void main(String[] args) {
        DatabaseManager dbManager = DatabaseManager.getInstance();
        ServiceRegistry serviceRegistry = new ServiceRegistry(dbManager);
        PacketRouter packetRouter = new KryoPacketRouter(
            serviceRegistry.getUserService(),
            serviceRegistry.getCharacterService()
        );

        try {
            GameServer server = new KryoServer(packetRouter);
            server.start(TCP_PORT, UDP_PORT);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                dbManager.close();
            }));

        } catch (Exception e) {
            System.err.println("Server failed to start: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
