-- V4 seeded the identical English string under BOTH lang_code = 'tr_TR' and 'en_US' for all ten
-- ACH_*_TITLE keys (the descriptions were genuinely translated; only the titles were left English-in-
-- both-columns). So switching to Turkish correctly pulled the tr_TR row — which happened to hold English.
-- This replaces those ten tr_TR title rows with real Turkish translations, matching the descriptions'
-- already-established evocative tone. Data fix only (K6); no client change — the call sites already read
-- the right key, they just resolved to the wrong text.
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
