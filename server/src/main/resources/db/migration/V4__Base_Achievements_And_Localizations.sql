INSERT INTO localization_keys (key_name, group_type)
VALUES ('ACH_PIONEER_TITLE', 'ACHIEVEMENT'),
       ('ACH_PIONEER_DESC', 'ACHIEVEMENT'),
       ('ACH_DAY_ONE_TITLE', 'ACHIEVEMENT'),
       ('ACH_DAY_ONE_DESC', 'ACHIEVEMENT'),
       ('ACH_NEW_LEGEND_TITLE', 'ACHIEVEMENT'),
       ('ACH_NEW_LEGEND_DESC', 'ACHIEVEMENT'),
       ('ACH_FIRST_BLOOD_TITLE', 'ACHIEVEMENT'),
       ('ACH_FIRST_BLOOD_DESC', 'ACHIEVEMENT'),
       ('ACH_UNBROKEN_TITLE', 'ACHIEVEMENT'),
       ('ACH_UNBROKEN_DESC', 'ACHIEVEMENT'),
       ('ACH_FIRST_CRY_STEEL_TITLE', 'ACHIEVEMENT'),
       ('ACH_FIRST_CRY_STEEL_DESC', 'ACHIEVEMENT'),
       ('ACH_FIRST_WHISPER_TITLE', 'ACHIEVEMENT'),
       ('ACH_FIRST_WHISPER_DESC', 'ACHIEVEMENT'),
       ('ACH_TWO_SHADOWS_TITLE', 'ACHIEVEMENT'),
       ('ACH_TWO_SHADOWS_DESC', 'ACHIEVEMENT'),
       ('ACH_PERFECT_STORM_TITLE', 'ACHIEVEMENT'),
       ('ACH_PERFECT_STORM_DESC', 'ACHIEVEMENT'),
       ('ACH_GHOST_SKIES_TITLE', 'ACHIEVEMENT'),
       ('ACH_GHOST_SKIES_DESC', 'ACHIEVEMENT') ON CONFLICT (key_name) DO NOTHING;

INSERT INTO localization_values (key_id, lang_code, text_value)
SELECT k.id, src.lang, src.val
FROM (VALUES ('ACH_PIONEER_TITLE', 'tr_TR', 'Pioneer of the Crimson Sky'),
             ('ACH_PIONEER_DESC', 'tr_TR', 'Gökyüzünün kızıllığına ilk şahit olanlardan biri oldun.'),
             ('ACH_DAY_ONE_TITLE', 'tr_TR', 'Day One Survivor'),
             ('ACH_DAY_ONE_DESC', 'tr_TR', 'Büyük göçün başladığı o meşum günde buradaydın.'),
             ('ACH_NEW_LEGEND_TITLE', 'tr_TR', 'A New Legend Rises'),
             ('ACH_NEW_LEGEND_DESC', 'tr_TR', 'Gökyüzü yeni bir hükümdar kazandı.'),
             ('ACH_FIRST_BLOOD_TITLE', 'tr_TR', 'First Blood'),
             ('ACH_FIRST_BLOOD_DESC', 'tr_TR', 'Çeliğin soğuk yüzü düşmanla tanıştı.'),
             ('ACH_UNBROKEN_TITLE', 'tr_TR', 'Unbroken'),
             ('ACH_UNBROKEN_DESC', 'tr_TR', 'Ölümün kıyısından dönüp zaferle kucaklaştın.'),
             ('ACH_FIRST_CRY_STEEL_TITLE', 'tr_TR', 'The First Cry of Steel'),
             ('ACH_FIRST_CRY_STEEL_DESC', 'tr_TR',
              'Kızıl fırtınalara karşı yükselteceğin ilk ses, avucunun içindeki o soğuk metalin yankısı oldu.'),
             ('ACH_FIRST_WHISPER_TITLE', 'tr_TR', 'The First Whisper of the Skies'),
             ('ACH_FIRST_WHISPER_DESC', 'tr_TR',
              'Zihnini gökyüzünün yırtıcı enerjisine açtın ve unutulmuş çağların fısıltısını saf bir güce dönüştürdün.'),
             ('ACH_TWO_SHADOWS_TITLE', 'tr_TR', 'Two Shadows in the Void'),
             ('ACH_TWO_SHADOWS_DESC', 'tr_TR',
              'Bulutların arasındaki gölgen artık yalnız değil; acımasız dünyaya karşı edilen sessiz bir sadakat yemini bu.'),
             ('ACH_PERFECT_STORM_TITLE', 'tr_TR', 'The Perfect Storm'),
             ('ACH_PERFECT_STORM_DESC', 'tr_TR',
              'Üst üste beş kez zafer rüzgarı estirmek, gökyüzünün senin iraden önünde diz çöktüğünün kanıtıdır.'),
             ('ACH_GHOST_SKIES_TITLE', 'tr_TR', 'Ghost of the Skies'),
             ('ACH_GHOST_SKIES_DESC', 'tr_TR',
              'Kızıl semalarda yankılanan çelik seslerinin arasında bir hayalet gibi süzüldün; düşman vurduğunu sandı, ama sadece rüzgarı yakalayabildi.')) AS src(k_name, lang, val)
         JOIN localization_keys k ON k.key_name = src.k_name ON CONFLICT (key_id, lang_code) DO
UPDATE SET text_value = EXCLUDED.text_value;

INSERT INTO localization_values (key_id, lang_code, text_value)
SELECT k.id, src.lang, src.val
FROM (VALUES ('ACH_PIONEER_TITLE', 'en_US', 'Pioneer of the Crimson Sky'),
             ('ACH_PIONEER_DESC', 'en_US', 'You became one of the first to witness the redness of the sky.'),
             ('ACH_DAY_ONE_TITLE', 'en_US', 'Day One Survivor'),
             ('ACH_DAY_ONE_DESC', 'en_US', 'You were here on that ominous day when the grand migration began.'),
             ('ACH_NEW_LEGEND_TITLE', 'en_US', 'A New Legend Rises'),
             ('ACH_NEW_LEGEND_DESC', 'en_US', 'The sky has gained a new ruler.'),
             ('ACH_FIRST_BLOOD_TITLE', 'en_US', 'First Blood'),
             ('ACH_FIRST_BLOOD_DESC', 'en_US', 'The cold face of steel met the enemy.'),
             ('ACH_UNBROKEN_TITLE', 'en_US', 'Unbroken'),
             ('ACH_UNBROKEN_DESC', 'en_US', 'You returned from the brink of death and embraced victory.'),
             ('ACH_FIRST_CRY_STEEL_TITLE', 'en_US', 'The First Cry of Steel'),
             ('ACH_FIRST_CRY_STEEL_DESC', 'en_US',
              'The first voice you raise against the crimson storms was the echo of that cold metal in your palm.'),
             ('ACH_FIRST_WHISPER_TITLE', 'en_US', 'The First Whisper of the Skies'),
             ('ACH_FIRST_WHISPER_DESC', 'en_US',
              'You opened your mind to the predatory energy of the sky and turned the whisper of forgotten ages into pure power.'),
             ('ACH_TWO_SHADOWS_TITLE', 'en_US', 'Two Shadows in the Void'),
             ('ACH_TWO_SHADOWS_DESC', 'en_US',
              'Your shadow among the clouds is no longer alone; this is a silent vow of loyalty against a cruel world.'),
             ('ACH_PERFECT_STORM_TITLE', 'en_US', 'The Perfect Storm'),
             ('ACH_PERFECT_STORM_DESC', 'en_US',
              'To stir up the wind of victory five times in a row is proof that the sky kneels before your will.'),
             ('ACH_GHOST_SKIES_TITLE', 'en_US', 'Ghost of the Skies'),
             ('ACH_GHOST_SKIES_DESC', 'en_US',
              'You glided like a ghost amidst the clashing sounds of steel in the crimson skies; the enemy thought they hit you, but only caught the wind.')) AS src(k_name, lang, val)
         JOIN localization_keys k ON k.key_name = src.k_name ON CONFLICT (key_id, lang_code) DO
UPDATE SET text_value = EXCLUDED.text_value;

INSERT INTO achievement_definitions (key_name, title_loc_key, desc_loc_key, icon_id, xp_reward)
SELECT src.ach_key,
       k_title.id,
       k_desc.id,
       src.icon,
       src.xp
FROM (VALUES ('PIONEER_OF_CRIMSON_SKY', 'ACH_PIONEER_TITLE', 'ACH_PIONEER_DESC', 'key_monument_red_sun', 0),
             ('DAY_ONE_SURVIVOR', 'ACH_DAY_ONE_TITLE', 'ACH_DAY_ONE_DESC', 'crimson_banner_golden_hand', 0),
             ('A_NEW_LEGEND_RISES', 'ACH_NEW_LEGEND_TITLE', 'ACH_NEW_LEGEND_DESC', 'crown_iron_hilt', 0),
             ('FIRST_BLOOD', 'ACH_FIRST_BLOOD_TITLE', 'ACH_FIRST_BLOOD_DESC', 'bloody_dagger_skull', 100),
             ('UNBROKEN', 'ACH_UNBROKEN_TITLE', 'ACH_UNBROKEN_DESC', 'phoenix_shield', 300),
             ('THE_FIRST_CRY_OF_STEEL', 'ACH_FIRST_CRY_STEEL_TITLE', 'ACH_FIRST_CRY_STEEL_DESC',
              'gauntlet_sword_chainmail', 0),
             ('THE_FIRST_WHISPER_OF_SKIES', 'ACH_FIRST_WHISPER_TITLE', 'ACH_FIRST_WHISPER_DESC',
              'purple_grimoire_vortex', 0),
             ('TWO_SHADOWS_IN_THE_VOID', 'ACH_TWO_SHADOWS_TITLE', 'ACH_TWO_SHADOWS_DESC', 'wolf_pup_silhouettes', 0),
             ('THE_PERFECT_STORM', 'ACH_PERFECT_STORM_TITLE', 'ACH_PERFECT_STORM_DESC', 'crossed_swords_vortex', 750),
             ('GHOST_OF_THE_SKIES', 'ACH_GHOST_SKIES_TITLE', 'ACH_GHOST_SKIES_DESC', 'phantom_spikes',
              1000)) AS src(ach_key, t_key, d_key, icon, xp)
         JOIN localization_keys k_title ON k_title.key_name = src.t_key
         JOIN localization_keys k_desc ON k_desc.key_name = src.d_key ON CONFLICT (key_name) DO
UPDATE SET
    title_loc_key = EXCLUDED.title_loc_key,
    desc_loc_key = EXCLUDED.desc_loc_key,
    icon_id = EXCLUDED.icon_id,
    xp_reward = EXCLUDED.xp_reward;
