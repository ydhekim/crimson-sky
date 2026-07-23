-- Localized title for the Main Menu screen's crimson-accented header (M4 screen-by-screen design
-- pass). Pure content seed, same shape as V2/V20 — no schema change, and UI_TITLE_MAIN_MENU is a
-- plain UI string key (not a MessageCode), so it's outside MessageCodeLocalizationCoverageTest.
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('UI_TITLE_MAIN_MENU', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_TITLE_MAIN_MENU'), 'tr_TR', 'Ana Menü'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_TITLE_MAIN_MENU'), 'en_US', 'Main Menu');
