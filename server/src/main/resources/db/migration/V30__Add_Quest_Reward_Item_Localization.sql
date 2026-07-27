-- Quest reward exposure (prompt 46). QuestProgress now carries its rewardOptions over the wire, so the
-- three consumables a quest can pay need player-facing display names for the first time — no shop UI has
-- ever rendered them, so none of these keys exist yet. Same content-seed shape as V23/V25/V27/V29: no
-- schema change, plain UI keys outside MessageCodeLocalizationCoverageTest's reach.
--
-- The key names mirror ShopService's consumable string constants (skill_restoration_scroll, repair_token,
-- pet_care_kit) uppercased under a UI_ITEM_ prefix; the client maps QuestReward.itemKey onto them.
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('UI_ITEM_SKILL_RESTORATION_SCROLL', 'UI'),
    ('UI_ITEM_REPAIR_TOKEN', 'UI'),
    ('UI_ITEM_PET_CARE_KIT', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_ITEM_SKILL_RESTORATION_SCROLL'), 'tr_TR', 'Beceri Onarım Parşömeni'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_ITEM_SKILL_RESTORATION_SCROLL'), 'en_US', 'Skill Restoration Scroll'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_ITEM_REPAIR_TOKEN'), 'tr_TR', 'Tamir Jetonu'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_ITEM_REPAIR_TOKEN'), 'en_US', 'Repair Token'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_ITEM_PET_CARE_KIT'), 'tr_TR', 'Evcil Hayvan Bakım Kiti'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_ITEM_PET_CARE_KIT'), 'en_US', 'Pet Care Kit');
