package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.model.PlatformType;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginResponse;
import io.github.ydhekim.crimson_sky.network.NetworkListener;

import java.util.HashMap;

public class ConnectionScreen extends BaseScreen implements NetworkListener {
    private VisLabel statusLabel;
    private VisTextButton retryButton;
    private final String testIdentityToken;

    public ConnectionScreen(final CrimsonSky game, String testIdentityToken) {
        super(game);
        this.testIdentityToken = testIdentityToken;
        setupUI();
        game.getNetworkClient().setListener(this);

        // If we have a token, start the auth process immediately
        if (testIdentityToken != null) {
            authenticateWithPlatform();
        } else {
            statusLabel.setText("No identity token provided.\nPlease launch with --testIdentityToken=YourToken");
            retryButton.setVisible(true);
        }
    }

    private void setupUI() {
        VisTable mainPanel = createMainContentPanel();

        VisLabel titleLabel = new VisLabel("CRIMSON SKY");
        titleLabel.setFontScale(2f);

        statusLabel = new VisLabel("Connecting to server...");

        retryButton = new VisTextButton("Retry Connection");
        retryButton.setVisible(false);
        retryButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (testIdentityToken != null) {
                    authenticateWithPlatform();
                }
            }
        });

        mainPanel.add(titleLabel).padBottom(40).row();
        mainPanel.add(statusLabel).padTop(10).row();
        mainPanel.add(retryButton).padTop(20);
    }

    private void authenticateWithPlatform() {
        retryButton.setVisible(false);
        statusLabel.setText("Authenticating with token...");

        LoginRequest request = new LoginRequest(
            PlatformType.TEST,
            testIdentityToken,
            "1.0.0",
            "Desktop",
            new HashMap<>()
        );

        game.getNetworkClient().sendTCP(request);
    }

    @Override
    public void onConnected() {
        if (testIdentityToken != null) {
            authenticateWithPlatform();
        }
    }

    @Override
    public void onLoginResponse(LoginResponse response) {
        if (response.success) {
            statusLabel.setText("Login successful!");
            game.setScreen(new MainMenuScreen(game));
        } else {
            statusLabel.setText("Authentication failed: " + response.message);
            retryButton.setVisible(true);
        }
    }

    @Override
    public void onDisconnected() {
        statusLabel.setText("Disconnected from server.");
        retryButton.setVisible(true);
    }

    @Override
    public void dispose() {
        super.dispose();
        game.getNetworkClient().setListener(null);
    }
}
