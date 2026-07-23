package io.github.ydhekim.crimson_sky.ui;

import com.badlogic.gdx.graphics.Color;

/**
 * Named accent colors (system design §24's "apply the accent palette now" decision), sibling to
 * {@link UiMetrics}. Crimson is the primary brand accent (buttons, titles, active states); gold is
 * secondary (dividers, highlights, reward/currency moments). Blue/silver for Skyborn-specific
 * contexts (e.g. the faction selection screen) are intentionally not here yet — added when that
 * screen's turn comes, not guessed at now.
 */
public final class UiPalette {
    private UiPalette() {}

    public static final Color BACKGROUND = new Color(0.078f, 0.071f, 0.063f, 1f);     // #141210
    public static final Color ACCENT_CRIMSON = new Color(0.542f, 0.165f, 0.165f, 1f); // #8A2A2A
    public static final Color ACCENT_CRIMSON_HOVER = new Color(0.64f, 0.24f, 0.22f, 1f);
    public static final Color ACCENT_CRIMSON_PRESSED = new Color(0.42f, 0.11f, 0.11f, 1f);
    public static final Color ACCENT_GOLD = new Color(0.788f, 0.604f, 0.290f, 1f);    // #C99A4A
    public static final Color TEXT_MUTED = new Color(0.78f, 0.77f, 0.74f, 1f);
    public static final Color TEXT_VERSION = new Color(0.353f, 0.337f, 0.306f, 1f);
}
