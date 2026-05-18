package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.model.PlatformType;
import io.github.ydhekim.crimson_sky.common.network.packet.LocalizationRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginResponse;
import io.github.ydhekim.crimson_sky.network.NetworkListener;
import io.github.ydhekim.crimson_sky.ui.UIButtonBuilder;

import java.util.HashMap;

/**
 * Screen for handling server connection and initial authentication.
 * Refactored to use State Machine (ConnectionState enum) for cleaner state transitions.
 * Uses Command Pattern (ClickListener) for button actions.
 * Applies Single Responsibility Principle via separated state and UI logic.
 */
public class ConnectionScreen extends BaseScreen implements NetworkListener {
    private VisLabel statusLabel;
    private com.badlogic.gdx.scenes.scene2d.ui.TextButton retryButton;
    private String testIdentityToken;

    // State machine
    private ConnectionState currentState = ConnectionState.IDLE;

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
        setState(ConnectionState.CONNECTING);
        connectToServer();
    }

    /**
     * Transitions to a new state and updates UI accordingly.
     */
    private void setState(ConnectionState newState) {
        Gdx.app.log("ConnectionScreen", "State transition: " + currentState + " -> " + newState);
        this.currentState = newState;
        updateUIForState();
    }

    /**
     * Updates UI elements based on current state.
     */
    private void updateUIForState() {
        String statusMessage = game.getLanguageManager().get(currentState.getMessageKey());
        statusLabel.setText(statusMessage);

        // Show retry button for error/disconnected states
        boolean showRetry = currentState == ConnectionState.FAILURE || currentState == ConnectionState.DISCONNECTED;
        retryButton.setVisible(showRetry);
    }

    /**
     * Initiates network connection to server.
     */
    private void connectToServer() {
        if (currentState != ConnectionState.CONNECTING) {
            return; // Prevent reconnection if not in CONNECTING state
        }
        game.getNetworkClient().connect("127.0.0.1", 54555, 54777);
    }

    /**
     * Builds the UI for the connection screen using UIButtonBuilder (Command Pattern).
     */
    private void setupUI() {
        stage.clear();

        if (backgroundImage != null) {
            stage.addActor(backgroundImage);
        }

        VisTable mainPanel = createMainContentPanel();

        VisLabel titleLabel = new VisLabel("CRIMSON SKY");
        titleLabel.setFontScale(2f);

        statusLabel = new VisLabel(game.getLanguageManager().get(currentState.getMessageKey()));

        // Use UIButtonBuilder with ScreenAction for cleaner button setup
        retryButton = new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_RETRY"))
            .withStyle(customButtonStyle)
            .withAction(this::handleRetryButtonClick)
            .build();
        retryButton.setVisible(false);

        mainPanel.add(titleLabel).padBottom(40).row();
        mainPanel.add(statusLabel).padTop(10).row();
        mainPanel.add(retryButton).padTop(20);
    }

    /**
     * Handles retry button click.
     * Attempts authentication if token available, otherwise reconnects.
     */
    private void handleRetryButtonClick() {
        if (testIdentityToken != null) {
            authenticateWithPlatform();
        } else {
            setState(ConnectionState.CONNECTING);
            connectToServer();
        }
    }

    /**
     * Authenticates with the platform using the test identity token.
     */
    private void authenticateWithPlatform() {
        if (currentState == ConnectionState.AUTHENTICATING) {
            return; // Already authenticating
        }

        setState(ConnectionState.AUTHENTICATING);

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
    public void refreshUI() {
        Gdx.app.log("ConnectionScreen", "Refreshing UI with new localizations.");
        setupUI();
    }

    // ===== Network Listener Callbacks =====

    @Override
    public void onConnected() {
        Gdx.app.log("ConnectionScreen", "Connection established.");
        // Request localization immediately after connection
        game.getNetworkClient().sendTCP(new LocalizationRequest(game.getLanguageManager().getCurrentLang()));
    }

    @Override
    public void onLocalizationResponse(io.github.ydhekim.crimson_sky.common.network.packet.LocalizationResponse response) {
        super.onLocalizationResponse(response);

        // After localization is received, authenticate if we have a test token
        if (response.success() && testIdentityToken != null && currentState != ConnectionState.AUTHENTICATING) {
            Gdx.app.postRunnable(this::authenticateWithPlatform);
        }
    }

    @Override
    public void onLoginResponse(LoginResponse response) {
        if (response.success()) {
            setState(ConnectionState.SUCCESS);
            // Transition to main menu after a short delay (for UX)
            Gdx.app.postRunnable(() -> {
                game.setScreen(new MainMenuScreen(game));
            });
        } else {
            setState(ConnectionState.FAILURE);
            Gdx.app.log("ConnectionScreen", "Login failed: " + response.message());
        }
    }

    @Override
    public void onDisconnected() {
        setState(ConnectionState.DISCONNECTED);
    }
}
