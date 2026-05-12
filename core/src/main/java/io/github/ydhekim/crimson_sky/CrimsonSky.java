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
import com.kotcrab.vis.ui.VisUI;
import io.github.ydhekim.crimson_sky.network.GameClient;
import io.github.ydhekim.crimson_sky.network.KryoClient;
import io.github.ydhekim.crimson_sky.screen.ConnectionScreen;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class CrimsonSky extends Game {
    private GameClient networkClient;
    private AssetManager assetManager;

    public CrimsonSky() {}

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

        // Read test token from local.properties
        String testToken = readTestToken();

        // Go to ConnectionScreen first
        setScreen(new ConnectionScreen(this, testToken));
    }

    private String readTestToken() {
        Properties properties = new Properties();

        // When running via Gradle or IntelliJ, the working directory is usually lwjgl3/
        // However, local.properties is in the root project directory.
        // We try reading from the root directory first, then fallback to current directory.
        File rootProps = new File("../local.properties");
        File currentProps = new File("local.properties");

        File targetFile = rootProps.exists() ? rootProps : (currentProps.exists() ? currentProps : null);

        if (targetFile != null) {
            try (FileInputStream fis = new FileInputStream(targetFile)) {
                properties.load(fis);
                return properties.getProperty("testIdentityToken");
            } catch (IOException e) {
                System.out.println("Error reading local.properties: " + e.getMessage());
            }
        } else {
             System.out.println("No local.properties file found. Proceeding without a test token.");
        }

        return null;
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
