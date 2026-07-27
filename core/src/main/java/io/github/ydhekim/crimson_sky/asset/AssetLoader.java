package io.github.ydhekim.crimson_sky.asset;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
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
     * Preloads core game assets (currently just the Turkish-capable font).
     * This is a synchronous load; consider creating a loading screen for larger projects.
     */
    private void preloadAssets() {
        // Configure font parameters
        // Baked at 2× the 16px display size and scaled back down in CrimsonSky.initializeUI() — a
        // supersample, since 16px is few source pixels for the rasterizer to anti-alias with. Mipmapping
        // is on because this font is now deliberately minified, which is exactly what mipmaps are for;
        // magFilter stays Linear since nothing magnifies it.
        FreetypeFontLoader.FreeTypeFontLoaderParameter fontParameter = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
        fontParameter.fontFileName = "fonts/Quicksand-Regular.ttf";
        fontParameter.fontParameters.size = 32;
        fontParameter.fontParameters.genMipMaps = true;
        fontParameter.fontParameters.minFilter = Texture.TextureFilter.MipMapLinearLinear;
        fontParameter.fontParameters.magFilter = Texture.TextureFilter.Linear;
        fontParameter.fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS + TURKISH_CHARS;

        // No image/atlas assets are shipped yet (M4 foundation cleanup) — placeholder visuals are
        // code-generated via TextureFactory. Only the Turkish-capable FreeType font loads here.
        assetManager.load("default-font.ttf", BitmapFont.class, fontParameter);

        // A second bake of the same font at title size. FreeType produces raster glyph textures, not
        // outlines, so a 16px bake stretched to 2× by setFontScale is visibly soft no matter what the
        // texture filters say — every screen title did exactly that. Baking 32px (= 16 × 2, the size
        // those titles were already displaying at) lets them render at fontScale(1f), unscaled and crisp.
        // The load key is only the AssetManager's registry name, not a file: FreetypeFontLoader generates
        // from fontFileName, which is how "default-font.ttf" already works with no such file on disk.
        FreetypeFontLoader.FreeTypeFontLoaderParameter titleFontParameter = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
        titleFontParameter.fontFileName = "fonts/Quicksand-Regular.ttf";
        titleFontParameter.fontParameters.size = 32;
        titleFontParameter.fontParameters.minFilter = Texture.TextureFilter.Linear;
        titleFontParameter.fontParameters.magFilter = Texture.TextureFilter.Linear;
        titleFontParameter.fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS + TURKISH_CHARS;
        assetManager.load("title-font.ttf", BitmapFont.class, titleFontParameter);

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

