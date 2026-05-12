package io.github.ydhekim.crimson_sky.server.network;

public interface GameServer {
    void start(int tcpPort, int udpPort) throws Exception;

    void stop();
}
