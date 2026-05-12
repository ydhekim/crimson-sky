package io.github.ydhekim.crimson_sky.server.network;

public interface PacketRouter {
    void route(GameConnection connection, Object packet);
}
