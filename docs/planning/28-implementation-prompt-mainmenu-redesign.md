# Implementation prompt — MainMenuScreen redesign

Second screen in the M4 screen-by-screen design pass (after ConnectionScreen, prompts 26/27). Confirmed the schema sketch from the navigation-schema discussion doesn't change Main Menu itself — its three destinations (Characters, Achievements, Settings) plus Exit are unchanged; the new Character Hub/Gear/Skills/Quests/Ladder structure all lives one level deeper, under Characters.

Agreed design: keep the boxed panel (`createMainContentPanel()`) — unlike Connection, this is the hub you return to constantly, not a one-time entry moment, so it stays visually distinct from Connection's full-bleed splash. Two changes: the title moves from a hardcoded, unlocalized `"MAIN MENU"` string to a localized, crimson-accented label (tying it to Connection's identity without going full-bleed), and the four buttons get visual hierarchy instead of being visually identical — Characters becomes the crimson-accented primary action (it's the actual gateway to playing), Achievements/Settings/Exit stay on the neutral standard style.

## 1. Found while grounding this: `accentButtonStyle` needs to move up to `BaseScreen`

`ConnectionScreen`'s Retry button (prompt 26) currently does `uiTheme.accentButtonStyle(VisUI.getSkin().getFont("default-font"))` *inside* `setupUI()`. `UiTheme.accentButtonStyle(...)` calls `buildStyle(...)`, which calls `drawable(...)` three times — creating three brand-new `Texture`s on every call, added to `uiTheme`'s internal list. Since `setupUI()` runs more than once per screen instance (construction, and again on every localization refresh), every refresh silently adds three more textures without disposing the previous three. They aren't a *permanent* leak — `BaseScreen.dispose()` eventually disposes everything `uiTheme` ever accumulated when the screen itself is disposed — but they accumulate for the screen's entire cached lifetime, which for `ConnectionScreen`/`MainMenuScreen` (both long-lived, cached by `ScreenRouter`) could mean real, avoidable GPU memory waste across a long session with a few language switches. This is exactly the same class of bug prompt 26 already fixed once for `ConnectionScreen`'s own five dedicated textures (`initializeConnectionVisuals()`, built once in the constructor) — just missed for the shared `uiTheme.accentButtonStyle()` call itself.

Fix it at the source rather than patching each screen separately: promote `accentButtonStyle` to `BaseScreen`, built once in `setupButtonStyle()` alongside `customButtonStyle`/`squareButtonStyle` — every screen gets a ready-made, leak-safe accent style for free, the same way the other two already work.

`BaseScreen.java`:

```java
protected TextButton.TextButtonStyle customButtonStyle;
protected TextButton.TextButtonStyle squareButtonStyle;
protected TextButton.TextButtonStyle accentButtonStyle;
protected UiTheme uiTheme;
...
private void setupButtonStyle() {
    uiTheme = new UiTheme();
    if (VisUI.isLoaded()) {
        BitmapFont font = VisUI.getSkin().getFont("default-font");
        customButtonStyle = uiTheme.standardButtonStyle(font);
        squareButtonStyle = uiTheme.iconButtonStyle(font);
        accentButtonStyle = uiTheme.accentButtonStyle(font);
    }
}
```

Then fix `ConnectionScreen`'s Retry button to reference the field instead of calling `uiTheme.accentButtonStyle(...)` inline:

```java
retryButton = new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_RETRY"))
    .withStyle(accentButtonStyle)
    .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
    .withAction(this::handleRetryButtonClick)
    .build();
```

This one-line change removes the only place the leak pattern currently exists in shipped code, and every future screen (`MainMenuScreen` here, and whatever else wants a primary-CTA button later) gets it right by construction rather than needing to remember this each time.

## 2. Localize the title

New migration, `server/src/main/resources/db/migration/V21__Add_Main_Menu_Title_Localization.sql`, following the exact `localization_keys`/`localization_values` insert shape V2/V20 already use:

```sql
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('UI_TITLE_MAIN_MENU', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_TITLE_MAIN_MENU'), 'tr_TR', 'Ana Menü'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_TITLE_MAIN_MENU'), 'en_US', 'Main Menu');
```

Update `MainMenuScreen.setupUI()`:

```java
VisLabel titleLabel = new VisLabel(game.getLanguageManager().get("UI_TITLE_MAIN_MENU"));
titleLabel.setFontScale(2f);
titleLabel.setColor(UiPalette.ACCENT_CRIMSON);
mainPanel.add(titleLabel).padBottom(20).row();
```

(Import `io.github.ydhekim.crimson_sky.ui.UiPalette`, already established in prompt 26.)

## 3. Give the buttons hierarchy

Only the Characters button changes style — Achievements/Settings/Exit keep `customButtonStyle` exactly as they are today:

```java
new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_CHARACTERS"))
    .withStyle(accentButtonStyle)
    .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
    .withAction(this::navigateToCharacters)
    .buildAndAddTo(buttonTable, 15);
buttonTable.row();

new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_ACHIEVEMENTS"))
    .withStyle(customButtonStyle)   // unchanged
    ...
```

Don't touch the H1 test in `TestDatabase`/any server test fixtures for this — `UI_TITLE_MAIN_MENU` is a pure content-seed migration like V20's, no schema change, no new DAO method, nothing for `MessageCodeLocalizationCoverageTest` to pick up (that test only diffs `MessageCode.values()` against seeded keys — this is a plain UI string key, not a `MessageCode`, so it's outside that test's scope; don't try to make it pass through that mechanism).

## 4. Testing / Definition of Done

No new JUnit coverage needed beyond what already exists — this is layout/content work, same as prompts 24–27.

1. `gradlew.bat lwjgl3:run`, reach Main Menu — confirm the title reads "Ana Menü" (or "Main Menu" in English) in the crimson accent color, and Characters is visibly styled differently (crimson) from Achievements/Settings/Exit (neutral gray).
2. Switch language in Settings and return to Main Menu (or trigger whatever path causes `refreshUI()`) — confirm the title updates correctly and no texture-leak warnings appear in the LibGDX log after a few refreshes. This is the actual regression test for the `accentButtonStyle` fix in §1 — before that fix, this exact action on `ConnectionScreen` would have been silently accumulating textures every time.
3. Confirm Achievements/Settings/Exit still navigate/behave exactly as before — no visual or behavioral change to those three.
4. Confirm `ConnectionScreen`'s Retry button still renders and behaves identically after switching it to the shared `accentButtonStyle` field.

Definition of done: `accentButtonStyle` lives on `BaseScreen`, built once, no per-refresh texture accumulation anywhere it's used; `ConnectionScreen` and `MainMenuScreen` both reference the shared field rather than calling `uiTheme.accentButtonStyle(...)` directly; Main Menu's title is localized and crimson-accented; Characters reads as the primary action, Achievements/Settings/Exit stay neutral and unchanged.
