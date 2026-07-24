# Implementation prompt — seed the missing UI localization keys

Found while reviewing the Settings redesign (prompt 29) in a screenshot: most of the screen showed raw `!KEY!` fallback text instead of translated labels. Traced to the source — `LanguageManager.get(String key)` returns `translations.getOrDefault(key, "!" + key + "!")`, and a repo-wide check (every `getLanguageManager().get("...")` call site in `core`, diffed against every key ever seeded across `server/src/main/resources/db/migration/*.sql`) shows these eight keys are used by the client but have never been seeded into `localization_keys`/`localization_values` at all:

`UI_BTN_RETRY`, `UI_BTN_SAVE`, `UI_LBL_ACHIEVEMENTS`, `UI_LBL_FULLSCREEN`, `UI_LBL_LANGUAGE`, `UI_LBL_SETTINGS`, `UI_LBL_VOLUME`, `UI_MSG_NO_ACHIEVEMENTS`.

This predates the M4 screen-by-screen design pass entirely — `UI_BTN_RETRY` has been broken on `ConnectionScreen` since it was first written, same for the others on `SettingsScreen`/`AchievementsScreen`. It just wasn't visually obvious until someone looked closely at the actual rendered screen (a mockup shows intended text, not the real localized string, so this class of gap doesn't surface until a real build is run) — the same kind of silent content gap the hardening pass (V20) found and fixed for `MessageCode` values, just for plain UI string keys instead.

## 1. Seed the eight missing keys

New migration, `server/src/main/resources/db/migration/V23__Add_Missing_UI_Localization_Keys.sql`, same shape as V2/V20/V21/V22:

```sql
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('UI_BTN_RETRY', 'UI'),
    ('UI_BTN_SAVE', 'UI'),
    ('UI_LBL_ACHIEVEMENTS', 'UI'),
    ('UI_LBL_FULLSCREEN', 'UI'),
    ('UI_LBL_LANGUAGE', 'UI'),
    ('UI_LBL_SETTINGS', 'UI'),
    ('UI_LBL_VOLUME', 'UI'),
    ('UI_MSG_NO_ACHIEVEMENTS', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_RETRY'), 'tr_TR', 'Tekrar Dene'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_RETRY'), 'en_US', 'Retry'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_SAVE'), 'tr_TR', 'Kaydet'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_SAVE'), 'en_US', 'Save'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_ACHIEVEMENTS'), 'tr_TR', 'Başarımlar'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_ACHIEVEMENTS'), 'en_US', 'Achievements'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_FULLSCREEN'), 'tr_TR', 'Tam Ekran'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_FULLSCREEN'), 'en_US', 'Fullscreen'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_LANGUAGE'), 'tr_TR', 'Dil'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_LANGUAGE'), 'en_US', 'Language'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_SETTINGS'), 'tr_TR', 'Ayarlar'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_SETTINGS'), 'en_US', 'Settings'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_VOLUME'), 'tr_TR', 'Ses'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_VOLUME'), 'en_US', 'Volume'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_NO_ACHIEVEMENTS'), 'tr_TR', 'Başarım bulunamadı.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_NO_ACHIEVEMENTS'), 'en_US', 'No achievements found.');
```

No client code changes — every call site already reads the correct key name, they just had nothing to resolve to.

## 2. Worth deciding: how do we stop this recurring?

Not fixing this now, just flagging the choice. `MessageCodeLocalizationCoverageTest` (from the hardening pass) already prevents this exact class of gap for `MessageCode`-derived keys — it reads migration SQL off the classpath and diffs against `MessageCode.values()`, so a new unseeded `MessageCode` fails the build. Plain `UI_*` string-literal keys have no equivalent enum to diff against, so replicating that test would mean scanning `core`'s Java source for `getLanguageManager().get("...")` call sites — a regex over `.java` files rather than an enum, and a test that would need to read across module boundaries (`core`'s source from a test that also needs `server`'s migration SQL), which is a real cross-module dependency this project doesn't currently have anywhere. Options, roughly cheapest to most thorough: (a) do nothing new, just be more careful to seed a key in the same prompt that introduces its usage — what should have happened for `UI_BTN_SAVE` etc. the first time; (b) a lightweight standalone script (not a JUnit test) that greps both trees and reports drift, run manually/occasionally rather than gating the build; (c) a real cross-module coverage test matching `MessageCodeLocalizationCoverageTest`'s spirit. I'd lean toward (a) for now given how rarely new UI keys get added, but it's worth your call rather than me just picking.

## 3. Testing / Definition of Done

1. `gradlew.bat lwjgl3:run`, reach Settings — confirm every label now shows real text (Turkish by default, matching current config) instead of `!KEY!` fallbacks.
2. Trigger the Retry button's visible state on ConnectionScreen (stop the server before connecting) — confirm it now reads "Tekrar Dene"/"Retry" instead of `!UI_BTN_RETRY!`.
3. Reach Achievements with zero unlocked achievements (or check the title) — confirm both the title and the empty-state message are now translated.
4. Switch language in Settings and confirm all eight now flip correctly between Turkish and English.

Definition of done: all eight keys resolve to real text in both languages; no client code changes needed since every call site was already correct.
