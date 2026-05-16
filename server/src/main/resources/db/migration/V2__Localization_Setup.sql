INSERT INTO localization_keys (key_name, group_type)
VALUES ('CHAR_NAME_TAKEN', 'ERROR'),
       ('UI_BTN_BACK', 'UI'),
       ('ACH_WIN_A_BATTLE_TITLE', 'ACHIEVEMENT'),
       ('ACH_WIN_A_BATTLE_DESC', 'ACHIEVEMENT');

INSERT INTO localization_values (key_id, lang_code, text_value)
VALUES ((SELECT id FROM localization_keys WHERE key_name = 'CHAR_NAME_TAKEN'), 'tr_TR',
        'Bu karakter ismi zaten alınmış.'),
       ((SELECT id FROM localization_keys WHERE key_name = 'CHAR_NAME_TAKEN'), 'en_US',
        'This character name is already taken.');

INSERT INTO localization_values (key_id, lang_code, text_value)
VALUES ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_BACK'), 'tr_TR', 'Geri'),
       ((SELECT id FROM localization_keys WHERE key_name = 'UI_BTN_BACK'), 'en_US', 'Back');

INSERT INTO localization_values (key_id, lang_code, text_value)
VALUES ((SELECT id FROM localization_keys WHERE key_name = 'ACH_WIN_A_BATTLE_TITLE'), 'tr_TR', 'Bir savaş kazan'),
       ((SELECT id FROM localization_keys WHERE key_name = 'ACH_WIN_A_BATTLE_TITLE'), 'en_US', 'Win a battle'),
       ((SELECT id FROM localization_keys WHERE key_name = 'ACH_WIN_A_BATTLE_DESC'), 'tr_TR',
        'İlk zaferini bir düşmanı alt ederek kazan.'),
       ((SELECT id FROM localization_keys WHERE key_name = 'ACH_WIN_A_BATTLE_DESC'), 'en_US',
        'Earn your first victory by defeating an enemy.');

INSERT INTO achievement_definitions (key_name, title_loc_key, desc_loc_key, xp_reward, icon_id)
VALUES ('FIRST_VICTORY',
        (SELECT id FROM localization_keys WHERE key_name = 'ACH_WIN_A_BATTLE_TITLE'),
        (SELECT id FROM localization_keys WHERE key_name = 'ACH_WIN_A_BATTLE_DESC'),
        100,
        'flag_normal');
