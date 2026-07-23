package io.github.ydhekim.crimson_sky.network;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.ydhekim.crimson_sky.common.network.KryoConfig;
import io.github.ydhekim.crimson_sky.util.LanguageManager;

import java.io.IOException;

/**
 * KryoNet-based implementation of GameClient.
 * Refactored to use PacketHandlerRegistry for cleaner packet dispatch.
 * Applies Strategy Pattern and Single Responsibility Principle.
 */
public class KryoClient implements GameClient {
    private final Client client;
    private NetworkListener listener;
    private LanguageManager languageManager;
    private PacketHandlerRegistry handlerRegistry;

    public KryoClient() {
        client = new Client(KryoConfig.WRITE_BUFFER_SIZE, KryoConfig.OBJECT_BUFFER_SIZE);
        KryoConfig.register(client.getKryo());

        // Add listener for connection events
        client.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                KryoClient.this.handlePacketReceived(object);
            }

            @Override
            public void connected(Connection connection) {
                Gdx.app.log("KryoClient", "Connection established.");
                if (listener != null) {
                    Gdx.app.postRunnable(listener::onConnected);
                }
            }

            @Override
            public void disconnected(Connection connection) {
                Gdx.app.log("KryoClient", "Disconnected from server.");
                if (listener != null) {
                    Gdx.app.postRunnable(listener::onDisconnected);
                }
            }
        });

        client.start();
    }

    /**
     * Handles received packets by dispatching to the handler registry.
     */
    private void handlePacketReceived(Object object) {
        if (handlerRegistry != null) {
            handlerRegistry.dispatch(object);
        }
    }

    /**
     * A cold-connect failure notifies the listener through the same {@code onDisconnected()} the
     * {@link Listener#disconnected(Connection)} callback above uses, so "couldn't connect at all"
     * and "connected then dropped" land on the one state screens already handle. Deliberately not a
     * distinct state/message key — DISCONNECTED's existing copy is close enough for both.
     */
    @Override
    public void connect(String host, int tcpPort, int udpPort) {
        if (client.isConnected()) return;
        new Thread(() -> {
            try {
                client.connect(5000, host, tcpPort, udpPort);
            } catch (IOException e) {
                Gdx.app.error("KryoClient", "Failed to connect to server: " + e.getMessage(), e);
                if (listener != null) {
                    Gdx.app.postRunnable(listener::onDisconnected);
                }
            }
        }, "KryoClient-Connect").start();
    }

    @Override
    public void setListener(NetworkListener listener) {
        this.listener = listener;
        // Initialize handler registry when listener is set
        if (this.listener != null) {
            this.handlerRegistry = new PacketHandlerRegistry(this.listener, this.languageManager);
        }
    }

    @Override
    public void setLanguageManager(LanguageManager languageManager) {
        this.languageManager = languageManager;
    }

    @Override
    public void sendTCP(Object packet) {
        if (client.isConnected()) {
            client.sendTCP(packet);
        } else {
            Gdx.app.error("KryoClient", "Cannot send TCP packet, client is not connected.");
        }
    }

    @Override
    public void sendUDP(Object packet) {
        if (client.isConnected()) {
            client.sendUDP(packet);
        } else {
            Gdx.app.error("KryoClient", "Cannot send UDP packet, client is not connected.");
        }
    }

    @Override
    public void disconnect() {
        if (client != null) {
            client.stop();
        }
    }
}
