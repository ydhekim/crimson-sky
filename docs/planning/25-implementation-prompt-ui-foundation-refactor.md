# Implementation prompt — UI foundation refactor (prompt (c) of the M4 pre-work sequence)

Third of the agreed M4 prep steps: (a) Turkish-font decision — done, (b) foundation cleanup (prompt 24) — merged, (c) this prompt, (d) shared M4 UI/UX design note (see `01-system-design-combat-engine.md` §24, added alongside this prompt). D1a (prompt 23) stays shelved until this lands, since it's built on the exact APIs this prompt replaces.

Scope: remove genuinely dead code, replace prompt 24's temporary button-style stand-in with a real (still placeholder-visual, zero-external-asset) generated theme, and give every screen's button sizes one named source of truth instead of scattered literals. This is a mechanical refactor — it should not change how any screen looks or behaves beyond giving buttons real up/down/over states instead of VisUI's bundled default. Reconciling `CharacterCreationScreen`'s divergent sizes (150×40, 90×30) with the 200×40 convention the other three screens already share is a visual-design call, not a refactor — leave those values as they are, just named, and flag the inconsistency for the screen-by-screen design pass that follows this prompt.

## 1. Remove `ScreenManager` — verify both ends before deleting

`core/screen/ScreenManager.java` is a singleton screen manager superseded by `ScreenFactory`/`ScreenRouter` (DI-based, constructed once in `CrimsonSky.create()`) — but it is not fully inert. `Main.java` (the class `Lwjgl3Launcher` actually instantiates — `new Main()`, not `new CrimsonSky()` directly) calls `ScreenManager.getInstance().initialize(this)` in `create()` and `.dispose()` in `dispose()`. Neither call does anything observable — `initialize` just stores a `Game` reference, `showScreen`/`createScreen` are never invoked from anywhere else (confirmed by grep) — but both lines need to go, not just the file:

```java
// Main.java
public class Main extends CrimsonSky {
    // create()/dispose() overrides removed entirely — CrimsonSky's own lifecycle (which already
    // constructs and owns ScreenFactory/ScreenRouter) is the sole screen-navigation path. See
    // ScreenRouter's own javadoc: "Uses dependency injection (as opposed to singleton ScreenManager)."
}
```

If `Main` ends up with no body at all beyond extending `CrimsonSky`, that's fine — it exists only because `Lwjgl3Launcher` needs a concrete `ApplicationListener` and this was presumably meant to be a per-platform hook point; leave the empty class rather than collapsing `Lwjgl3Launcher` to instantiate `CrimsonSky` directly, since that's outside this prompt's scope.

Delete `ScreenManager.java`.

## 2. `UiMetrics` — name every button size currently in use

New file, `core/src/main/java/io/github/ydhekim/crimson_sky/ui/UiMetrics.java`:

```java
package io.github.ydhekim.crimson_sky.ui;

/**
 * Named button/sizing constants, extracted from what every screen already uses (see prompt 25) —
 * this doesn't change any screen's current appearance, it gives the numbers already in use a single
 * source of truth instead of repeated literals. NAV_BUTTON is the de facto standard three of four
 * screens already agree on; FACTION_BUTTON/SMALL_BUTTON are CharacterCreationScreen-specific sizes
 * that diverge from it — flagged, not resolved, here (see system design §24 / the screen-by-screen
 * design pass this prompt precedes).
 */
public final class UiMetrics {
    private UiMetrics() {}

    public static final float NAV_BUTTON_WIDTH = 200f;
    public static final float NAV_BUTTON_HEIGHT = 40f;

    public static final float DIALOG_BUTTON_WIDTH = 96f;
    public static final float DIALOG_BUTTON_HEIGHT = 32f;

    public static final float ICON_BUTTON_SIZE = 16f;

    // CharacterCreationScreen-specific — not yet reconciled with NAV_BUTTON, see class javadoc.
    public static final float FACTION_BUTTON_WIDTH = 150f;
    public static final float FACTION_BUTTON_HEIGHT = 40f;
    public static final float SMALL_BUTTON_WIDTH = 90f;
    public static final float SMALL_BUTTON_HEIGHT = 30f;
}
```

Replace every `.withSize(<literal>, <literal>)` call across `CharactersScreen`, `AchievementsScreen`, `SettingsScreen`, and `CharacterCreationScreen` with the matching `UiMetrics` constant — same numeric values, just named. (`CombatScreen` doesn't exist on this branch — prompt 23 is shelved — so nothing to migrate there yet; when 23 is revisited it should be written against `UiMetrics` from the start.)

## 3. `UiTheme` — replace prompt 24's stand-in with a real generated style

`BaseScreen.setupButtonStyle()` currently does this (prompt 24's deliberately temporary fix):

```java
TextButton.TextButtonStyle defaultStyle = VisUI.getSkin().get(TextButton.TextButtonStyle.class);
customButtonStyle = defaultStyle;
squareButtonStyle = defaultStyle;
```

Both fields point at the same VisUI-bundled style — no visual distinction between a full-width nav button and a small icon-square button. Replace with a small factory that builds real `TextButtonStyle`s from solid-color pixmaps, following the exact pattern `TextureFactory` already uses for panel/row/avatar placeholders — same "code-generated, no external asset" philosophy, just extended to button *styles* instead of only background textures.

New file, `core/src/main/java/io/github/ydhekim/crimson_sky/ui/UiTheme.java`:

```java
package io.github.ydhekim.crimson_sky.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.kotcrab.vis.ui.VisUI;

/**
 * Generates {@link TextButton.TextButtonStyle}s from solid-color placeholder textures (system design
 * §24) — no external skin/atlas, same spirit as {@link TextureFactory}'s panel/row textures and
 * {@link io.github.ydhekim.crimson_sky.combat.CombatVisualFactory}'s action swatches. Two distinct
 * styles instead of prompt 24's single stand-in: a standard button and a smaller, flatter one for
 * icon-square buttons (stat +/-, future loadout priority arrows — see §24).
 * <p>
 * Textures are owned by this instance; call {@link #dispose()} once per screen (BaseScreen already
 * does — see its dispose()).
 */
public class UiTheme implements Disposable {

    private final Array<com.badlogic.gdx.graphics.Texture> textures = new Array<>();

    public TextButton.TextButtonStyle standardButtonStyle(BitmapFont font) {
        return buildStyle(font, new Color(0.25f, 0.25f, 0.28f, 1f), new Color(0.35f, 0.35f, 0.4f, 1f),
            new Color(0.18f, 0.18f, 0.2f, 1f));
    }

    public TextButton.TextButtonStyle iconButtonStyle(BitmapFont font) {
        return buildStyle(font, new Color(0.3f, 0.3f, 0.33f, 1f), new Color(0.4f, 0.4f, 0.45f, 1f),
            new Color(0.22f, 0.22f, 0.25f, 1f));
    }

    private TextButton.TextButtonStyle buildStyle(BitmapFont font, Color up, Color over, Color down) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = font;
        style.up = drawable(up);
        style.over = drawable(over);
        style.down = drawable(down);
        style.fontColor = Color.WHITE;
        return style;
    }

    private TextureRegionDrawable drawable(Color color) {
        var texture = TextureFactory.createSolidTexture(color);
        textures.add(texture);
        return new TextureRegionDrawable(new com.badlogic.gdx.graphics.g2d.TextureRegion(texture));
    }

    @Override
    public void dispose() {
        for (var texture : textures) {
            texture.dispose();
        }
        textures.clear();
    }
}
```

`BaseScreen` changes: add a `protected UiTheme uiTheme;` field, construct it in the constructor, and rewrite `setupButtonStyle()`:

```java
private void setupButtonStyle() {
    uiTheme = new UiTheme();
    BitmapFont font = VisUI.isLoaded() ? VisUI.getSkin().getFont("default-font") : null;
    customButtonStyle = uiTheme.standardButtonStyle(font);
    squareButtonStyle = uiTheme.iconButtonStyle(font);
}
```

(Drop the `VisUI.isLoaded()` guard around the whole method if `font` can be null-safe inside `TextButtonStyle` — check how VisUI/Scene2D handles a null `font` on a style before deciding; if it doesn't tolerate null, keep the guard and skip building styles when VisUI isn't loaded, matching the existing defensive pattern.) Add `uiTheme.dispose()` to `BaseScreen.dispose()` alongside `panelBackgroundTexture.dispose()`.

## 4. Debug-log cleanup

Two files have leftover Turkish-language debug logging mixed into otherwise-English code — noise from earlier development, not user-facing text (that already correctly goes through `LanguageManager`):

- `CharactersScreen.onLocalizationResponse()`: the per-key logging loop (`"Gelen veri boyutu..."`, the `forEach` logging every translation key/value) and the Turkish log/error strings around it. Collapse to one English line confirming the translation count, matching every other screen's logging style (e.g. `Gdx.app.log("CharactersScreen", "Localization applied: " + response.translations().size() + " keys.")`), and drop the per-key loop entirely — it was clearly a one-time debugging aid, not something worth keeping at this volume long-term.
- `AchievementsScreen.populateAchievements()`'s javadoc comment (`"Sunucudan veri geldiğinde listeyi dinamik olarak doldurur"`) and the couple of inline Turkish comments nearby — translate to English, matching every other method's javadoc style in this file.

## 5. Testing / Definition of Done

No server changes; no new JUnit coverage needed (this is layout/style plumbing, not decision logic — nothing here produces a value worth asserting in a test, per the presenter/pure-logic testing convention system design §24 lays out for *future* screens).

1. `gradlew.bat lwjgl3:run` — every screen (MainMenu, Characters, Character Creation, Achievements, Settings) still navigates cleanly.
2. Buttons now show a visible up/over/down color change on hover/press (previously flat, since prompt 24's stand-in had no distinct states) — confirms `UiTheme` is actually wired in, not just present.
3. `grep -rn "withSize(2\|withSize(9\|withSize(1[56]0\|withSize(3[02]" core/src/main/java` (or similar) turns up nothing outside `UiMetrics.java` itself — confirms the literal-to-constant migration is complete.
4. `ScreenManager.java` no longer exists; `Main.java` compiles without it.
5. No remaining Turkish-language log/comment strings in `CharactersScreen`/`AchievementsScreen` (English project convention, matching every other screen).

Definition of done: `ScreenManager` and its two call sites in `Main.java` are gone; every screen's button sizes reference `UiMetrics`; every screen's buttons are styled via `UiTheme` (visibly distinct hover/press states, no VisUI-bundled-skin dependency); the two debug-log files read in consistent English. `CharacterCreationScreen`'s size reconciliation (150×40/90×30 vs. the 200×40 convention) is explicitly deferred to the screen-by-screen design pass, not resolved here.
