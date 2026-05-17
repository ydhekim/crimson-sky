package io.github.ydhekim.crimson_sky.server;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.server.bootstrap.ServerBootstrap;
import io.github.ydhekim.crimson_sky.server.database.DatabaseManager;
import io.github.ydhekim.crimson_sky.server.network.factory.KryoNetworkServerFactory;
import io.github.ydhekim.crimson_sky.server.network.factory.KryoPacketRouterFactory;
import io.github.ydhekim.crimson_sky.server.network.factory.NetworkServerFactory;
import io.github.ydhekim.crimson_sky.server.network.factory.PacketRouterFactory;

/**
 * Entry point for the Crimson Sky game server.
 * Initializes the headless LibGDX environment and delegates server composition/lifecycle to ServerBootstrap.
 *
 * This class is intentionally small and focuses only on:
 * 1. Initializing the headless LibGDX environment
 * 2. Initializing the database manager
 * 3. Creating factories for components
 * 4. Delegating to ServerBootstrap for all composition and lifecycle management
 *
 * This design follows Single Responsibility Principle and Dependency Inversion Principle.
 */
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
                log = new Logger("Main", Logger.DEBUG);
                Gdx.app.setLogLevel(com.badlogic.gdx.Application.LOG_DEBUG);
                startServer();
            }
        }, config);
    }

    private static void startServer() {
        log.info("Starting Crimson Sky Server...");

        DatabaseManager dbManager;
        try {
            dbManager = DatabaseManager.getInstance();
            log.info("Database connection established.");
        } catch (Exception e) {
            log.error("Critical failure during database initialization. Server cannot start.", e);
            System.exit(1);
            return;
        }

        // Create factories (abstractions) instead of directly instantiating server components
        PacketRouterFactory packetRouterFactory = new KryoPacketRouterFactory();
        NetworkServerFactory networkFactory = new KryoNetworkServerFactory();

        // Delegate composition and lifecycle to ServerBootstrap
        ServerBootstrap bootstrap = new ServerBootstrap(
                dbManager,
                packetRouterFactory,
                networkFactory,
                TCP_PORT,
                UDP_PORT);

        try {
            bootstrap.start();
        } catch (Exception e) {
            log.error("Server failed to start", e);
            System.exit(1);
        }
    }
}
