package io.github.ydhekim.crimson_sky.server;

import io.github.ydhekim.crimson_sky.server.database.DatabaseManager;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import io.github.ydhekim.crimson_sky.server.database.dao.UserDao;
import io.github.ydhekim.crimson_sky.server.network.GameServer;
import io.github.ydhekim.crimson_sky.server.network.KryoServer;

public class Main {
    private static final int TCP_PORT = 54555;
    private static final int UDP_PORT = 54777;

    public static void main(String[] args) {
        System.out.println("Starting Crimson Sky Server...");

        DatabaseManager dbManager = DatabaseManager.getInstance();
        System.out.println("Database pool configured.");

        // Dependency Injection Setup
        UserDao userDao = new UserDao();
        CharacterDao characterDao = new CharacterDao();

        // Start Network Server
        try {
            // Inject dependencies into the server
            GameServer server = new KryoServer(userDao, characterDao);
            server.start(TCP_PORT, UDP_PORT);

            // Keep the server running
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
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
