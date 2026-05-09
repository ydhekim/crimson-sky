package io.github.ydhekim.crimson_sky;

import io.github.ydhekim.crimson_sky.screen.ScreenManager;
import io.github.ydhekim.crimson_sky.screen.ScreenType;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms.
 */
public class Main extends CrimsonSky {
    @Override
    public void create() {
        super.create();
        ScreenManager.getInstance().initialize(this);
        ScreenManager.getInstance().showScreen(ScreenType.LOGIN);
    }

    @Override
    public void dispose() {
        super.dispose();
        ScreenManager.getInstance().dispose();
    }
}
