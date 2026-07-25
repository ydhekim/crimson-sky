-- Two UI string keys the AchievementsScreen redesign (M4 screen pass, prompt 33) needs: the
-- centered "X / Y unlocked" subtitle (a parameterized template, %d/%d filled via String.format at
-- the call site — nothing in this codebase does parameterized localization otherwise) and the
-- load-error message, which was a hardcoded English literal ("Error loading achievements.") before
-- this pass. Same content-seed shape as V23 — no schema change, plain UI keys outside
-- MessageCodeLocalizationCoverageTest, so they must land in the same change as their get(...) call sites.
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('UI_LBL_ACHIEVEMENTS_UNLOCKED_COUNT', 'UI'),
    ('UI_MSG_ACHIEVEMENTS_LOAD_ERROR', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_ACHIEVEMENTS_UNLOCKED_COUNT'), 'tr_TR', '%d / %d açıldı'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_ACHIEVEMENTS_UNLOCKED_COUNT'), 'en_US', '%d / %d unlocked'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_ACHIEVEMENTS_LOAD_ERROR'), 'tr_TR', 'Başarımlar yüklenemedi.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_ACHIEVEMENTS_LOAD_ERROR'), 'en_US', 'Couldn''t load achievements.');
