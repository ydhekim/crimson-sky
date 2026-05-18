INSERT INTO localization_keys (key_name, group_type)
VALUES ('UI_BTN_CHARACTERS', 'UI'),
       ('UI_BTN_ACHIEVEMENTS', 'UI'),
       ('UI_BTN_SETTINGS', 'UI'),
       ('UI_BTN_EXIT', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value)
VALUES ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_CHARACTERS'), 'tr_TR',
        'Karakterler'),
       ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_CHARACTERS'), 'en_US',
        'Characters');

INSERT INTO localization_values (key_id, lang_code, text_value)
VALUES ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_ACHIEVEMENTS'), 'tr_TR',
        'Başarımlar'),
       ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_ACHIEVEMENTS'), 'en_US',
        'Achievements');

INSERT INTO localization_values (key_id, lang_code, text_value)
VALUES ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_SETTINGS'), 'tr_TR',
        'Ayarlar'),
       ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_SETTINGS'), 'en_US',
        'Settings');

INSERT INTO localization_values (key_id, lang_code, text_value)
VALUES ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_EXIT'), 'tr_TR',
        'Çıkış'),
       ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_EXIT'), 'en_US',
        'Exit');
