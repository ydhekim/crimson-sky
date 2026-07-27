package io.github.ydhekim.crimson_sky.screen.factory;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.utils.ObjectMap;
import io.github.ydhekim.crimson_sky.screen.BaseScreen;
import io.github.ydhekim.crimson_sky.screen.ScreenType;

/**
 * Manages screen navigation and caching.
 * Uses dependency injection (as opposed to singleton ScreenManager).
 * Applies Factory Pattern for screen creation and Repository Pattern for caching.
 */
public class ScreenRouter {
    private final Game game;
    private final ScreenFactory screenFactory;
    private final ObjectMap<ScreenType, BaseScreen> screenCache;

    public ScreenRouter(Game game, ScreenFactory screenFactory) {
        this.game = game;
        this.screenFactory = screenFactory;
        this.screenCache = new ObjectMap<>();
    }

    /**
     * Navigates to a screen of the specified type.
     * Creates and caches the screen if it doesn't exist.
     *
     * @param type the screen type to navigate to
     */
    public void navigateTo(ScreenType type) {
        BaseScreen screen = screenCache.get(type);
        if (screen == null) {
            screen = screenFactory.createScreen(type);
            screenCache.put(type, screen);
            game.setScreen(screen);
        } else {
            // Reused cached instance — session-global state (translations, most concretely) may have
            // changed while this screen wasn't active. A screen only rebuilds on a LocalizationResponse
            // while it is the registered NetworkListener, which BaseScreen.show()/hide() wires on and off,
            // so a language change made elsewhere never reached this instance and its actor tree is still
            // whatever it was on the last visit. Refreshing after setScreen (not before) matters: show()
            // registers the listener first, so a refresh that re-fetches data gets its response back.
            game.setScreen(screen);
            screen.refreshUI();
        }
    }

    /**
     * Gets a cached screen without navigating to it.
     *
     * @param type the screen type
     * @return     the cached screen, or null if not created yet
     */
    public BaseScreen getScreen(ScreenType type) {
        return screenCache.get(type);
    }

    /**
     * Clears a cached screen by type.
     * The next navigation to this type will create a new instance.
     *
     * @param type the screen type to clear
     */
    public void clearScreen(ScreenType type) {
        BaseScreen screen = screenCache.remove(type);
        if (screen != null) {
            screen.dispose();
        }
    }

    /**
     * Disposes of all cached screens.
     * Must be called during game shutdown.
     */
    public void dispose() {
        for (BaseScreen screen : screenCache.values()) {
            screen.dispose();
        }
        screenCache.clear();
    }
}
