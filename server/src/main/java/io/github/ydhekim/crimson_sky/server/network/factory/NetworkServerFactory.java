package io.github.ydhekim.crimson_sky.server.network.factory;

import io.github.ydhekim.crimson_sky.server.network.GameServer;
import io.github.ydhekim.crimson_sky.server.network.PacketRouter;

/**
 * Factory abstraction for creating GameServer instances.
 * Enables dependency inversion and decouples from concrete KryoNet implementation.
 */
public interface NetworkServerFactory {
    GameServer create(PacketRouter packetRouter);
}

