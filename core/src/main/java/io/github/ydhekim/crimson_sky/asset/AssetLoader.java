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

    private static final String FONT_FILE = "fonts/Quicksand-Regular.ttf";

    /**
     * Every text role bakes at this one size and is scaled to its display size once in
     * {@code CrimsonSky.initializeUI()} — see the class-level note in {@code preloadAssets()} for why
     * a single bake size rather than one bake per role.
     */
    private static final int BAKE_SIZE = 32;

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
        // No image/atlas assets are shipped yet (M4 foundation cleanup) — placeholder visuals are
        // code-generated via TextureFactory. Only the Turkish-capable FreeType font loads here, but it
        // loads five times: one bake per distinct text role.
        //
        // Why five separate BitmapFont objects rather than one font rescaled per label: Scene2D's
        // Label.setFontScale() writes straight through to the font's BitmapFontData, which every Label
        // sharing that style shares too (libgdx#4232/#4346) — so two labels asking one font for two sizes
        // means whichever lays out last wins, for both. Giving each role its own object, pre-scaled once
        // at startup, removes the shared mutable state instead of trying to sequence around it.
        //
        // All five bake at the same BAKE_SIZE=32 canvas. For roles that display *below* 32px that's a
        // supersample (FreeType gets 2× the source pixels per glyph, and the result is minified rather
        // than hinted at a tiny native size), which is why those enable mipmapping — they're deliberately
        // minified, exactly what mipmaps are for. Roles that display at or above 32px are magnified
        // instead, so mipmaps would do nothing for them and Linear alone is correct.
        //
        // The load keys are AssetManager registry names, not files: FreetypeFontLoader generates from
        // fontFileName, which is how "default-font.ttf" already works with no such file on disk.
        loadFont("default-font.ttf", true);    // body — displays 16px
        loadFont("caption-font.ttf", true);    // caption — displays 14px (timestamps, XP tags, version)
        loadFont("emphasis-font.ttf", true);   // emphasis — displays 18px (character names)
        loadFont("title-font.ttf", false);     // title — displays at the 32px bake, unscaled
        loadFont("title-lg-font.ttf", false);  // title-lg — displays 38.4px (ConnectionScreen splash)

        // Block until all assets are loaded
        assetManager.finishLoading();

        System.out.println("All assets loaded successfully.");
    }

    /**
     * Queues one bake of the shared Turkish-capable font under {@code assetKey}. {@code minified} selects
     * the filter pair: mipmapped minification for roles that display below {@link #BAKE_SIZE}, plain
     * Linear for roles that display at or above it.
     */
    private void loadFont(String assetKey, boolean minified) {
        FreetypeFontLoader.FreeTypeFontLoaderParameter parameter = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
        parameter.fontFileName = FONT_FILE;
        parameter.fontParameters.size = BAKE_SIZE;
        parameter.fontParameters.genMipMaps = minified;
        parameter.fontParameters.minFilter = minified
            ? Texture.TextureFilter.MipMapLinearLinear
            : Texture.TextureFilter.Linear;
        parameter.fontParameters.magFilter = Texture.TextureFilter.Linear;
        parameter.fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS + TURKISH_CHARS;
        assetManager.load(assetKey, BitmapFont.class, parameter);
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

