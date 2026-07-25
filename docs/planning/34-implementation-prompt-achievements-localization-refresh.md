# Implementation prompt — Achievements: language switch doesn't re-render, and title translations were never real (K6)

Two distinct, confirmed bugs found testing the merged AchievementsScreen redesign (prompt 33) after switching the app language: the screen's title/count/back button/rows stay in the old language, and even where content *does* update, most achievement titles are identical in both languages.

## 1. `AchievementsScreen` never re-renders on language change

`AchievementsScreen` doesn't override `refreshUI()` (`BaseScreen`'s default is a no-op). Every piece of UI — the header title, the "X / Y unlocked" count, the Back button, and every achievement row — is built exactly once, in the constructor, from whatever language was active at that moment. `BaseScreen.onLocalizationResponse()` calls `refreshUI()` on every successful `LocalizationResponse`, same as every other screen, but here that hook does nothing.

Fix, mirroring the `ConnectionScreen`/`initializeConnectionVisuals()` precedent: split the current `setupUIShell()` into a one-time texture-construction step and a rebuildable structure-construction step, since blindly re-running the current `setupUIShell()` from `refreshUI()` would recreate `rowBgUnlockedTexture`/`rowBgLockedTexture`/`xpBadgeUnlockedTexture`/`xpBadgeLockedTexture`/`dividerTexture`/`placeholderIconTexture` every time without disposing the old ones — the exact texture-leak class already fixed once for `ConnectionScreen`'s Retry button (prompt 28).

```java
public AchievementsScreen(CrimsonSky game) {
    super(game);
    this.disposables = new ArrayList<>();
    game.getNetworkClient().setListener(this);
    initializeTextures();   // one-time only — never called again
    setupUIShell();         // safe to re-run — reads the drawables built above, builds no textures itself
    fetchAchievements();
}

@Override
public void refreshUI() {
    setupUIShell();
    fetchAchievements();
}
```

Move the six `TextureFactory.createSolidTexture(...)`/`disposables.add(...)`/`...Drawable = ...` blocks currently inside `setupUIShell()` (rowBg, xpBadge, divider, placeholder icon) into a new private `initializeTextures()`, called once from the constructor before `setupUIShell()`. `setupUIShell()` keeps building the header/scrollpane/footer structure exactly as it does now, just without touching any `Texture`/`TextureRegionDrawable` fields — those are already populated by the time it runs.

Re-fetching (`fetchAchievements()`) on every `refreshUI()` is deliberate, not just re-localizing static labels: `populateAchievements()` resolves each row's title/description via `game.getLanguageManager().get(ach.titleLocKey())` at render time, so a fresh render pass with the now-current `translations` map is what actually re-localizes the rows — no need for the server round trip to return different data, just for the client to re-render with the updated map.

## 2. Achievement titles were never actually translated — V4 seeded the English string under `tr_TR` too

Root cause, found reading `V4__Base_Achievements_And_Localizations.sql`: all ten `ACH_*_TITLE` keys have the *identical* English text inserted for both `lang_code = 'tr_TR'` and `lang_code = 'en_US'` (e.g. `('ACH_PIONEER_TITLE', 'tr_TR', 'Pioneer of the Crimson Sky')` right next to `('ACH_PIONEER_TITLE', 'en_US', 'Pioneer of the Crimson Sky')`). The *descriptions* were genuinely translated — real Turkish prose exists for every `ACH_*_DESC` key — only the titles were left as English-in-both-columns. This predates the M4 pass entirely (it's been this way since V4) and isn't a rendering bug: switching language correctly pulls whatever's in the `tr_TR` row, and that row has always held English text.

New migration, `server/src/main/resources/db/migration/V26__Fix_Achievement_Title_Turkish_Translations.sql`, replacing the ten `tr_TR` title rows with real translations (matching the descriptions' already-established evocative tone rather than flat literal ones):

```sql
UPDATE localization_values SET text_value = 'Kızıl Gökyüzü''nün Öncüsü'
    WHERE key_id = (SELECT id FROM localization_keys WHERE key_name = 'ACH_PIONEER_TITLE') AND lang_code = 'tr_TR';
UPDATE localization_values SET text_value = 'İlk Gün Hayatta Kalanı'
    WHERE key_id = (SELECT id FROM localization_keys WHERE key_name = 'ACH_DAY_ONE_TITLE') AND lang_code = 'tr_TR';
UPDATE localization_values SET text_value = 'Yeni Bir Efsane Doğuyor'
    WHERE key_id = (SELECT id FROM localization_keys WHERE key_name = 'ACH_NEW_LEGEND_TITLE') AND lang_code = 'tr_TR';
UPDATE localization_values SET text_value = 'İlk Kan'
    WHERE key_id = (SELECT id FROM localization_keys WHERE key_name = 'ACH_FIRST_BLOOD_TITLE') AND lang_code = 'tr_TR';
UPDATE localization_values SET text_value = 'Kırılmaz'
    WHERE key_id = (SELECT id FROM localization_keys WHERE key_name = 'ACH_UNBROKEN_TITLE') AND lang_code = 'tr_TR';
UPDATE localization_values SET text_value = 'Çeliğin İlk Çığlığı'
    WHERE key_id = (SELECT id FROM localization_keys WHERE key_name = 'ACH_FIRST_CRY_STEEL_TITLE') AND lang_code = 'tr_TR';
UPDATE localization_values SET text_value = 'Göklerin İlk Fısıltısı'
    WHERE key_id = (SELECT id FROM localization_keys WHERE key_name = 'ACH_FIRST_WHISPER_TITLE') AND lang_code = 'tr_TR';
UPDATE localization_values SET text_value = 'Boşluktaki İki Gölge'
    WHERE key_id = (SELECT id FROM localization_keys WHERE key_name = 'ACH_TWO_SHADOWS_TITLE') AND lang_code = 'tr_TR';
UPDATE localization_values SET text_value = 'Mükemmel Fırtına'
    WHERE key_id = (SELECT id FROM localization_keys WHERE key_name = 'ACH_PERFECT_STORM_TITLE') AND lang_code = 'tr_TR';
UPDATE localization_values SET text_value = 'Göklerin Hayaleti'
    WHERE key_id = (SELECT id FROM localization_keys WHERE key_name = 'ACH_GHOST_SKIES_TITLE') AND lang_code = 'tr_TR';
```

No client code changes for this part — the call sites already read the right key, they just resolved to the wrong (English) text under `tr_TR`.

## 3. Testing / Definition of Done

1. `gradlew.bat lwjgl3:run` in Turkish, reach Achievements, note the title/count/back button/row text. Go to Settings, switch to English, Save, navigate back to Achievements (don't restart) — confirm the title now reads "Achievements", the count reads "N / M unlocked", Back reads "Back", and every row's title and description are in English.
2. Switch back to Turkish the same way — confirm everything reverts, including the ten achievement titles now showing real Turkish (not the English strings) alongside their already-correct Turkish descriptions.
3. Confirm no texture-leak regression: repeatedly toggle language back and forth (10+ times) and watch for growing memory/texture count — `initializeTextures()` must only ever run once, from the constructor.

Definition of done: every part of the Achievements screen — chrome and row content — reflects the active language immediately after a language change, without needing an app restart; all ten achievement titles have real, distinct Turkish translations.
