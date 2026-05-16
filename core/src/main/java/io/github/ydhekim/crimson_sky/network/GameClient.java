package io.github.ydhekim.crimson_sky.network;

import io.github.ydhekim.crimson_sky.util.LanguageManager;

public interface GameClient {
    void connect(String host, int tcpPort, int udpPort);
    void setListener(NetworkListener listener);
    void setLanguageManager(LanguageManager languageManager);
    void sendTCP(Object packet);
    void sendUDP(Object packet);
    void disconnect();
}
