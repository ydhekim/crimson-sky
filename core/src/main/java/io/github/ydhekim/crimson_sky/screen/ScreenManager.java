package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.ObjectMap;
import io.github.ydhekim.crimson_sky.CrimsonSky;

public class ScreenManager {
    private static ScreenManager instance;
    private Game game;
    private final ObjectMap<ScreenType, Screen> screens;

    private ScreenManager() {
        screens = new ObjectMap<>();
    }

    public static ScreenManager getInstance() {
        if (instance == null) {
            instance = new ScreenManager();
        }
        return instance;
    }

    public void initialize(Game game) {
        this.game = game;
    }

    public void showScreen(ScreenType type) {
        if (game == null) {
            throw new IllegalStateException("ScreenManager must be initialized with a Game instance before use.");
        }

        Screen screen = screens.get(type);
        if (screen == null) {
            screen = createScreen(type);
            screens.put(type, screen);
        }

        game.setScreen(screen);
    }

    private Screen createScreen(ScreenType type) {
        return switch (type) {
            case MAIN_MENU -> new MainMenuScreen((CrimsonSky) game);
            case GAME ->
                // Return new GameScreen() once implemented
                    throw new UnsupportedOperationException("GameScreen not yet implemented.");
            default -> throw new IllegalArgumentException("Unknown screen type: " + type);
        };
    }

    public void dispose() {
        for (Screen screen : screens.values()) {
            screen.dispose();
        }
        screens.clear();
    }
}
