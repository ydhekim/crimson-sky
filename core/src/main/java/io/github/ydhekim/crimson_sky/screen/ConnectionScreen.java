package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.model.PlatformType;
import io.github.ydhekim.crimson_sky.common.network.packet.LocalizationRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginResponse;
import io.github.ydhekim.crimson_sky.network.NetworkListener;

import java.util.HashMap;

public class ConnectionScreen extends BaseScreen implements NetworkListener {
    private VisLabel statusLabel;
    private VisTextButton retryButton;
    private String testIdentityToken;
    private boolean isAuthenticating = false;
    private boolean isConnecting = false;

    public ConnectionScreen(final CrimsonSky game, String testIdentityToken) {
        super(game);
        this.testIdentityToken = testIdentityToken;
        setupUI();
    }

    public ConnectionScreen(final CrimsonSky game) {
        super(game);
        setupUI();
    }

    @Override
    public void show() {
        super.show();
        connectToServer();
    }

    private void connectToServer() {
         if (isConnecting) return;
         isConnecting = true;
         game.getNetworkClient().connect("127.0.0.1", 54555, 54777);
    }

    private void setupUI() {
        // Clear the stage before rebuilding to prevent duplicate actors
        stage.clear();

        // Re-add the background image if it exists
        if (backgroundImage != null) {
            stage.addActor(backgroundImage);
        }

        VisTable mainPanel = createMainContentPanel();

        VisLabel titleLabel = new VisLabel("CRIMSON SKY");
        titleLabel.setFontScale(2f);

        statusLabel = new VisLabel(game.getLanguageManager().get("UI_CONNECTING"));

        retryButton = new VisTextButton(game.getLanguageManager().get("UI_BTN_RETRY"));
        retryButton.setVisible(false);
        retryButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (testIdentityToken != null) {
                    authenticateWithPlatform();
                } else {
                     connectToServer();
                }
            }
        });

        mainPanel.add(titleLabel).padBottom(40).row();
        mainPanel.add(statusLabel).padTop(10).row();
        mainPanel.add(retryButton).padTop(20);
    }

    @Override
    public void refreshUI() {
        Gdx.app.log("ConnectionScreen", "Refreshing UI with new localizations.");
        setupUI();
    }

    private void authenticateWithPlatform() {
        if (isAuthenticating) return;
        isAuthenticating = true;

        retryButton.setVisible(false);
        statusLabel.setText(game.getLanguageManager().get("UI_AUTHENTICATING"));

        LoginRequest request = new LoginRequest(
            PlatformType.TEST,
            testIdentityToken != null ? testIdentityToken : "test_token",
            "1.0.0",
            "Desktop",
            new HashMap<>()
        );

        game.getNetworkClient().sendTCP(request);
    }

    @Override
    public void onConnected() {
        Gdx.app.log("ConnectionScreen", "Connection established.");
        isConnecting = false;
        game.getNetworkClient().sendTCP(new LocalizationRequest(game.getLanguageManager().getCurrentLang()));
    }

    @Override
    public void onLocalizationResponse(io.github.ydhekim.crimson_sky.common.network.packet.LocalizationResponse response) {
        super.onLocalizationResponse(response);

        if (response.success() && testIdentityToken != null && !isAuthenticating) {
             Gdx.app.postRunnable(this::authenticateWithPlatform);
        }
    }

    @Override
    public void onLoginResponse(LoginResponse response) {
        if (response.success()) {
            statusLabel.setText(game.getLanguageManager().get("UI_LOGIN_SUCCESS"));
            game.setScreen(new MainMenuScreen(game));
        } else {
            statusLabel.setText(game.getLanguageManager().get("UI_LOGIN_FAILED") + ": " + response.message());
            retryButton.setVisible(true);
            isAuthenticating = false;
        }
    }

    @Override
    public void onDisconnected() {
        statusLabel.setText(game.getLanguageManager().get("UI_DISCONNECTED"));
        retryButton.setVisible(true);
        isAuthenticating = false;
        isConnecting = false;
    }
}
