-- Account-wide achievements caption (prompt 47 / K14). Now that AchievementsScreen's query is a genuine
-- "any character on this account" aggregate rather than an accidentally-narrower ACCOUNT-scope-only view,
-- the screen says so out loud — otherwise the same achievement reading unlocked here and locked on a
-- specific character's page (a legitimate difference between the two views) still looks like a bug.
-- Same content-seed shape as V25, which seeded the rest of this screen's strings: no schema change, a plain
-- UI key outside MessageCodeLocalizationCoverageTest's reach, landing with its own get(...) call site.
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('UI_LBL_ACHIEVEMENTS_ACCOUNT_SCOPE', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_ACHIEVEMENTS_ACCOUNT_SCOPE'), 'tr_TR', 'Hesabındaki herhangi bir karakterin kazandığı başarımlar'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_ACHIEVEMENTS_ACCOUNT_SCOPE'), 'en_US', 'Earned by any character on your account');
