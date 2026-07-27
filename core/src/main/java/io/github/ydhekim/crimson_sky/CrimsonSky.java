package io.github.ydhekim.crimson_sky;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.kotcrab.vis.ui.VisUI;
import io.github.ydhekim.crimson_sky.asset.AssetLoader;
import io.github.ydhekim.crimson_sky.common.model.AccountSettings;
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
    // The signed-in account's persisted settings, loaded from the LoginResponse. Defaulted (never null)
    // so screens reading it before login don't have to null-check.
    private AccountSettings accountSettings = AccountSettings.createDefault();

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

        // Start with connection screen
        setScreen(new ConnectionScreen(this, testToken));
    }

    /**
     * Initializes VisUI with custom fonts and UI assets.
     * Separated from create() for improved code organization (SRP).
     */
    private void initializeUI() {
        if (!VisUI.isLoaded()) {
            BitmapFont bodyFont = assetManager.get("default-font.ttf", BitmapFont.class);
            // Supersampling: the body font is baked at 32px and scaled back to its 16px display size, so
            // FreeType's rasterizer gets 2× the source pixels per glyph and the result is Linear-minified
            // rather than hinted at a tiny native size. Visual size is unchanged — 32 × 0.5 = the old 16.
            // NOTE: Scene2D's Label.setFontScale REPLACES this base scale rather than multiplying it (see
            // Label.layout()), so any label drawing with THIS font that also sets its own scale must fold
            // the 0.5 in. None do today — the existing setFontScale sites are all plain VisLabels, which
            // use VisUI's bundled font, not this one (see the font-reach note below).
            bodyFont.getData().setScale(0.5f);
            // The title font is already baked at its intended 32px display size (prompt 41), so it needs
            // no compensating rescale.
            BitmapFont titleFont = assetManager.get("title-font.ttf", BitmapFont.class);

            // No custom skin/atlases are shipped (M4 foundation cleanup): load VisUI's own bundled
            // default skin and register only the custom Turkish-capable fonts onto it.
            //
            // How far these fonts actually reach (verified at runtime, not assumed): Skin resolves a
            // style's font reference when it parses the skin JSON, so the styles VisUI.load() has already
            // built keep pointing at VisUI's own bundled "Vis Open Sans" 15px — adding "default-font"
            // here replaces the map entry, not those existing bindings. Only code that looks the font up
            // *after* this point gets ours: BaseScreen's button styles (getFont("default-font")) and the
            // "title" style below. Plain VisLabels therefore still render in VisUI's font, which is why
            // this class currently draws two typefaces side by side.
            VisUI.load();
            VisUI.getSkin().add("default-font", bodyFont, BitmapFont.class);
            VisUI.getSkin().add("title-font", titleFont, BitmapFont.class);

            // A named style rather than reassigning the shared default LabelStyle's font: that style
            // object backs every plain label in the app, so mutating it in place would reflow all of
            // them (the same shared-mutable-style hazard flagged for VisProgressBar in prompt 39).
            Label.LabelStyle titleStyle = new Label.LabelStyle(VisUI.getSkin().get(Label.LabelStyle.class));
            titleStyle.font = titleFont;
            VisUI.getSkin().add("title", titleStyle, Label.LabelStyle.class);

            System.out.println("VisUI initialized with bundled skin + custom fonts (body 32px@0.5, title 32px).");
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

    public AccountSettings getAccountSettings() {
        return accountSettings;
    }

    public void setAccountSettings(AccountSettings accountSettings) {
        this.accountSettings = accountSettings != null ? accountSettings : AccountSettings.createDefault();
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
