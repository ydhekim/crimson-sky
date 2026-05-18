package io.github.ydhekim.crimson_sky.screen.factory;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.utils.ObjectMap;
import io.github.ydhekim.crimson_sky.screen.ScreenType;

/**
 * Manages screen navigation and caching.
 * Uses dependency injection (as opposed to singleton ScreenManager).
 * Applies Factory Pattern for screen creation and Repository Pattern for caching.
 */
public class ScreenRouter {
    private final Game game;
    private final ScreenFactory screenFactory;
    private final ObjectMap<ScreenType, Screen> screenCache;

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
        Screen screen = screenCache.get(type);
        if (screen == null) {
            screen = screenFactory.createScreen(type);
            screenCache.put(type, screen);
        }
        game.setScreen(screen);
    }

    /**
     * Gets a cached screen without navigating to it.
     *
     * @param type the screen type
     * @return     the cached screen, or null if not created yet
     */
    public Screen getScreen(ScreenType type) {
        return screenCache.get(type);
    }

    /**
     * Clears a cached screen by type.
     * The next navigation to this type will create a new instance.
     *
     * @param type the screen type to clear
     */
    public void clearScreen(ScreenType type) {
        Screen screen = screenCache.remove(type);
        if (screen != null) {
            screen.dispose();
        }
    }

    /**
     * Disposes of all cached screens.
     * Must be called during game shutdown.
     */
    public void dispose() {
        for (Screen screen : screenCache.values()) {
            screen.dispose();
        }
        screenCache.clear();
    }
}

