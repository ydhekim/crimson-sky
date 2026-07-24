package io.github.ydhekim.crimson_sky.ui;

import com.badlogic.gdx.Gdx;

/**
 * Applies windowing preferences ({@code "WIDTHxHEIGHT"} + fullscreen) to the live LibGDX window.
 * <p>
 * Extracted from {@code SettingsScreen} so both it and {@code ConnectionScreen} apply a returning
 * player's saved resolution/fullscreen through the exact same path (prompt 31) — the same shared-helper
 * extraction precedent as {@link UiMetrics}/{@link UiTheme}. Pure side-effect on {@code Gdx.graphics};
 * holds no state.
 */
public final class DisplaySettings {
    private DisplaySettings() {}

    /**
     * Sets the window to {@code resolutionStr} (e.g. {@code "1280x720"}) in windowed mode, or to the
     * current display's mode when {@code isFullscreen}. A malformed/blank resolution is ignored rather
     * than throwing.
     */
    public static void apply(String resolutionStr, boolean isFullscreen) {
        if (resolutionStr == null || !resolutionStr.contains("x")) return;

        try {
            String[] parts = resolutionStr.split("x");
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);

            if (isFullscreen) {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            } else {
                Gdx.graphics.setWindowedMode(width, height);
            }
            Gdx.app.log("DisplaySettings", "Window mode updated: " + resolutionStr + " (fullscreen: " + isFullscreen + ")");
        } catch (Exception e) {
            Gdx.app.error("DisplaySettings", "Failed to apply resolution", e);
        }
    }
}
