-- CharactersScreen redesign (M4 screen pass, prompt 35). Almost the entire screen was hardcoded
-- English literals before this change — only Back (UI_BTN_BACK) was localized. These seed the title,
-- the centered slot-count subtitle, the per-row level/XP captions, the Play/New Character buttons,
-- the empty-state message, and the whole delete-confirmation dialog. Same content-seed shape as
-- V23/V25 — no schema change, plain UI keys outside MessageCodeLocalizationCoverageTest, so they must
-- land in the same change as their get(...) call sites. %d/%s templates fill via String.format at the
-- call site, matching the convention established in prompts 32/33.
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('UI_LBL_CHARACTER_SELECTION', 'UI'),
    ('UI_LBL_CHARACTER_SLOTS_COUNT', 'UI'),
    ('UI_LBL_LEVEL_SHORT', 'UI'),
    ('UI_LBL_XP_PROGRESS', 'UI'),
    ('UI_LBL_MAX_LEVEL', 'UI'),
    ('UI_BTN_PLAY', 'UI'),
    ('UI_BTN_NEW_CHARACTER', 'UI'),
    ('UI_LBL_SLOTS_FULL', 'UI'),
    ('UI_MSG_NO_CHARACTERS', 'UI'),
    ('UI_LBL_DELETE_CHARACTER_TITLE', 'UI'),
    ('UI_MSG_DELETE_CHARACTER_CONFIRM', 'UI'),
    ('UI_BTN_YES', 'UI'),
    ('UI_BTN_NO', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_CHARACTER_SELECTION'), 'tr_TR', 'Karakter Seçimi'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_CHARACTER_SELECTION'), 'en_US', 'Character selection'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_CHARACTER_SLOTS_COUNT'), 'tr_TR', '%d / %d slot'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_CHARACTER_SLOTS_COUNT'), 'en_US', '%d / %d slots'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_LEVEL_SHORT'), 'tr_TR', 'Sv. %d'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_LEVEL_SHORT'), 'en_US', 'Lv %d'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_XP_PROGRESS'), 'tr_TR', '%d / %d XP'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_XP_PROGRESS'), 'en_US', '%d / %d XP'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_MAX_LEVEL'), 'tr_TR', 'Maks. seviye'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_MAX_LEVEL'), 'en_US', 'Max level'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_PLAY'), 'tr_TR', 'Oyna'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_PLAY'), 'en_US', 'Play'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_NEW_CHARACTER'), 'tr_TR', 'Yeni karakter'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_NEW_CHARACTER'), 'en_US', 'New character'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_SLOTS_FULL'), 'tr_TR', 'Slotlar dolu'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_SLOTS_FULL'), 'en_US', 'Slots full'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_NO_CHARACTERS'), 'tr_TR', 'Karakter bulunamadı. Maceraya başlamak için bir tane oluştur.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_NO_CHARACTERS'), 'en_US', 'No characters found. Create one to begin your journey.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_DELETE_CHARACTER_TITLE'), 'tr_TR', 'Karakteri sil'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_DELETE_CHARACTER_TITLE'), 'en_US', 'Delete character'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_DELETE_CHARACTER_CONFIRM'), 'tr_TR', '%s karakterini kalıcı olarak silmek istediğine emin misin?'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_DELETE_CHARACTER_CONFIRM'), 'en_US', 'Are you sure you want to permanently delete %s?'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_YES'), 'tr_TR', 'Evet'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_YES'), 'en_US', 'Yes'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_NO'), 'tr_TR', 'Hayır'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_NO'), 'en_US', 'No');
