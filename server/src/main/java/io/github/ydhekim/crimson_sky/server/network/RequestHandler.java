package io.github.ydhekim.crimson_sky.server.network;

public interface RequestHandler<T> {
    void handle(GameConnection connection, T request);
}
