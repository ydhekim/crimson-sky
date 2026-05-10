package io.github.ydhekim.crimson_sky;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader;
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.kotcrab.vis.ui.VisUI;
import io.github.ydhekim.crimson_sky.network.GameClient;
import io.github.ydhekim.crimson_sky.network.KryoClient;
import io.github.ydhekim.crimson_sky.screen.MainMenuScreen;

public class CrimsonSky extends Game {
    private GameClient networkClient;
    private AssetManager assetManager;

    @Override
    public void create() {
        assetManager = new AssetManager();
        FileHandleResolver resolver = new InternalFileHandleResolver();

        // Set up FreeTypeFontLoader
        assetManager.setLoader(FreeTypeFontGenerator.class, new FreeTypeFontGeneratorLoader(resolver));
        assetManager.setLoader(BitmapFont.class, ".ttf", new FreetypeFontLoader(resolver));

        FreetypeFontLoader.FreeTypeFontLoaderParameter fontParameter = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
        fontParameter.fontFileName = "fonts/Quicksand-Regular.ttf";
        fontParameter.fontParameters.size = 16;
        fontParameter.fontParameters.minFilter = Texture.TextureFilter.Linear;
        fontParameter.fontParameters.magFilter = Texture.TextureFilter.Linear;

        // Preload general assets like the background, UI atlas, and custom font
        assetManager.load("background.png", Texture.class);
        assetManager.load("demir_avaz_ui_buttons.atlas", TextureAtlas.class);
        assetManager.load("default-font.ttf", BitmapFont.class, fontParameter);

        // Blocks until assets are loaded (for a simple game this is OK, usually you'd show a loading screen)
        assetManager.finishLoading();

        // Load VisUI using our custom font via the Skin
        if (!VisUI.isLoaded()) {
            BitmapFont customFont = assetManager.get("default-font.ttf", BitmapFont.class);
            TextureAtlas uiAtlas = assetManager.get("demir_avaz_ui_buttons.atlas", TextureAtlas.class);

            VisUI.load();
            VisUI.getSkin().add("default-font", customFont, BitmapFont.class);
            VisUI.getSkin().addRegions(uiAtlas);

            // Load custom styles from uiskin.json
            VisUI.getSkin().load(Gdx.files.internal("uiskin.json"));
        }
        // Applying Dependency Inversion
        networkClient = new KryoClient();
        // Assuming default local test server config
        networkClient.connect("127.0.0.1", 54555, 54777);

        // Start the game directly at the Main Menu
        setScreen(new MainMenuScreen(this));
    }

    public GameClient getNetworkClient() {
        return networkClient;
    }

    public AssetManager getAssetManager() {
        return assetManager;
    }

    @Override
    public void dispose() {
        super.dispose();
        if (networkClient != null) {
            networkClient.disconnect();
        }
        if (assetManager != null) {
            assetManager.dispose();
        }
        if (VisUI.isLoaded()) {
            VisUI.dispose();
        }
    }
}
