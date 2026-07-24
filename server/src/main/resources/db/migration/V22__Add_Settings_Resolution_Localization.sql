-- Localized label for the Settings screen's Resolution row (M4 screen-by-screen design pass).
-- Closes the gap the old inline comment flagged. Pure content seed, same shape as V2/V20/V21 —
-- no schema change, and UI_LBL_RESOLUTION is a plain UI string key (not a MessageCode), so it's
-- outside MessageCodeLocalizationCoverageTest.
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('UI_LBL_RESOLUTION', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_RESOLUTION'), 'tr_TR', 'Çözünürlük'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_RESOLUTION'), 'en_US', 'Resolution');
