package io.github.ydhekim.crimson_sky.server.network;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.network.packet.*;
import io.github.ydhekim.crimson_sky.server.network.handler.*;
import io.github.ydhekim.crimson_sky.server.service.*;

import java.util.HashMap;
import java.util.Map;

public class KryoPacketRouter implements PacketRouter {
    private static final Logger log = new Logger("KryoPacketRouter", Logger.DEBUG);
    private final Map<Class<?>, RequestHandler<?>> handlers = new HashMap<>();

    public KryoPacketRouter(UserService userService,
                            CharacterService characterService,
                            AttackService attackService,
                            LocalizationService localizationService,
                            AchievementService achievementService,
                            AccountService accountService) {
        handlers.put(LoginRequest.class, new LoginRequestHandler(userService));
        handlers.put(CharacterListRequest.class, new CharacterListRequestHandler(characterService));
        handlers.put(CreateCharacterRequest.class, new CreateCharacterRequestHandler(characterService));
        handlers.put(DeleteCharacterRequest.class, new DeleteCharacterRequestHandler(characterService));
        handlers.put(AttackRequest.class, new AttackRequestHandler(attackService));
        handlers.put(LocalizationRequest.class, new LocalizationRequestHandler(localizationService));
        handlers.put(AchievementListRequest.class, new AchievementListRequestHandler(achievementService));
        handlers.put(SaveAccountSettingsRequest.class, new SaveAccountSettingsRequestHandler(accountService));
    }

    @Override
    public void route(GameConnection connection, Object packet) {
        // Exclude Kryo's internal keep-alive framework packets from regular logging to prevent spam
        if (packet instanceof com.esotericsoftware.kryonet.FrameworkMessage) {
            return;
        }

        log.debug("Received packet of type [" + packet.getClass().getSimpleName() + "] from Connection ID: " + connection.getID());

        @SuppressWarnings("unchecked")
        RequestHandler<Object> handler = (RequestHandler<Object>) handlers.get(packet.getClass());

        if (handler != null) {
            try {
                handler.handle(connection, packet);
            } catch (Exception e) {
                log.error("Exception occurred while handling packet [" + packet.getClass().getSimpleName() + "] from Connection ID: " + connection.getID(), e);
            }
        } else {
            log.error("No registered handler found for packet type: [" + packet.getClass().getSimpleName() + "]");
        }
    }
}
