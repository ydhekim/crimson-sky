package io.github.ydhekim.crimson_sky.server.network.factory;

import io.github.ydhekim.crimson_sky.server.network.KryoPacketRouter;
import io.github.ydhekim.crimson_sky.server.network.PacketRouter;
import io.github.ydhekim.crimson_sky.server.service.ServiceRegistry;

/**
 * Kryo-based implementation of PacketRouterFactory.
 * Creates a KryoPacketRouter that handles routing of network packets to service handlers.
 */
public class KryoPacketRouterFactory implements PacketRouterFactory {
    @Override
    public PacketRouter create(ServiceRegistry serviceRegistry) {
        return new KryoPacketRouter(
                serviceRegistry.getUserService(),
                serviceRegistry.getCharacterService(),
                serviceRegistry.getLocalizationService(),
                serviceRegistry.getAchievementService());
    }
}

