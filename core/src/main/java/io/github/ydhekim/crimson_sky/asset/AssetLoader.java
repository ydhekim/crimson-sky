package io.github.ydhekim.crimson_sky.asset;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader;
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader;

/**
 * Manages asset loading and cleanup for the game.
 * Applies Single Responsibility Principle by isolating asset initialization logic.
 */
public class AssetLoader {
    private final AssetManager assetManager;
    private static final String TURKISH_CHARS = "abcçdefgğhıijklmnoöprsştuüvyz"
        + "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZ"
        + "0123456789.,:!?()_+-=/*%&'@\"";

    public AssetLoader() {
        this.assetManager = new AssetManager();
    }

    /**
     * Initializes asset loaders and preloads essential game assets.
     * Should be called during game initialization.
     */
    public void initialize() {
        setupFontLoaders();
        preloadAssets();
    }

    /**
     * Configures loaders for FreeType fonts.
     */
    private void setupFontLoaders() {
        FileHandleResolver resolver = new InternalFileHandleResolver();
        assetManager.setLoader(FreeTypeFontGenerator.class, new FreeTypeFontGeneratorLoader(resolver));
        assetManager.setLoader(BitmapFont.class, ".ttf", new FreetypeFontLoader(resolver));
    }

    /**
     * Preloads core game assets (background, UI atlas, font).
     * This is a synchronous load; consider creating a loading screen for larger projects.
     */
    private void preloadAssets() {
        // Configure font parameters
        FreetypeFontLoader.FreeTypeFontLoaderParameter fontParameter = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
        fontParameter.fontFileName = "fonts/Quicksand-Regular.ttf";
        fontParameter.fontParameters.size = 16;
        fontParameter.fontParameters.minFilter = Texture.TextureFilter.Linear;
        fontParameter.fontParameters.magFilter = Texture.TextureFilter.Linear;
        fontParameter.fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS + TURKISH_CHARS;

        // Load assets
        assetManager.load("background.png", Texture.class);
        assetManager.load("demir_avaz_ui_buttons.atlas", TextureAtlas.class);
        assetManager.load("achievements/achievements.atlas", TextureAtlas.class);
        assetManager.load("default-font.ttf", BitmapFont.class, fontParameter);

        // Block until all assets are loaded
        assetManager.finishLoading();

        System.out.println("All assets loaded successfully.");
    }

    /**
     * Gets the underlying AssetManager.
     */
    public AssetManager getAssetManager() {
        return assetManager;
    }

    /**
     * Disposes of all managed assets.
     * Must be called during game shutdown.
     */
    public void dispose() {
        if (assetManager != null) {
            assetManager.dispose();
            System.out.println("AssetManager disposed.");
        }
    }
}

