# Implementation prompt — SettingsScreen redesign

Third screen in the M4 screen-by-screen pass (after Connection, prompt 26/27; Main Menu, prompt 28). Same boxed-panel treatment as Main Menu — Settings is a utility screen, not an entry moment, so no full-bleed change — with the same two-part update: localized, crimson-accented title, and visual hierarchy on the footer buttons (Save as the accent primary action, since it's the one that actually commits a change; Back stays neutral).

Grounded in the current `SettingsScreen.java` before writing this: the title (`"UI_LBL_SETTINGS"`) is already localized, just needs the crimson color. The `"Resolution:"` label is not — it's a hardcoded English string with a comment literally noting the gap (`// İleride yerelleştirme anahtarı bağlanabilir` — "a localization key can be attached later") that was never closed. There are also several Turkish-only comments and two Turkish `Gdx.app.log`/`Gdx.app.error` calls around the resolution-change listener, inconsistent with the English-log convention the UI foundation refactor (prompt 25) already established for `CharactersScreen`/`AchievementsScreen`. Both are cheap to close out while this screen is already open for its title/button changes.

## 1. Localize the Resolution label

New migration, `server/src/main/resources/db/migration/V22__Add_Settings_Resolution_Localization.sql`:

```sql
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('UI_LBL_RESOLUTION', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_RESOLUTION'), 'tr_TR', 'Çözünürlük'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_RESOLUTION'), 'en_US', 'Resolution');
```

`SettingsScreen.setupUI()`:

```java
VisLabel resolutionLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_RESOLUTION"));
```

(Drop the old inline comment entirely — the gap it flagged is closed, not just re-flagged.)

## 2. Crimson title, accented Save

```java
VisLabel titleLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_SETTINGS"));
titleLabel.setFontScale(2f);
titleLabel.setColor(UiPalette.ACCENT_CRIMSON);
mainPanel.add(titleLabel).padBottom(20).colspan(2).row();
```

```java
new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_BACK"))
    .withStyle(customButtonStyle)   // unchanged
    .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
    .withAction(this::navigateBack)
    .buildAndAddTo(footerTable);

footerTable.add().expandX();

new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_SAVE"))
    .withStyle(accentButtonStyle)   // was customButtonStyle
    .withSize(UiMetrics.NAV_BUTTON_WIDTH, UiMetrics.NAV_BUTTON_HEIGHT)
    .withAction(this::saveSettings)
    .buildAndAddTo(footerTable);
```

(Import `io.github.ydhekim.crimson_sky.ui.UiPalette` — `accentButtonStyle` is already inherited from `BaseScreen`, per prompt 28, no new import needed for it.)

## 3. Clean up the Turkish comments/logs around resolution handling

Same convention prompt 25 already applied to `CharactersScreen`/`AchievementsScreen` — these are developer-facing comments/logs, not player-facing text, so they should be in English like every other screen's:

```java
// Applies the selected resolution immediately when it changes.
resolutionSelectBox.addListener(new ChangeListener() {
    @Override
    public void changed(ChangeEvent event, Actor actor) {
        applyResolution(resolutionSelectBox.getSelected(), fullscreenCheckBox.isChecked());
    }
});
```

```java
private void applyResolution(String resolutionStr, boolean isFullscreen) {
    if (resolutionStr == null || !resolutionStr.contains("x")) return;

    try {
        String[] parts = resolutionStr.split("x");
        int width = Integer.parseInt(parts[0]);
        int height = Integer.parseInt(parts[1]);

        if (isFullscreen) {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        } else {
            Gdx.graphics.setWindowedMode(width, height);
        }
        Gdx.app.log("SettingsScreen", "Window mode updated: " + resolutionStr + " (fullscreen: " + isFullscreen + ")");
    } catch (Exception e) {
        Gdx.app.error("SettingsScreen", "Failed to apply resolution", e);
    }
}
```

Also drop the two now-stale comments above the resolution select box's construction (`// Buraya ağ paketinden...` and `// Örnek varsayılan kilit:`) — the behavior they were describing is already just... the code below them; they don't add information beyond what's already cleaned up.

## 4. Testing / Definition of Done

No new JUnit coverage needed (layout/content/logging cleanup, same shape as prompts 24–28).

1. `gradlew.bat lwjgl3:run`, reach Settings — confirm the title is crimson, Save is visually the accent color and Back is neutral, and the Resolution label reads "Çözünürlük"/"Resolution" depending on language instead of always English.
2. Change language, save, confirm the screen refreshes correctly (existing `refreshUI()`/`onSaveAccountSettingsResponse` flow, unchanged) and the Resolution label updates to match the new language too.
3. Change resolution/fullscreen, confirm the window actually resizes as before — no behavior change, only the log text changed.
4. Confirm Back still returns to Main Menu unchanged.

Definition of done: Resolution label is localized (English/Turkish both correct); title and Save button match the established crimson-accent pattern from Connection/Main Menu; all Turkish-only developer comments/logs in this file are now in English, consistent with every other screen.
