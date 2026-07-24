-- Seeds eight UI string keys the client already reads but nothing ever seeded into
-- localization_keys/localization_values, so LanguageManager.get(...) was returning the
-- "!KEY!" fallback on ConnectionScreen (UI_BTN_RETRY), SettingsScreen, and AchievementsScreen.
-- Predates the M4 screen-by-screen design pass; surfaced only when the real rendered Settings
-- screen was inspected (a mockup shows intended text, not the resolved string). Pure content seed,
-- same shape as V2/V20/V21/V22 — no schema change, and these are plain UI string keys (not
-- MessageCode values), so they're outside MessageCodeLocalizationCoverageTest.
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
