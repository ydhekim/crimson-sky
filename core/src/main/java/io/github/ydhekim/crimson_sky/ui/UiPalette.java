package io.github.ydhekim.crimson_sky.ui;

import com.badlogic.gdx.graphics.Color;

/**
 * Named accent colors (system design §24's "apply the accent palette now" decision), sibling to
 * {@link UiMetrics}. Crimson is the primary brand accent (buttons, titles, active states); gold is
 * secondary (dividers, highlights, reward/currency moments). Blue/silver are the Skyborn's own pair,
 * added when the character-creation wizard's faction cards needed a second faction accent that isn't
 * the app-wide crimson — they belong to that faction specifically, not to "some other button style".
 */
public final class UiPalette {
    private UiPalette() {}

    public static final Color BACKGROUND = new Color(0.078f, 0.071f, 0.063f, 1f);     // #141210
    public static final Color ACCENT_CRIMSON = new Color(0.542f, 0.165f, 0.165f, 1f); // #8A2A2A
    public static final Color ACCENT_CRIMSON_HOVER = new Color(0.64f, 0.24f, 0.22f, 1f);
    public static final Color ACCENT_CRIMSON_PRESSED = new Color(0.42f, 0.11f, 0.11f, 1f);
    public static final Color ACCENT_GOLD = new Color(0.788f, 0.604f, 0.290f, 1f);    // #C99A4A
    public static final Color ACCENT_BLUE = new Color(0.227f, 0.431f, 0.647f, 1f);    // #3A6EA5
    public static final Color ACCENT_BLUE_HOVER = new Color(0.30f, 0.51f, 0.73f, 1f);
    public static final Color ACCENT_BLUE_PRESSED = new Color(0.165f, 0.33f, 0.50f, 1f);
    public static final Color ACCENT_SILVER = new Color(0.722f, 0.769f, 0.816f, 1f);  // #B8C4D0
    public static final Color TEXT_MUTED = new Color(0.78f, 0.77f, 0.74f, 1f);
    public static final Color TEXT_VERSION = new Color(0.353f, 0.337f, 0.306f, 1f);
}
