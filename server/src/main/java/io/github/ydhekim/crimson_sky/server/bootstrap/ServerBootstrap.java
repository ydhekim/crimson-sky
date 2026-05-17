package io.github.ydhekim.crimson_sky.server.bootstrap;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.server.database.DatabaseManager;
import io.github.ydhekim.crimson_sky.server.network.GameServer;
import io.github.ydhekim.crimson_sky.server.network.PacketRouter;
import io.github.ydhekim.crimson_sky.server.network.factory.NetworkServerFactory;
import io.github.ydhekim.crimson_sky.server.network.factory.PacketRouterFactory;
import io.github.ydhekim.crimson_sky.server.service.ServiceRegistry;

/**
 * ServerBootstrap is responsible for composing and managing the lifecycle of server components.
 *
 * It applies Single Responsibility Principle by centralizing all composition logic.
 * It applies Dependency Inversion by depending on factory abstractions rather than concrete implementations.
 * It provides clean error handling and resource cleanup on shutdown.
 */
public class ServerBootstrap {
    private static final Logger log = new Logger("ServerBootstrap", Logger.DEBUG);

    private final DatabaseManager dbManager;
    private final PacketRouterFactory packetRouterFactory;
    private final NetworkServerFactory networkFactory;
    private final int tcpPort;
    private final int udpPort;

    private GameServer server;

    public ServerBootstrap(DatabaseManager dbManager,
                           PacketRouterFactory packetRouterFactory,
                           NetworkServerFactory networkFactory,
                           int tcpPort,
                           int udpPort) {
        this.dbManager = dbManager;
        this.packetRouterFactory = packetRouterFactory;
        this.networkFactory = networkFactory;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
    }

    /**
     * Starts the server by composing all components and registering a shutdown hook.
     *
     * @throws Exception if server startup fails
     */
    public void start() throws Exception {
        log.info("Bootstrapping server components...");

        ServiceRegistry serviceRegistry = new ServiceRegistry(dbManager);
        PacketRouter packetRouter = packetRouterFactory.create(serviceRegistry);

        server = networkFactory.create(packetRouter);
        server.start(tcpPort, udpPort);

        // Register shutdown hook for graceful termination with exception safety
        final DatabaseManager finalDb = this.dbManager;
        final GameServer finalServer = this.server;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received. Stopping server...");
            try {
                if (finalServer != null) {
                    finalServer.stop();
                }
            } catch (Exception e) {
                log.error("Exception while stopping server", e);
            }
            try {
                if (finalDb != null) {
                    finalDb.close();
                }
            } catch (Exception e) {
                log.error("Exception while closing database manager", e);
            }
            log.info("Server shutdown complete.");
        }));

        log.info("Server started successfully.");
    }

    /**
     * Stops the server programmatically (e.g., from a management endpoint).
     */
    public void stop() {
        log.info("Stopping server from bootstrap...");
        try {
            if (server != null) {
                server.stop();
            }
        } catch (Exception e) {
            log.error("Exception while stopping server", e);
        }
        try {
            if (dbManager != null) {
                dbManager.close();
            }
        } catch (Exception e) {
            log.error("Exception during DB close", e);
        }
    }
}

