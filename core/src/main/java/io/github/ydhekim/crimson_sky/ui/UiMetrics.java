package io.github.ydhekim.crimson_sky.ui;

/**
 * Named button/sizing constants, extracted from what every screen already uses (see prompt 25) —
 * this doesn't change any screen's current appearance, it gives the numbers already in use a single
 * source of truth instead of repeated literals. NAV_BUTTON is the de facto standard three of four
 * screens already agree on; FACTION_BUTTON/SMALL_BUTTON are CharacterCreationScreen-specific sizes
 * that diverge from it — flagged, not resolved, here (see system design §24 / the screen-by-screen
 * design pass this prompt precedes).
 */
public final class UiMetrics {
    private UiMetrics() {}

    public static final float NAV_BUTTON_WIDTH = 200f;
    public static final float NAV_BUTTON_HEIGHT = 40f;

    public static final float DIALOG_BUTTON_WIDTH = 96f;
    public static final float DIALOG_BUTTON_HEIGHT = 32f;

    public static final float ICON_BUTTON_SIZE = 16f;

    // CharacterCreationScreen-specific — not yet reconciled with NAV_BUTTON, see class javadoc.
    public static final float FACTION_BUTTON_WIDTH = 150f;
    public static final float FACTION_BUTTON_HEIGHT = 40f;
    public static final float SMALL_BUTTON_WIDTH = 90f;
    public static final float SMALL_BUTTON_HEIGHT = 30f;
}
