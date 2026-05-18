package io.github.ydhekim.crimson_sky.network;

import com.badlogic.gdx.Gdx;
import io.github.ydhekim.crimson_sky.common.network.packet.*;
import io.github.ydhekim.crimson_sky.util.LanguageManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Manages packet handlers for network responses.
 * Applies Strategy Pattern to delegate packet handling to registered handlers.
 * Centralizes handler registration and dispatch logic, following SRP.
 */
public class PacketHandlerRegistry {
    private final Map<Class<?>, Consumer<Object>> handlers = new HashMap<>();
    private final NetworkListener listener;
    private final LanguageManager languageManager;

    public PacketHandlerRegistry(NetworkListener listener, LanguageManager languageManager) {
        this.listener = listener;
        this.languageManager = languageManager;
        registerDefaultHandlers();
    }

    /**
     * Registers all default packet handlers.
     */
    private void registerDefaultHandlers() {
        register(LoginResponse.class,
                packet -> listener.onLoginResponse((LoginResponse) packet));
        register(CharacterListResponse.class,
                packet -> listener.onCharacterListResponse((CharacterListResponse) packet));
        register(CreateCharacterResponse.class,
                packet -> listener.onCreateCharacterResponse((CreateCharacterResponse) packet));
        register(DeleteCharacterResponse.class,
                packet -> listener.onDeleteCharacterResponse((DeleteCharacterResponse) packet));
        register(AchievementListResponse.class,
                packet -> listener.onAchievementListResponse((AchievementListResponse) packet));
        register(LocalizationResponse.class,
                packet -> handleLocalizationResponse((LocalizationResponse) packet));
    }

    /**
     * Registers a custom packet handler.
     *
     * @param packetType the class of the packet to handle
     * @param handler    the handler function
     */
    public <T> void register(Class<T> packetType, Consumer<T> handler) {
        handlers.put(packetType, (Consumer<Object>) handler);
    }

    /**
     * Dispatches a received packet to its registered handler (if any).
     *
     * @param packet the received packet
     */
    @SuppressWarnings("unchecked")
    public void dispatch(Object packet) {
        if (packet == null) {
            return;
        }

        Consumer<Object> handler = handlers.get(packet.getClass());
        if (handler != null) {
            Gdx.app.postRunnable(() -> handler.accept(packet));
        } else {
            Gdx.app.log("PacketHandlerRegistry", "No handler registered for packet type: " + packet.getClass().getSimpleName());
        }
    }

    /**
     * Handles LocalizationResponse packets:
     * - Updates the LanguageManager with received translations
     * - Delegates to listener for UI refresh
     */
    private void handleLocalizationResponse(LocalizationResponse response) {
        Gdx.app.log("PacketHandlerRegistry", "Localization response received.");

        if (response.success() && languageManager != null) {
            languageManager.setTranslations(response.translations());
            Gdx.app.log("PacketHandlerRegistry", "LanguageManager updated with " + response.translations().size() + " keys.");
        }

        if (listener != null) {
            listener.onLocalizationResponse(response);
            Gdx.app.log("PacketHandlerRegistry", "UI refresh triggered via listener.");
        }
    }
}

