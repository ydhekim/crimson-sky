package io.github.ydhekim.crimson_sky.screen.factory;

import com.badlogic.gdx.Screen;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.screen.*;

/**
 * Factory for creating Screen instances.
 * Applies Factory Pattern to decouple screen creation from navigation code.
 * Centralizes all screen instantiation logic.
 */
public class ScreenFactory {
    private final CrimsonSky game;

    public ScreenFactory(CrimsonSky game) {
        this.game = game;
    }

    /**
     * Creates a screen of the specified type.
     *
     * @param type the screen type to create
     * @return     the created screen instance
     * @throws     IllegalArgumentException if screen type is unknown
     */
    public Screen createScreen(ScreenType type) {
        return switch (type) {
            case MAIN_MENU -> new MainMenuScreen(game);
            case CHARACTERS -> new CharactersScreen(game);
            case CHARACTER_CREATION -> new CharacterCreationScreen(game);
            case ACHIEVEMENTS -> new AchievementsScreen(game);
            case SETTINGS -> new SettingsScreen(game);
            case GAME -> throw new UnsupportedOperationException("GameScreen not yet implemented.");
            default -> throw new IllegalArgumentException("Unknown screen type: " + type);
        };
    }
}
