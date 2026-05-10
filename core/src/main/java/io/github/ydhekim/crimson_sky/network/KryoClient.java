package io.github.ydhekim.crimson_sky.network;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.ydhekim.crimson_sky.common.network.KryoConfig;
import io.github.ydhekim.crimson_sky.common.network.packet.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class KryoClient implements GameClient {
    private Client client;
    private NetworkListener listener;

    // Using a Map for OCP (Open-Closed Principle) just like the server
    private final Map<Class<?>, Consumer<Object>> packetHandlers = new HashMap<>();

    public KryoClient() {
        client = new Client();
        KryoConfig.register(client.getKryo());

        setupHandlers();

        client.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                if (listener != null) {
                    // Look up the handler for this specific packet class
                    Consumer<Object> handler = packetHandlers.get(object.getClass());

                    if (handler != null) {
                        // Execute it on the main LibGDX thread
                        Gdx.app.postRunnable(() -> handler.accept(object));
                    }
                }
            }

            @Override
            public void connected(Connection connection) {
                Gdx.app.log("KryoClient", "Connected to server!");
                if (listener != null) {
                    Gdx.app.postRunnable(() -> listener.onConnected());
                }
            }

            @Override
            public void disconnected(Connection connection) {
                Gdx.app.log("KryoClient", "Disconnected from server.");
                if (listener != null) {
                    Gdx.app.postRunnable(() -> listener.onDisconnected());
                }
            }
        });
    }

    private void setupHandlers() {
        // Registering responses to methods on the active listener
        // The cast (Object -> SpecificPacket) is safe because of the Map key class
        packetHandlers.put(CharacterListResponse.class,
            packet -> listener.onCharacterListResponse((CharacterListResponse) packet));

        packetHandlers.put(CreateCharacterResponse.class,
            packet -> listener.onCreateCharacterResponse((CreateCharacterResponse) packet));

        packetHandlers.put(DeleteCharacterResponse.class,
            packet -> listener.onDeleteCharacterResponse((DeleteCharacterResponse) packet));
    }

    @Override
    public void connect(String host, int tcpPort, int udpPort) {
        client.start();
        new Thread(() -> {
            try {
                client.connect(5000, host, tcpPort, udpPort);
            } catch (IOException e) {
                Gdx.app.error("KryoClient", "Failed to connect to server: " + e.getMessage(), e);
            }
        }).start();
    }

    @Override
    public void setListener(NetworkListener listener) {
        this.listener = listener;
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
