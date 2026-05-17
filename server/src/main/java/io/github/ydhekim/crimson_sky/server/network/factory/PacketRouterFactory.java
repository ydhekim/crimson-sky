package io.github.ydhekim.crimson_sky.server.network.factory;

import io.github.ydhekim.crimson_sky.server.network.PacketRouter;
import io.github.ydhekim.crimson_sky.server.service.ServiceRegistry;

/**
 * Factory abstraction for creating PacketRouter instances.
 * Enables dependency inversion and makes it easier to swap implementations.
 */
public interface PacketRouterFactory {
    PacketRouter create(ServiceRegistry serviceRegistry);
}

