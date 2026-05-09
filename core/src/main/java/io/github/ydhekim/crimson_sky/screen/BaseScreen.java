package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.kotcrab.vis.ui.VisUI;
import io.github.ydhekim.crimson_sky.CrimsonSky;

public abstract class BaseScreen extends ScreenAdapter {
    protected final CrimsonSky game;
    protected Stage stage;
    protected Viewport viewport;
    protected Image backgroundImage;
    protected TextButton.TextButtonStyle customButtonStyle;

    public BaseScreen(CrimsonSky game) {
        this.game = game;
        viewport = new ScreenViewport();
        stage = new Stage(viewport);
        setupBackground();
        setupButtonStyle();
    }

    private void setupBackground() {
        if (game.getAssetManager().isLoaded("background.png", Texture.class)) {
            Texture bgTexture = game.getAssetManager().get("background.png", Texture.class);
            backgroundImage = new Image(new TextureRegionDrawable(new TextureRegion(bgTexture)));
            backgroundImage.setFillParent(true);
            stage.addActor(backgroundImage);
        }
    }

    private void setupButtonStyle() {
        if (VisUI.isLoaded()) {
            // Retrieve the custom button style defined in uiskin.json
            customButtonStyle = VisUI.getSkin().get("custom", TextButton.TextButtonStyle.class);
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        if (stage != null) {
            stage.dispose();
        }
    }
}
