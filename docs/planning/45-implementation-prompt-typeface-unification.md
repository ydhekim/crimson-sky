# Implementation prompt — unify every widget onto the Turkish-capable font

Follow-up to K10/prompt 43. That prompt's own grounding note (`docs/planning/02-user-stories.md`, K10 entry) found the supersampling fix only ever reached button text and the two title styles — every plain `VisLabel` in the app, which is most of the app's body text, is still silently bound to VisUI's own bundled font, not the Quicksand/Turkish-capable one. This prompt is the actual fix for that gap, plus a second, related correctness issue found while designing it.

## Root cause, precisely

`Skin` resolves a style's font reference when it *parses* the skin JSON. `VisUI.load()` parses VisUI's bundled default skin — including the default `Label.LabelStyle` and, very likely, the default `TextField`/`SelectBox`/`Window` styles — before `CrimsonSky.initializeUI()` ever registers `"default-font"`. Registering that key afterward only adds a new entry to the skin's resource map; it doesn't reach back and update a style object that already captured a direct reference to VisUI's own font during parsing. `BaseScreen`'s button styles were never affected only because `UiTheme` explicitly constructs fresh `TextButtonStyle` objects referencing `bodyFont` directly, at screen-construction time — well after `initializeUI()` runs — which sidesteps the parse-time freeze entirely rather than relying on skin lookup.

## Second issue found while designing the fix: `Label.setFontScale()` mutates shared state

Six call sites in the app currently do `new VisLabel(text); label.setFontScale(x)` to get a non-default text size (0.85, 1.1, 1.2, 1.2 again). This is a real, independently-documented libGDX quirk, not specific to this codebase — see [libgdx/libgdx#4232](https://github.com/libgdx/libgdx/issues/4232) and [#4346](https://github.com/libgdx/libgdx/issues/4346): `Label.setFontScale()` sets the underlying `BitmapFontData`'s scale directly rather than layering a per-label multiplier on top. Every label built from the *same* `Label.LabelStyle` shares the *same* `BitmapFont` object, so the moment any one of them calls `setFontScale()`, every other label using that identical font object is affected too — whichever label lays out last "wins" the shared scale. Once the fix above makes the default style actually point at `bodyFont`, this stops being a latent risk and becomes a real, active bug: any label calling `setFontScale()` would silently resize *every other plain label in the app* sharing that font object.

The fix for both issues is the same one: stop asking one shared `BitmapFont` object to serve multiple intended sizes at all. Give every distinct text role its own font object, pre-scaled once at load time, and never call `Label.setFontScale()` anywhere.

## 1. `AssetLoader` — three more pre-scaled font loads

All baked at the same 32px canvas already established for body/title (K10/K8) — consistency, and it keeps every text role on the identical "bake big, scale via `data.setScale()`" pattern rather than inventing a new bake-size decision per role:

```java
// Caption — smaller supporting text (achievement timestamps/XP tags, the connection screen's version
// label). Downscaled from the 32px bake, same as body, so it gets mipmapped minification too.
FreetypeFontLoader.FreeTypeFontLoaderParameter captionFontParameter = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
captionFontParameter.fontFileName = "fonts/Quicksand-Regular.ttf";
captionFontParameter.fontParameters.size = 32;
captionFontParameter.fontParameters.genMipMaps = true;
captionFontParameter.fontParameters.minFilter = Texture.TextureFilter.MipMapLinearLinear;
captionFontParameter.fontParameters.magFilter = Texture.TextureFilter.Linear;
captionFontParameter.fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS + TURKISH_CHARS;
assetManager.load("caption-font.ttf", BitmapFont.class, captionFontParameter);

// Emphasis — slightly larger text (character name labels on the row list and creation screen). The
// codebase had two close-but-different values here (1.1, 1.2 relative to body) from two separate passes
// that never coordinated a shared constant — consolidating to one value (see §3) rather than carrying
// two near-identical styles forward.
FreetypeFontLoader.FreeTypeFontLoaderParameter emphasisFontParameter = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
emphasisFontParameter.fontFileName = "fonts/Quicksand-Regular.ttf";
emphasisFontParameter.fontParameters.size = 32;
emphasisFontParameter.fontParameters.genMipMaps = true;
emphasisFontParameter.fontParameters.minFilter = Texture.TextureFilter.MipMapLinearLinear;
emphasisFontParameter.fontParameters.magFilter = Texture.TextureFilter.Linear;
emphasisFontParameter.fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS + TURKISH_CHARS;
assetManager.load("emphasis-font.ttf", BitmapFont.class, emphasisFontParameter);

// Title-lg — the one title that displayed larger than the standard 32px title bake (ConnectionScreen's
// splash, previously fontScale(1.2f) applied at the call site). Upscaled, so Linear only, no mipmap —
// matches how "title-font.ttf" is already treated.
FreetypeFontLoader.FreeTypeFontLoaderParameter titleLgFontParameter = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
titleLgFontParameter.fontFileName = "fonts/Quicksand-Regular.ttf";
titleLgFontParameter.fontParameters.size = 32;
titleLgFontParameter.fontParameters.minFilter = Texture.TextureFilter.Linear;
titleLgFontParameter.fontParameters.magFilter = Texture.TextureFilter.Linear;
titleLgFontParameter.fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS + TURKISH_CHARS;
assetManager.load("title-lg-font.ttf", BitmapFont.class, titleLgFontParameter);
```

## 2. `CrimsonSky.initializeUI()` — reassign every affected default style, register the new named styles

```java
private void initializeUI() {
    if (!VisUI.isLoaded()) {
        BitmapFont bodyFont = assetManager.get("default-font.ttf", BitmapFont.class);
        bodyFont.getData().setScale(0.5f);
        BitmapFont titleFont = assetManager.get("title-font.ttf", BitmapFont.class);
        BitmapFont titleLgFont = assetManager.get("title-lg-font.ttf", BitmapFont.class);
        titleLgFont.getData().setScale(1.2f);
        BitmapFont captionFont = assetManager.get("caption-font.ttf", BitmapFont.class);
        captionFont.getData().setScale(0.4375f); // 14px effective (32 * 0.4375)
        BitmapFont emphasisFont = assetManager.get("emphasis-font.ttf", BitmapFont.class);
        emphasisFont.getData().setScale(0.5625f); // 18px effective (32 * 0.5625)

        VisUI.load();

        // The actual K10 gap: these default styles resolved their font when VisUI.load() parsed its own
        // skin JSON, before any registration below runs. Adding "default-font" as a resource-map entry
        // doesn't retroactively fix a style that already captured a direct reference to VisUI's bundled
        // font — each one needs its font field reassigned explicitly, the same explicit-construction
        // approach BaseScreen's button styles already use (verify each class/field name below against the
        // actual VisUI version on the classpath — noted with the confidence level I actually have, not
        // asserted as certain).
        VisUI.getSkin().add("default-font", bodyFont, BitmapFont.class);
        VisUI.getSkin().get(Label.LabelStyle.class).font = bodyFont; // confirmed — this is K10's own finding
        VisUI.getSkin().get(VisTextField.VisTextFieldStyle.class).font = bodyFont; // verify field name
        VisUI.getSkin().get(VisSelectBox.VisSelectBoxStyle.class).font = bodyFont; // verify field name
        VisUI.getSkin().get(List.ListStyle.class).font = bodyFont; // the select box dropdown's own list
        VisUI.getSkin().get(Window.WindowStyle.class).titleFont = bodyFont; // VisDialog's title bar

        // Named, independently-scaled styles for every non-default text role. Never Label.setFontScale()
        // from here on — see the shared-BitmapFontData note above for why.
        Label.LabelStyle titleStyle = new Label.LabelStyle(VisUI.getSkin().get(Label.LabelStyle.class));
        titleStyle.font = titleFont;
        VisUI.getSkin().add("title", titleStyle, Label.LabelStyle.class);

        Label.LabelStyle titleLgStyle = new Label.LabelStyle(VisUI.getSkin().get(Label.LabelStyle.class));
        titleLgStyle.font = titleLgFont;
        VisUI.getSkin().add("title-lg", titleLgStyle, Label.LabelStyle.class);

        Label.LabelStyle captionStyle = new Label.LabelStyle(VisUI.getSkin().get(Label.LabelStyle.class));
        captionStyle.font = captionFont;
        VisUI.getSkin().add("caption", captionStyle, Label.LabelStyle.class);

        Label.LabelStyle emphasisStyle = new Label.LabelStyle(VisUI.getSkin().get(Label.LabelStyle.class));
        emphasisStyle.font = emphasisFont;
        VisUI.getSkin().add("emphasis", emphasisStyle, Label.LabelStyle.class);

        System.out.println("VisUI initialized: every default style now uses the Turkish-capable font family.");
    }
}
```

> **As-built correction (the §2 block above is the pre-implementation guess; the shipped code differs).** Checked against the real skin JSON in `vis-ui:1f8b37a24b` (`com/kotcrab/vis/ui/skin/x1/uiskin.json`) instead of trusting the guessed names:
> - `VisSelectBox.VisSelectBoxStyle` **does not exist**. `VisSelectBox` extends Scene2D's `SelectBox` and VisUI ships no style subclass for it — use `SelectBox.SelectBoxStyle`.
> - `List.ListStyle` is **not** the dropdown's list style, so the line above wouldn't have fixed the dropdown. The skin declares `SelectBoxStyle` with an *inline* `listStyle: {font: default-font, …}` object; an open dropdown reads that nested instance, while `List$ListStyle: default` is a separate object. The shipped code assigns all three (`selectBoxStyle.font`, `selectBoxStyle.listStyle.font`, and `List.ListStyle`'s own).
> - `VisCheckBox.VisCheckBoxStyle` exists with a `default` entry and a `font` field, as assumed.
> - `Window.WindowStyle`'s `default` entry is the right target: decompiling shows `new VisDialog(title)` → `VisWindow(title, true)` → style `"default"`, not the skin's separate `"dialog"` entry.

The `VisTextField`/`VisSelectBox`/`List`/`Window` lines are grounded in what's actually constructed in this codebase (`SettingsScreen`'s language/resolution `VisSelectBox`es and fullscreen `VisCheckBox`, `CharacterCreationScreen`'s `VisTextField`, both screens' `VisDialog` title bars) sharing the identical root cause as `Label` — not a guess that they're affected, but the exact class/field names should be confirmed against the real VisUI API at compile time rather than trusted verbatim from this prompt, since I can't compile-check them from here. `VisCheckBox`'s own displayed text (`fullscreenCheckBox`) is currently an empty string, so it's low-impact today, but the style fix is still worth applying for whenever that changes.

## 3. Screen call sites — replace every `setFontScale()` with a named style

- `ConnectionScreen.java:155-159` — construct with `new VisLabel("CRIMSON SKY", "title-lg")`, delete the `setFontScale(1.2f)` line and its comment.
- `ConnectionScreen.java:183-185` — construct with `new VisLabel(VERSION_LABEL, "caption")`, delete `setFontScale(0.85f)`.
- `AchievementsScreen.java:218` — construct `timeLabel` with the `"caption"` style, delete `setFontScale(0.85f)`.
- `AchievementsScreen.java:224` — same for `xpLabel`.
- `CharacterRowBuilder.java:81` — construct `nameLabel` with the `"emphasis"` style, delete `setFontScale(1.2f)`.
- `CharacterCreationScreen.java:283` — construct `nameLabel` with the `"emphasis"` style, delete `setFontScale(1.1f)`. This is the one deliberate visual consolidation in this pass: this label previously rendered slightly smaller (1.1×) than `CharacterRowBuilder`'s (1.2×) — now both use the identical `"emphasis"` style. If the two should actually stay visually distinct, flag it and a second named style (e.g. `"emphasis-sm"`) is a one-line addition, not a redesign.

Every current call site already constructs its `VisLabel` fresh (none reuse a cached instance across languages in a way that would miss the style name), so this is a mechanical swap at each site, not a structural change.

## 4. Testing / Definition of Done

Claude Code's job stops at automated checks — no manual client-driving:

1. `gradlew.bat build` — confirm it compiles, including whichever exact `VisTextField`/`VisSelectBox`/`List`/`Window` style class and field names the actual VisUI version resolves to (adjust from this prompt's best-guess names if the compiler disagrees).
2. `gradlew.bat test` — confirm nothing existing broke (no test currently covers font/style wiring).
3. Confirm zero remaining calls to `Label.setFontScale(` anywhere in `core/src/main/java` (`grep -rn "setFontScale" core/src/main/java` should only show the two comments explaining why it's no longer used, not a live call).

Manual verification — I'll check this myself:

- Every label across every screen (not just titles and buttons) now visibly renders in the Quicksand-family typeface, not VisUI's bundled one — should be obvious side by side since the two have a noticeably different letterform.
- Achievement timestamps/XP tags, the connection screen's version label, and both character-name emphasis labels are all still legible at their (slightly adjusted) sizes — nothing reads as accidentally huge or illegibly tiny.
- Switch language to Turkish and confirm ı/ş/ğ/ö/ü/ç render correctly in a `VisSelectBox` (Settings' language/resolution dropdowns) and a `VisDialog` title bar (the delete-character confirmation) specifically — these are the two widget types most likely to have been silently falling back to a non-Turkish-capable font before this fix, and it's worth confirming they actually had a real, visible gap rather than an already-fine edge case.

Definition of done: one consistent typeface across every widget in the app; zero remaining `Label.setFontScale()` calls; build and tests green.
