package io.github.ydhekim.crimson_sky;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisTextField;
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
    // Display size of each text role, as a scale factor on AssetLoader's shared 32px bake. Body is the
    // 16px baseline; caption/emphasis replace the 0.85×/1.1×/1.2× setFontScale calls that used to sit at
    // the call sites, resolved against 16px rather than VisUI's old 15px bundled font.
    private static final float BODY_SCALE = 0.5f;       // 16px
    private static final float CAPTION_SCALE = 0.4375f; // 14px
    private static final float EMPHASIS_SCALE = 0.5625f;// 18px
    private static final float TITLE_LG_SCALE = 1.2f;   // 38.4px

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
            // One BitmapFont object per text role, each scaled from the shared 32px bake exactly once,
            // here. Nothing in the app may call Label.setFontScale() afterwards: that call writes through
            // to the font's BitmapFontData, which is shared by every label drawing with the same font
            // object, so one label's scale silently becomes every sibling label's scale
            // (libgdx#4232/#4346). Separate objects are what make the sizes below independent.
            BitmapFont bodyFont = assetManager.get("default-font.ttf", BitmapFont.class);
            bodyFont.getData().setScale(BODY_SCALE);
            BitmapFont captionFont = assetManager.get("caption-font.ttf", BitmapFont.class);
            captionFont.getData().setScale(CAPTION_SCALE);
            BitmapFont emphasisFont = assetManager.get("emphasis-font.ttf", BitmapFont.class);
            emphasisFont.getData().setScale(EMPHASIS_SCALE);
            // The title font is already baked at its intended 32px display size (prompt 41), so it needs
            // no compensating rescale; title-lg is the one role that magnifies past the bake.
            BitmapFont titleFont = assetManager.get("title-font.ttf", BitmapFont.class);
            BitmapFont titleLgFont = assetManager.get("title-lg-font.ttf", BitmapFont.class);
            titleLgFont.getData().setScale(TITLE_LG_SCALE);

            // No custom skin/atlases are shipped (M4 foundation cleanup): load VisUI's own bundled
            // default skin, then repoint it at the Turkish-capable font.
            VisUI.load();
            Skin skin = VisUI.getSkin();
            skin.add("default-font", bodyFont, BitmapFont.class);
            skin.add("title-font", titleFont, BitmapFont.class);

            // Why each style needs its font field assigned individually (K10's finding, verified at
            // runtime): Skin resolves a style's font when it *parses* the skin JSON, so every style
            // VisUI.load() just built holds a direct reference to VisUI's own bundled "Vis Open Sans"
            // 15px. Registering "default-font" above only replaces a map entry — it can't reach back into
            // a style object that already captured the old reference. BaseScreen's button styles were
            // never affected because UiTheme constructs fresh TextButtonStyles at screen-construction
            // time, well after this runs, which sidesteps the parse-time freeze entirely.
            skin.get(Label.LabelStyle.class).font = bodyFont;
            skin.get(VisTextField.VisTextFieldStyle.class).font = bodyFont;
            skin.get(VisCheckBox.VisCheckBoxStyle.class).font = bodyFont;
            // VisDialog's title bar: VisDialog(String) → VisWindow(title, true) → WindowStyle "default".
            skin.get(Window.WindowStyle.class).titleFont = bodyFont;

            // VisSelectBox extends Scene2D's SelectBox and uses the plain SelectBoxStyle — VisUI defines
            // no VisSelectBoxStyle at all. Its dropdown needs a *second* assignment: the skin JSON gives
            // SelectBoxStyle an inline `listStyle: {...}` object, so the open dropdown reads that nested
            // instance, not the shared List$ListStyle the next line fixes. Repointing only one of the two
            // leaves either the closed box or the open list in the wrong typeface.
            SelectBox.SelectBoxStyle selectBoxStyle = skin.get(SelectBox.SelectBoxStyle.class);
            selectBoxStyle.font = bodyFont;
            selectBoxStyle.listStyle.font = bodyFont;
            skin.get(List.ListStyle.class).font = bodyFont;

            // Named styles for every non-default text role, each backed by its own pre-scaled font.
            skin.add("title", labelStyleWith(skin, titleFont), Label.LabelStyle.class);
            skin.add("title-lg", labelStyleWith(skin, titleLgFont), Label.LabelStyle.class);
            skin.add("caption", labelStyleWith(skin, captionFont), Label.LabelStyle.class);
            skin.add("emphasis", labelStyleWith(skin, emphasisFont), Label.LabelStyle.class);

            System.out.println("VisUI initialized: every default style now uses the Turkish-capable font.");
        }
    }

    /** A copy of the skin's default label style with {@code font} swapped in — one line per named role. */
    private static Label.LabelStyle labelStyleWith(Skin skin, BitmapFont font) {
        Label.LabelStyle style = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
        style.font = font;
        return style;
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
