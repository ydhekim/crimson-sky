package io.github.ydhekim.crimson_sky;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.kotcrab.vis.ui.VisUI;
import io.github.ydhekim.crimson_sky.asset.AssetLoader;
import io.github.ydhekim.crimson_sky.config.ConfigurationManager;
import io.github.ydhekim.crimson_sky.network.GameClient;
import io.github.ydhekim.crimson_sky.network.KryoClient;
import io.github.ydhekim.crimson_sky.screen.ConnectionScreen;
import io.github.ydhekim.crimson_sky.screen.factory.ScreenFactory;
import io.github.ydhekim.crimson_sky.screen.factory.ScreenRouter;
import io.github.ydhekim.crimson_sky.util.LanguageManager;

/**
 * Main game class extending LibGDX Game.
 * Refactored to use bootstrap components for asset loading, configuration, and dependency injection.
 * Applies Dependency Inversion and Single Responsibility Principle.
 */
public class CrimsonSky extends Game {
    private GameClient networkClient;
    private AssetManager assetManager;
    private LanguageManager languageManager;
    private ScreenRouter screenRouter;

    public CrimsonSky() {}

    @Override
    public void create() {
        // Load and initialize assets
        AssetLoader assetLoader = new AssetLoader();
        assetLoader.initialize();
        this.assetManager = assetLoader.getAssetManager();

        // Load configuration
        ConfigurationManager configManager = new ConfigurationManager();

        // Initialize UI with custom font
        initializeUI();

        // Initialize language manager
        languageManager = new LanguageManager();

        // Create network client and dependency link
        networkClient = new KryoClient();
        networkClient.setLanguageManager(languageManager);

        // Create screen factory and router for dependency-injected navigation
        ScreenFactory screenFactory = new ScreenFactory(this);
        screenRouter = new ScreenRouter(this, screenFactory);

        // Read test token from configuration
        String testToken = configManager.getTestIdentityToken();
        String testLangCode = configManager.getLangCode();

        languageManager.setCurrentLang(testLangCode);

        // Start with connection screen
        setScreen(new ConnectionScreen(this, testToken));
    }

    /**
     * Initializes VisUI with custom fonts and UI assets.
     * Separated from create() for improved code organization (SRP).
     */
    private void initializeUI() {
        if (!VisUI.isLoaded()) {
            BitmapFont customFont = assetManager.get("default-font.ttf", BitmapFont.class);
            TextureAtlas uiAtlas = assetManager.get("demir_avaz_ui_buttons.atlas", TextureAtlas.class);

            VisUI.load();
            VisUI.getSkin().add("default-font", customFont, BitmapFont.class);
            VisUI.getSkin().addRegions(uiAtlas);
            VisUI.getSkin().load(Gdx.files.internal("uiskin.json"));

            System.out.println("VisUI initialized with custom assets.");
        }
    }

    public GameClient getNetworkClient() {
        return networkClient;
    }

    public AssetManager getAssetManager() {
        return assetManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public ScreenRouter getScreenRouter() {
        return screenRouter;
    }

    @Override
    public void dispose() {
        super.dispose();
        if (networkClient != null) {
            networkClient.disconnect();
        }
        if (screenRouter != null) {
            screenRouter.dispose();
        }
        if (assetManager != null) {
            assetManager.dispose();
        }
        if (VisUI.isLoaded()) {
            VisUI.dispose();
        }
    }
}
