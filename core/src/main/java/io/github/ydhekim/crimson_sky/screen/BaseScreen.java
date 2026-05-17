package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisTable;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.network.packet.LocalizationResponse;
import io.github.ydhekim.crimson_sky.network.NetworkListener;

public abstract class BaseScreen extends ScreenAdapter implements NetworkListener {
    protected final CrimsonSky game;
    protected Stage stage;
    protected Viewport viewport;
    protected Image backgroundImage;
    protected TextButton.TextButtonStyle customButtonStyle;

    protected static final float VIRTUAL_WIDTH = 1280f;
    protected static final float VIRTUAL_HEIGHT = 720f;
    protected static final float MAIN_PANEL_WIDTH = 960f;
    protected static final float MAIN_PANEL_HEIGHT = 600f;

    protected Texture panelBackgroundTexture;

    public BaseScreen(CrimsonSky game) {
        this.game = game;
        viewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        stage = new Stage(viewport);
        setupBackground();
        setupButtonStyle();
        createPanelBackground();
    }

    private void setupBackground() {
        if (game.getAssetManager().isLoaded("background.png", Texture.class)) {
            Texture bgTexture = game.getAssetManager().get("background.png", Texture.class);
            backgroundImage = new Image(new TextureRegionDrawable(new TextureRegion(bgTexture)));
            backgroundImage.setFillParent(true);
            stage.addActor(backgroundImage);
        }
    }

    private void createPanelBackground() {
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(new Color(0.15f, 0.15f, 0.15f, 0.85f));
        bgPixmap.fill();
        panelBackgroundTexture = new Texture(bgPixmap);
        bgPixmap.dispose();
    }

    private void setupButtonStyle() {
        if (VisUI.isLoaded()) {
            customButtonStyle = VisUI.getSkin().get("custom", TextButton.TextButtonStyle.class);
        }
    }

    protected VisTable createMainContentPanel() {
        VisTable container = new VisTable();
        container.setFillParent(true);

        VisTable mainPanel = new VisTable();
        mainPanel.setBackground(new TextureRegionDrawable(new TextureRegion(panelBackgroundTexture)));
        mainPanel.pad(20);

        container.add(mainPanel).width(MAIN_PANEL_WIDTH).height(MAIN_PANEL_HEIGHT);
        stage.addActor(container);

        return mainPanel;
    }

    /**
     * This method should be overridden by screens that need to rebuild their UI
     * after a significant data change, like receiving localization.
     */
    public void refreshUI() {
        // By default, does nothing.
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        game.getNetworkClient().setListener(this);
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
        game.getNetworkClient().setListener(null);
    }

    @Override
    public void onLocalizationResponse(LocalizationResponse response) {
        if (response.success()) {
            Gdx.app.postRunnable(this::refreshUI);
        }
    }

    @Override
    public void dispose() {
        if (stage != null) {
            stage.dispose();
        }
        if (panelBackgroundTexture != null) {
            panelBackgroundTexture.dispose();
        }
    }
}
