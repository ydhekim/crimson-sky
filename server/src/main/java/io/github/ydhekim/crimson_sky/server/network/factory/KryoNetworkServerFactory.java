package io.github.ydhekim.crimson_sky.server.network.factory;

import io.github.ydhekim.crimson_sky.server.network.GameServer;
import io.github.ydhekim.crimson_sky.server.network.KryoServer;
import io.github.ydhekim.crimson_sky.server.network.PacketRouter;

/**
 * Kryo-based implementation of NetworkServerFactory.
 * Creates a KryoServer that handles network communication via KryoNet.
 */
public class KryoNetworkServerFactory implements NetworkServerFactory {
    @Override
    public GameServer create(PacketRouter packetRouter) {
        return new KryoServer(packetRouter);
    }
}

