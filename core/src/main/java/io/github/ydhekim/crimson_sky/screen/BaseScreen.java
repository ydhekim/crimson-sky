package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
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
import io.github.ydhekim.crimson_sky.ui.TextureFactory;

/**
 * Base class for all screens in the game.
 * Provides common initialization, rendering, and resource management.
 * Applies Template Method Pattern for screen lifecycle.
 * Uses TextureFactory to decouple texture creation from screen logic.
 */
public abstract class BaseScreen extends ScreenAdapter implements NetworkListener {
    protected final CrimsonSky game;
    protected Stage stage;
    protected Viewport viewport;
    protected Image backgroundImage;
    protected TextButton.TextButtonStyle customButtonStyle;
    protected TextButton.TextButtonStyle squareButtonStyle;

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
        initializePanelBackground();
    }

    /**
     * Sets up the background image from assets.
     */
    private void setupBackground() {
        if (game.getAssetManager().isLoaded("background.png", Texture.class)) {
            Texture bgTexture = game.getAssetManager().get("background.png", Texture.class);
            backgroundImage = new Image(new TextureRegionDrawable(new TextureRegion(bgTexture)));
            backgroundImage.setFillParent(true);
            stage.addActor(backgroundImage);
        }
    }

    /**
     * Creates the panel background texture using TextureFactory.
     * Centralizes texture creation logic.
     */
    private void initializePanelBackground() {
        panelBackgroundTexture = TextureFactory.createPanelBackgroundTexture();
    }

    /**
     * Sets up the custom button style from VisUI skin.
     */
    private void setupButtonStyle() {
        if (VisUI.isLoaded()) {
            // Placeholder-phase stand-in: "custom"/"square" styles no longer exist (uiskin.json
            // removed, see prompt 24). Both point at VisUI's own bundled default style until the
            // UI foundation refactor (prompt following this one) replaces this with a real theme.
            TextButton.TextButtonStyle defaultStyle = VisUI.getSkin().get(TextButton.TextButtonStyle.class);
            customButtonStyle = defaultStyle;
            squareButtonStyle = defaultStyle;
        }
    }

    /**
     * Creates the main content panel with background and tables.
     * Applies Table layout pattern for responsive design.
     */
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
     * Template method: override in subclasses to refresh UI after data changes.
     * Called when localization or other significant data changes occur.
     */
    public void refreshUI() {
        // Default: no-op. Subclasses override as needed.
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
