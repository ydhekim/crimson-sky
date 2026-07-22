package io.github.ydhekim.crimson_sky;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms.
 * <p>
 * Screen navigation is owned entirely by {@link CrimsonSky}, which constructs and holds the
 * ScreenFactory/ScreenRouter (dependency injection, not a singleton). This class exists only as the
 * concrete {@code ApplicationListener} the launchers instantiate and as a per-platform hook point;
 * it adds no lifecycle of its own.
 */
public class Main extends CrimsonSky {
}
