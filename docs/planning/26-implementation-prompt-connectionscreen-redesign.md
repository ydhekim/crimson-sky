# Implementation prompt — ConnectionScreen redesign (first screen of the M4 screen-by-screen pass)

First concrete screen from the design discussion following prompts 24/25 (foundation cleanup, UI theme refactor — both merged). Confirmed current state before writing this: `BaseScreen` already constructs a `UiTheme` (via `setupButtonStyle()`) giving every screen `customButtonStyle`/`squareButtonStyle`, and `UiMetrics` centralizes button sizes. `ConnectionScreen` is the actual first screen a player sees — `CrimsonSky.create()` calls `setScreen(new ConnectionScreen(...))` before the Main Menu ever loads — and today it's a boxed gray panel identical to every other screen: a `"CRIMSON SKY"` title, a state-driven status label (`ConnectionState` → `LanguageManager`), and a Retry button hidden except on `FAILURE`/`DISCONNECTED`. No branding, no visual distinction from Settings or Achievements.

Agreed design (mockup reviewed and approved): full-bleed dark background (no boxed panel — this is the one screen that should feel like an entry moment, not a utility panel), a generic placeholder crest (rotated gold-bordered crimson square) above the title, the title itself in a crimson accent color with a gold divider beneath it, a small pulsing indicator next to the status text while connecting/authenticating, a crimson-accented Retry button, and a small muted version label in the bottom-left corner. Solid colors only — no gradients (`TextureFactory` only generates solid-color textures today; adding gradient support is out of scope here). Nothing here touches any other screen — `MainMenuScreen`'s still-unlocalized `"MAIN MENU"` title, for instance, is a known separate item for whenever that screen's turn comes.

## 1. `UiPalette` — named colors, sibling to `UiMetrics`

New file, `core/src/main/java/io/github/ydhekim/crimson_sky/ui/UiPalette.java`:

```java
package io.github.ydhekim.crimson_sky.ui;

import com.badlogic.gdx.graphics.Color;

/**
 * Named accent colors (system design §24's "apply the accent palette now" decision), sibling to
 * {@link UiMetrics}. Crimson is the primary brand accent (buttons, titles, active states); gold is
 * secondary (dividers, highlights, reward/currency moments). Blue/silver for Skyborn-specific
 * contexts (e.g. the faction selection screen) are intentionally not here yet — added when that
 * screen's turn comes, not guessed at now.
 */
public final class UiPalette {
    private UiPalette() {}

    public static final Color BACKGROUND = new Color(0.078f, 0.071f, 0.063f, 1f);   // #141210
    public static final Color ACCENT_CRIMSON = new Color(0.542f, 0.165f, 0.165f, 1f); // #8A2A2A
    public static final Color ACCENT_CRIMSON_HOVER = new Color(0.64f, 0.24f, 0.22f, 1f);
    public static final Color ACCENT_CRIMSON_PRESSED = new Color(0.42f, 0.11f, 0.11f, 1f);
    public static final Color ACCENT_GOLD = new Color(0.788f, 0.604f, 0.290f, 1f);   // #C99A4A
    public static final Color TEXT_MUTED = new Color(0.78f, 0.77f, 0.74f, 1f);
    public static final Color TEXT_VERSION = new Color(0.353f, 0.337f, 0.306f, 1f);
}
```

(Hex comments are the values from the reviewed mockup — keep them if convenient, adjust only if they look wrong once actually rendered; matching the mockup exactly isn't more important than it reading well on an actual monitor.)

## 2. `UiTheme.accentButtonStyle(font)`

Add alongside the existing `standardButtonStyle`/`iconButtonStyle` in `UiTheme.java` — same `buildStyle(...)` helper, just fed `UiPalette`'s crimson trio instead of new inline colors:

```java
public TextButton.TextButtonStyle accentButtonStyle(BitmapFont font) {
    return buildStyle(font, UiPalette.ACCENT_CRIMSON, UiPalette.ACCENT_CRIMSON_HOVER,
        UiPalette.ACCENT_CRIMSON_PRESSED);
}
```

This is deliberately reusable beyond `ConnectionScreen` — any future "primary CTA" button (e.g. an eventual real Attack/Play button) can reach for this same style rather than each screen inventing its own crimson.

## 3. `ConnectionScreen` — full-bleed layout, not a boxed panel

Replace `setupUI()`'s reliance on `createMainContentPanel()` with a full-stage root `Table`. Split texture creation (once, constructor-time) from actor layout (rebuilt on every `setupUI()`/`refreshUI()` call) — the existing code already calls `setupUI()` more than once (construction, and again on localization refresh), so any texture created *inside* `setupUI()` would leak a duplicate on every refresh unless it's created once and reused.

```java
public class ConnectionScreen extends BaseScreen implements NetworkListener {
    private VisLabel statusLabel;
    private com.badlogic.gdx.scenes.scene2d.ui.TextButton retryButton;
    private Image loadingPulse;
    private String testIdentityToken;
    private ConnectionState currentState = ConnectionState.IDLE;

    // Created once in the constructor, reused across setupUI() rebuilds, disposed in dispose().
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

    private void initializeConnectionVisuals() {
        backgroundTexture = TextureFactory.createSolidTexture(UiPalette.BACKGROUND);
        crestGoldTexture = TextureFactory.createSolidTexture(UiPalette.ACCENT_GOLD);
        crestCrimsonTexture = TextureFactory.createSolidTexture(UiPalette.ACCENT_CRIMSON);
        dividerTexture = TextureFactory.createSolidTexture(UiPalette.ACCENT_GOLD);
        pulseTexture = TextureFactory.createSolidTexture(UiPalette.TEXT_MUTED);
    }
```

`setupUI()` rebuild — full-bleed background first, then a centered content `Table` (crest → title → divider → status row → retry button), then the version label added directly to the stage (not the centered table, since it's corner-anchored):

```java
    private void setupUI() {
        stage.clear();

        Image background = new Image(new TextureRegionDrawable(new TextureRegion(backgroundTexture)));
        background.setFillParent(true);
        stage.addActor(background);

        VisTable root = new VisTable();
        root.setFillParent(true);
        stage.addActor(root);

        // Crest: gold square behind, slightly smaller crimson square in front, both rotated 45°,
        // faking a bordered diamond without needing an actual bordered-drawable capability.
        Stack crest = new Stack();
        Image crestGold = new Image(new TextureRegionDrawable(new TextureRegion(crestGoldTexture)));
        Image crestCrimson = new Image(new TextureRegionDrawable(new TextureRegion(crestCrimsonTexture)));
        crestGold.setOrigin(28, 28);
        crestGold.setRotation(45);
        crestCrimson.setOrigin(24, 24);
        crestCrimson.setRotation(45);
        crest.add(crestGold);
        crest.add(crestCrimson);
        root.add(crest).size(56, 56).padBottom(24).row();

        VisLabel titleLabel = new VisLabel("CRIMSON SKY");
        titleLabel.setFontScale(2.4f);
        titleLabel.setColor(UiPalette.ACCENT_CRIMSON);
        root.add(titleLabel).padBottom(18).row();

        Image divider = new Image(new TextureRegionDrawable(new TextureRegion(dividerTexture)));
        root.add(divider).size(120, 2).padBottom(24).row();

        Table statusRow = new Table();
        loadingPulse = new Image(new TextureRegionDrawable(new TextureRegion(pulseTexture)));
        loadingPulse.setSize(8, 8);
        statusRow.add(loadingPulse).size(8, 8).padRight(10);
        statusLabel = new VisLabel(game.getLanguageManager().get(currentState.getMessageKey()));
        statusLabel.setColor(UiPalette.TEXT_MUTED);
        statusRow.add(statusLabel);
        root.add(statusRow).padBottom(24).row();

        retryButton = new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_RETRY"))
            .withStyle(uiTheme.accentButtonStyle(VisUI.getSkin().getFont("default-font")))
            .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
            .withAction(this::handleRetryButtonClick)
            .build();
        retryButton.setVisible(false);
        root.add(retryButton);

        VisLabel versionLabel = new VisLabel("v0.1.0-dev");
        versionLabel.setColor(UiPalette.TEXT_VERSION);
        versionLabel.setFontScale(0.85f);
        versionLabel.setPosition(20, 20);
        stage.addActor(versionLabel);

        updateUIForState();
    }
```

(`Stack` is `com.badlogic.gdx.scenes.scene2d.ui.Stack` — layers actors on top of each other at the same cell bounds, the simplest way to composite the two rotated squares. Import it alongside the other Scene2D imports.)

Note the version string is a hardcoded placeholder (`"v0.1.0-dev"`) — there's no real build-versioning mechanism in this project yet, and building one is well out of scope here. Treat this exactly like `AttackRejectedResponse`'s `MessageCode`-name convention or any other "good enough for now" placeholder: correct to ship as-is, worth revisiting once an actual release/build pipeline exists (Epic G territory, not now).

## 4. Pulsing indicator — start/stop tied to connection state

`updateUIForState()` already toggles `retryButton.setVisible(...)`; extend it to start/stop the pulse:

```java
private void updateUIForState() {
    String statusMessage = game.getLanguageManager().get(currentState.getMessageKey());
    statusLabel.setText(statusMessage);

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
```

(`Actions` is `com.badlogic.gdx.scenes.scene2d.actions.Actions` — already the convention system design §10 mandates for placeholder animation elsewhere, so this is consistent, not a new pattern.)

## 5. Disposal

Add a `dispose()` override — `BaseScreen.dispose()` already handles `stage`/`panelBackgroundTexture`/`uiTheme`, but the five screen-specific textures created in `initializeConnectionVisuals()` are this screen's own and need their own cleanup:

```java
@Override
public void dispose() {
    super.dispose();
    backgroundTexture.dispose();
    crestGoldTexture.dispose();
    crestCrimsonTexture.dispose();
    dividerTexture.dispose();
    pulseTexture.dispose();
}
```

## 6. Testing / Definition of Done

No server changes; no new JUnit coverage (pure layout/visual work, same as prompts 24/25 — nothing here produces a value worth asserting per the presenter/pure-logic testing convention in system design §24, since there's no decision logic here, only presentation).

1. `gradlew.bat lwjgl3:run` — confirm the connection screen now shows: full-bleed dark background (no boxed panel), the crest above the title, "CRIMSON SKY" in crimson, the gold divider, status text in muted gray with a visibly pulsing dot while connecting/authenticating (pulse stops and hides once `SUCCESS`/before the Main Menu transition), and the version label in the bottom-left corner.
2. Force a failure path (stop the server before launching, or point at a wrong port temporarily) — confirm the crimson-accented Retry button appears and the pulse stops/hides.
3. Trigger a localization refresh (language switch, if reachable from Settings) and confirm the screen rebuilds cleanly with no duplicate/leftover textures — check the LibGDX log for texture-leak warnings after a few refresh cycles.
4. Confirm the transition to `MainMenuScreen` on successful login still works unchanged.

Definition of done: `ConnectionScreen` matches the reviewed design (full-bleed, crest, crimson title, gold divider, pulsing status indicator, crimson Retry button, version label); `UiPalette` and `UiTheme.accentButtonStyle` exist as shared, reusable additions (not one-off inline colors in `ConnectionScreen` itself); all five screen-specific textures are disposed correctly with no leak across repeated `setupUI()` rebuilds. No other screen changes — Main Menu's unlocalized title and every other screen's still-neutral styling are explicitly out of scope, next up whenever their turn comes.
