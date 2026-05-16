package io.github.ydhekim.crimson_sky.server.network;

import io.github.ydhekim.crimson_sky.common.network.packet.*;
import io.github.ydhekim.crimson_sky.server.network.handler.*;
import io.github.ydhekim.crimson_sky.server.service.CharacterService;
import io.github.ydhekim.crimson_sky.server.service.LocalizationService;
import io.github.ydhekim.crimson_sky.server.service.UserService;

import java.util.HashMap;
import java.util.Map;

public class KryoPacketRouter implements PacketRouter {
    private final Map<Class<?>, RequestHandler<?>> handlers = new HashMap<>();

    public KryoPacketRouter(UserService userService, CharacterService characterService, LocalizationService localizationService) {
        handlers.put(LoginRequest.class, new LoginRequestHandler(userService));
        handlers.put(CharacterListRequest.class, new CharacterListRequestHandler(characterService));
        handlers.put(CreateCharacterRequest.class, new CreateCharacterRequestHandler(characterService));
        handlers.put(DeleteCharacterRequest.class, new DeleteCharacterRequestHandler(characterService));
        handlers.put(LocalizationRequest.class, new LocalizationRequestHandler(localizationService));
    }

    @Override
    public void route(GameConnection connection, Object packet) {
        @SuppressWarnings("unchecked")
        RequestHandler<Object> handler = (RequestHandler<Object>) handlers.get(packet.getClass());

        if (handler != null) {
            handler.handle(connection, packet);
        }
    }
}
