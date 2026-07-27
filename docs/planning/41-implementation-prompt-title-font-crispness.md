# Implementation prompt — crisp title text (dedicated title-size font)

Second issue raised alongside the CharactersScreen localization screenshot: labels look "very blurry." Grounded in `AssetLoader`/`CrimsonSky` — this is a font issue, not a rendering/viewport bug.

## Root cause

`AssetLoader.preloadAssets()` (`core/asset/AssetLoader.java:52`) bakes exactly one FreeType font size: 16px. Every screen's title label uses that same 16px-baked font and stretches it via `VisLabel.setFontScale(2f)` (most screens) or `2.4f` (`ConnectionScreen`'s splash title) — grep-confirmed at:

- `screen/ConnectionScreen.java:156` — `2.4f`
- `screen/MainMenuScreen.java:41` — `2f`
- `screen/CharactersScreen.java:61` — `2f`
- `screen/AchievementsScreen.java:118` — `2f`
- `screen/SettingsScreen.java:54` — `2f`

Bitmap fonts (what `FreeTypeFontGenerator` produces) are baked raster glyph textures, not vector outlines — magnifying one past its baked size always blurs, regardless of `minFilter`/`magFilter` settings (both already `Linear`, which smooths the blur rather than eliminating it). A 16px glyph stretched 2–2.4× is visibly soft; this is what the screenshot shows.

Smaller-than-1 scale factors (`AchievementsScreen`'s `timeLabel`/`xpLabel` at `0.85f`, `ConnectionScreen`'s `versionLabel` at `0.85f`) are *downscaling*, not upscaling — meaningfully less lossy, and out of scope here. Same for the two name-label mid-scales (`CharacterRowBuilder.java:81` at `1.2f`, `CharacterCreationScreen.java:284` at `1.1f`) — mild enough not to be the "very blurry" the screenshot shows; not touched by this pass.

## Fix: a second, title-sized baked font

Add a second `BitmapFont` load in `AssetLoader.preloadAssets()`, same source file and character set as the existing body font, baked at 32px instead of 16px:

```java
FreetypeFontLoader.FreeTypeFontLoaderParameter titleFontParameter = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
titleFontParameter.fontFileName = "fonts/Quicksand-Regular.ttf";
titleFontParameter.fontParameters.size = 32;
titleFontParameter.fontParameters.minFilter = Texture.TextureFilter.Linear;
titleFontParameter.fontParameters.magFilter = Texture.TextureFilter.Linear;
titleFontParameter.fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS + TURKISH_CHARS;
assetManager.load("title-font.ttf", BitmapFont.class, titleFontParameter);
```

(The string passed to `assetManager.load(...)` is just the AssetManager's internal registry key here, not a real file on disk — `FreetypeFontLoader` generates from `parameter.fontFileName` regardless of that key, which is exactly how the existing `"default-font.ttf"` load already works despite there being no literal file by that name. Same trick, a second independent key. Verify this assumption holds against the installed LibGDX/gdx-freetype version if `gradlew.bat build`/a load-time exception says otherwise — flagging rather than asserting with total certainty, since I can't compile-check this from planning.)

32px covers the common `2f` case (32 = 16 × 2, an exact bake at the size those titles were already targeting) at `fontScale(1f)` — no scaling, fully crisp. `ConnectionScreen`'s `2.4f` splash title becomes `fontScale(1.2f)` on the new 32px font (32 × 1.2 = 38.4, the same effective size as before) — a much milder 1.2× upscale than the previous 2.4×, so still meaningfully crisper even though not perfectly pixel-exact. Adding a third baked size just for one screen's splash title isn't worth the extra asset for that residual gap.

## Register a "title" label style

`CrimsonSky.initializeUI()` (`CrimsonSky.java:68`) currently only registers the body font onto the skin as `"default-font"`. Add the title font plus a dedicated `Label.LabelStyle` so screens select it by style name rather than by mutating a shared style object (mutating the *shared* default `LabelStyle`'s `.font` field in place would silently reflow every plain label in the app, not just titles — same shared-mutable-style hazard already flagged for `VisProgressBar` in prompt 39):

```java
private void initializeUI() {
    if (!VisUI.isLoaded()) {
        BitmapFont bodyFont = assetManager.get("default-font.ttf", BitmapFont.class);
        BitmapFont titleFont = assetManager.get("title-font.ttf", BitmapFont.class);

        VisUI.load();
        VisUI.getSkin().add("default-font", bodyFont, BitmapFont.class);
        VisUI.getSkin().add("title-font", titleFont, BitmapFont.class);

        Label.LabelStyle titleStyle = new Label.LabelStyle(VisUI.getSkin().get(Label.LabelStyle.class));
        titleStyle.font = titleFont;
        VisUI.getSkin().add("title", titleStyle, Label.LabelStyle.class);

        System.out.println("VisUI initialized with bundled skin + custom fonts (body 16px, title 32px).");
    }
}
```

(`import com.badlogic.gdx.scenes.scene2d.ui.Label;` needed. Verify `VisLabel` exposes a `(CharSequence text, String styleName)` constructor that resolves against `Label.LabelStyle` in the installed VisUI version — this is the standard Scene2D `Label` constructor shape and `VisLabel` doesn't normally override style-name resolution, so it should resolve correctly, but confirm at compile time. If that constructor isn't available for any reason, `new VisLabel(text)` followed by `label.setStyle(titleStyle)` is an equivalent fallback — `setStyle` is `Label`'s own public method, always available.)

## Sweep every title label

Replace `new VisLabel(...)` + `.setFontScale(...)` with the new `"title"` style, dropping the scale call entirely (font is now baked at its actual display size):

- `ConnectionScreen.java:155-156` — `new VisLabel(title, "title")`, then keep `titleLabel.setFontScale(1.2f)` (the one residual case, see above).
- `MainMenuScreen.java:40-41`
- `CharactersScreen.java:60-61`
- `AchievementsScreen.java:117-118`
- `SettingsScreen.java:53-54`
- `CharacterCreationScreen.java` (from prompt 39, around its `titleLabel` construction) — **only if prompt 39 has already merged** by the time this lands; if not, skip it here and note it as a immediate follow-up once 39 does merge, since prompt 39 was written before this fix existed and still uses the old `setFontScale(2f)` pattern.

Each site keeps its existing `.setColor(...)`/`.setAlignment(Align.center)` calls — only the construction + scale-removal changes.

## Testing / Definition of Done

1. `gradlew.bat build` — confirm it compiles, including the new `AssetLoader` load call and `CrimsonSky.initializeUI()`'s new style registration.
2. `gradlew.bat test` — confirm nothing broke (no existing test touches font/style setup).

Manual verification — I'll check this myself:

- Every screen's title reads visibly sharper than before, at both 100% and any higher OS display scaling.
- No screen's title looks unexpectedly *larger* or *smaller* than its previous size (font is baked at the intended display size now, not scaled — a mismatch here would mean the wrong bake size was picked for that screen).

Definition of done: build is green; six title labels (all current screens minus whichever CharacterCreationScreen state applies at merge time) use the new dedicated title font at `fontScale(1f)` (or `1.2f` only for `ConnectionScreen`'s splash), with the visual size preserved from before.
