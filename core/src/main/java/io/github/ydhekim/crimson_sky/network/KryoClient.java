package io.github.ydhekim.crimson_sky.network;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.ydhekim.crimson_sky.common.network.KryoConfig;
import io.github.ydhekim.crimson_sky.common.network.packet.*;
import io.github.ydhekim.crimson_sky.util.LanguageManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class KryoClient implements GameClient {
    private final Client client;
    private NetworkListener listener;
    private LanguageManager languageManager;

    private final Map<Class<?>, Consumer<Object>> packetHandlers = new HashMap<>();

    public KryoClient() {
        client = new Client();
        KryoConfig.register(client.getKryo());

        setupHandlers();

        client.addListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof LocalizationResponse) {
                    LocalizationResponse response = (LocalizationResponse) object;
                    Gdx.app.log("KryoClient", "Localization response intercepted.");

                    Gdx.app.postRunnable(() -> {
                        if (response.success() && languageManager != null) {
                            languageManager.setTranslations(response.translations());
                            Gdx.app.log("KryoClient", "LanguageManager updated. Keys: " + response.translations().size());
                        }

                        if (listener != null) {
                            listener.onLocalizationResponse(response);
                            Gdx.app.log("KryoClient", "UI Refresh triggered via Listener.");
                        }
                    });
                    return;
                }

                if (listener != null) {
                    Consumer<Object> handler = packetHandlers.get(object.getClass());
                    if (handler != null) {
                        Gdx.app.postRunnable(() -> handler.accept(object));
                    }
                }
            }

            @Override
            public void connected(Connection connection) {
                Gdx.app.log("KryoClient", "Connection established.");
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

        client.start(); // Start the client thread once and for all.
    }

    private void setupHandlers() {
        packetHandlers.put(LoginResponse.class,
            packet -> listener.onLoginResponse((LoginResponse) packet));
        packetHandlers.put(CharacterListResponse.class,
            packet -> listener.onCharacterListResponse((CharacterListResponse) packet));
        packetHandlers.put(CreateCharacterResponse.class,
            packet -> listener.onCreateCharacterResponse((CreateCharacterResponse) packet));
        packetHandlers.put(DeleteCharacterResponse.class,
            packet -> listener.onDeleteCharacterResponse((DeleteCharacterResponse) packet));
        packetHandlers.put(AchievementListResponse.class,
            packet -> listener.onAchievementListResponse((AchievementListResponse) packet));
    }

    @Override
    public void connect(String host, int tcpPort, int udpPort) {
        if (client.isConnected()) return;
        new Thread(() -> {
            try {
                client.connect(5000, host, tcpPort, udpPort);
            } catch (IOException e) {
                Gdx.app.error("KryoClient", "Failed to connect to server: " + e.getMessage(), e);
            }
        }, "KryoClient-Connect").start();
    }

    @Override
    public void setListener(NetworkListener listener) {
        this.listener = listener;
    }

    @Override
    public void setLanguageManager(LanguageManager languageManager) {
        this.languageManager = languageManager;
    }

    @Override
    public void sendTCP(Object packet) {
        if (client.isConnected()) {
            client.sendTCP(packet);
            if (packet instanceof LocalizationRequest) {
                 Gdx.app.log("KryoClient", "Localization request sent.");
            }
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
