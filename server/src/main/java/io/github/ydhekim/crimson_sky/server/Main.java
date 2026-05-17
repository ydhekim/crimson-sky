package io.github.ydhekim.crimson_sky.server;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.server.database.DatabaseManager;
import io.github.ydhekim.crimson_sky.server.network.GameServer;
import io.github.ydhekim.crimson_sky.server.network.KryoPacketRouter;
import io.github.ydhekim.crimson_sky.server.network.KryoServer;
import io.github.ydhekim.crimson_sky.server.network.PacketRouter;
import io.github.ydhekim.crimson_sky.server.service.ServiceRegistry;

public class Main {
    private static Logger log;
    private static final int TCP_PORT = 54555;
    private static final int UDP_PORT = 54777;

    public static void main(String[] args) {
        // Initialize Headless LibGDX environment to enable Gdx.app and Logger
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        new HeadlessApplication(new ApplicationAdapter() {
            @Override
            public void create() {
                // Initialize logger after Gdx is set up
                log = new Logger("Main", Logger.DEBUG);
                Gdx.app.setLogLevel(com.badlogic.gdx.Application.LOG_DEBUG);
                startServer();
            }
        }, config);
    }

    private static void startServer() {
        log.info("Starting Crimson Sky Server...");

        DatabaseManager dbManager = null;
        try {
            dbManager = DatabaseManager.getInstance();
            log.info("Database connection established.");
        } catch (Exception e) {
             log.error("Critical failure during database initialization. Server cannot start.", e);
             System.exit(1);
        }

        ServiceRegistry serviceRegistry = new ServiceRegistry(dbManager);
        PacketRouter packetRouter = new KryoPacketRouter(
            serviceRegistry.getUserService(),
            serviceRegistry.getCharacterService(),
            serviceRegistry.getLocalizationService(),
            serviceRegistry.getAchievementService()
        );

        try {
            GameServer server = new KryoServer(packetRouter);
            server.start(TCP_PORT, UDP_PORT);

            final DatabaseManager finalDbManager = dbManager;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown signal received. Stopping server...");
                server.stop();
                finalDbManager.close();
                log.info("Server shutdown complete.");
            }));

        } catch (Exception e) {
            log.error("Server failed to start", e);
            System.exit(1);
        }
    }
}
