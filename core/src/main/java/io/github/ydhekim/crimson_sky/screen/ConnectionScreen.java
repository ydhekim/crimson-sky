package io.github.ydhekim.crimson_sky.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import io.github.ydhekim.crimson_sky.CrimsonSky;
import io.github.ydhekim.crimson_sky.common.model.PlatformType;
import io.github.ydhekim.crimson_sky.common.network.packet.LocalizationRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginRequest;
import io.github.ydhekim.crimson_sky.common.network.packet.LoginResponse;
import io.github.ydhekim.crimson_sky.common.model.AccountSettings;
import io.github.ydhekim.crimson_sky.network.NetworkListener;
import io.github.ydhekim.crimson_sky.ui.DisplaySettings;
import io.github.ydhekim.crimson_sky.ui.TextureFactory;
import io.github.ydhekim.crimson_sky.ui.UIButtonBuilder;
import io.github.ydhekim.crimson_sky.ui.UiMetrics;
import io.github.ydhekim.crimson_sky.ui.UiPalette;

import java.util.HashMap;

/**
 * Screen for handling server connection and initial authentication.
 * Refactored to use State Machine (ConnectionState enum) for cleaner state transitions.
 * Uses Command Pattern (ClickListener) for button actions.
 * Applies Single Responsibility Principle via separated state and UI logic.
 * <p>
 * This is the first screen a player sees, so unlike the utility screens it is laid out full-bleed
 * (no boxed panel): placeholder crest, crimson title, gold divider, pulsing status indicator and a
 * corner version label — see the screen-by-screen design pass in docs/planning.
 */
public class ConnectionScreen extends BaseScreen implements NetworkListener {
    private static final float CREST_OUTER_SIZE = 56f;
    private static final float CREST_INNER_SIZE = 48f;
    private static final float PULSE_SIZE = 8f;

    /** Placeholder until an actual build/release pipeline exists (Epic G), not a real build stamp. */
    private static final String VERSION_LABEL = "v0.1.0-dev";

    private VisLabel statusLabel;
    private TextButton retryButton;
    private Image loadingPulse;
    private String testIdentityToken;

    // State machine
    private ConnectionState currentState = ConnectionState.IDLE;

    // Created once in the constructor, reused across setupUI() rebuilds, disposed in dispose().
    // setupUI() runs more than once (construction + every localization refresh), so creating these
    // inside it would leak a fresh set of textures per refresh.
    private Texture backgroundTexture;
    private Texture crestGoldTexture;
    private Texture crestCrimsonTexture;
    private Texture dividerTexture;
    private Texture pulseTexture;

    public ConnectionScreen(final CrimsonSky game, String testIdentityToken) {
        super(game);
        this.testIdentityToken = testIdentityToken;
        initializeConnectionVisuals();
        setupUI();
    }

    public ConnectionScreen(final CrimsonSky game) {
        super(game);
        initializeConnectionVisuals();
        setupUI();
    }

    /**
     * Creates this screen's own solid-color placeholder textures. Called once from the constructor —
     * see the field comment above for why this is deliberately not part of {@link #setupUI()}.
     */
    private void initializeConnectionVisuals() {
        backgroundTexture = TextureFactory.createSolidTexture(UiPalette.BACKGROUND);
        crestGoldTexture = TextureFactory.createSolidTexture(UiPalette.ACCENT_GOLD);
        crestCrimsonTexture = TextureFactory.createSolidTexture(UiPalette.ACCENT_CRIMSON);
        dividerTexture = TextureFactory.createSolidTexture(UiPalette.ACCENT_GOLD);
        pulseTexture = TextureFactory.createSolidTexture(UiPalette.TEXT_MUTED);
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
     * Updates UI elements based on current state: status text, retry visibility, and the pulsing
     * indicator (which only animates while there is something in flight).
     */
    private void updateUIForState() {
        String statusMessage = game.getLanguageManager().get(currentState.getMessageKey());
        statusLabel.setText(statusMessage);

        // Show retry button for error/disconnected states
        boolean showRetry = currentState == ConnectionState.FAILURE || currentState == ConnectionState.DISCONNECTED;
        retryButton.setVisible(showRetry);

        boolean showPulse = currentState == ConnectionState.CONNECTING || currentState == ConnectionState.AUTHENTICATING;
        loadingPulse.setVisible(showPulse);
        loadingPulse.clearActions();
        if (showPulse) {
            loadingPulse.getColor().a = 1f;
            loadingPulse.addAction(Actions.forever(Actions.sequence(
                Actions.fadeOut(0.6f), Actions.fadeIn(0.6f))));
        }
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
     * Full-bleed background, then a centered content column, then the corner-anchored version label.
     */
    private void setupUI() {
        stage.clear();

        Image background = new Image(new TextureRegionDrawable(new TextureRegion(backgroundTexture)));
        background.setFillParent(true);
        stage.addActor(background);

        VisTable root = new VisTable();
        root.setFillParent(true);
        stage.addActor(root);

        root.add(createCrest()).size(CREST_OUTER_SIZE, CREST_OUTER_SIZE).padBottom(24).row();

        VisLabel titleLabel = new VisLabel("CRIMSON SKY");
        titleLabel.setFontScale(2.4f);
        titleLabel.setColor(UiPalette.ACCENT_CRIMSON);
        root.add(titleLabel).padBottom(18).row();

        Image divider = new Image(new TextureRegionDrawable(new TextureRegion(dividerTexture)));
        root.add(divider).size(120, 2).padBottom(24).row();

        Table statusRow = new Table();
        loadingPulse = new Image(new TextureRegionDrawable(new TextureRegion(pulseTexture)));
        statusRow.add(loadingPulse).size(PULSE_SIZE, PULSE_SIZE).padRight(10);
        statusLabel = new VisLabel(game.getLanguageManager().get(currentState.getMessageKey()));
        statusLabel.setColor(UiPalette.TEXT_MUTED);
        statusRow.add(statusLabel);
        root.add(statusRow).padBottom(24).row();

        // Use UIButtonBuilder with ScreenAction for cleaner button setup
        retryButton = new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_RETRY"))
            .withStyle(accentButtonStyle)
            .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
            .withAction(this::handleRetryButtonClick)
            .build();
        retryButton.setVisible(false);
        root.add(retryButton).size(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT);

        VisLabel versionLabel = new VisLabel(VERSION_LABEL);
        versionLabel.setColor(UiPalette.TEXT_VERSION);
        versionLabel.setFontScale(0.85f);
        versionLabel.setPosition(20, 20);
        stage.addActor(versionLabel);

        updateUIForState();
    }

    /**
     * Generic placeholder crest: a gold square with a slightly smaller crimson square in front, both
     * rotated 45°, faking a bordered diamond without needing a bordered-drawable capability. The
     * inner square is wrapped in a {@link Container} because {@link Stack} otherwise stretches every
     * child to the full cell — the container is what keeps the gold "border" visible.
     */
    private Stack createCrest() {
        Image crestGold = new Image(new TextureRegionDrawable(new TextureRegion(crestGoldTexture)));
        crestGold.setOrigin(CREST_OUTER_SIZE / 2f, CREST_OUTER_SIZE / 2f);
        crestGold.setRotation(45);

        Image crestCrimson = new Image(new TextureRegionDrawable(new TextureRegion(crestCrimsonTexture)));
        crestCrimson.setOrigin(CREST_INNER_SIZE / 2f, CREST_INNER_SIZE / 2f);
        crestCrimson.setRotation(45);

        Stack crest = new Stack();
        crest.add(crestGold);
        crest.add(new Container<>(crestCrimson).size(CREST_INNER_SIZE));
        return crest;
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
            // Persisted settings are the source of truth for a returning player. Store them so
            // SettingsScreen shows real values instead of hardcoded defaults, apply the two with an
            // immediate visible effect (resolution + fullscreen), and prefer the DB language over
            // whatever ConfigurationManager set at boot.
            AccountSettings settings = response.settings() != null ? response.settings() : AccountSettings.createDefault();
            game.setAccountSettings(settings);
            DisplaySettings.apply(settings.resolution(), settings.fullscreen());
            game.getLanguageManager().setCurrentLang(settings.language());

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

    @Override
    public void dispose() {
        super.dispose();
        backgroundTexture.dispose();
        crestGoldTexture.dispose();
        crestCrimsonTexture.dispose();
        dividerTexture.dispose();
        pulseTexture.dispose();
    }
}
