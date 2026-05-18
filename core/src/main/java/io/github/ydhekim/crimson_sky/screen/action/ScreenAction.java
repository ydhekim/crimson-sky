package io.github.ydhekim.crimson_sky.screen.action;

/**
 * Represents an action that can be performed (Command Pattern).
 * Used for button clicks, network events, and other discrete user actions.
 */
@FunctionalInterface
public interface ScreenAction {
    /**
     * Executes the action.
     */
    void execute();
}
