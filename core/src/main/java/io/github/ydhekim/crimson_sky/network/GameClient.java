package io.github.ydhekim.crimson_sky.network;

public interface GameClient {
    void connect(String host, int tcpPort, int udpPort);
    void setListener(NetworkListener listener);
    void sendTCP(Object packet);
    void sendUDP(Object packet);
    void disconnect();
}
