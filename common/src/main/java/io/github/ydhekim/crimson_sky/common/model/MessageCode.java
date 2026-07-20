package io.github.ydhekim.crimson_sky.common.model;

public enum MessageCode {
    SUCCESS,
    ERROR_UNKNOWN,
    CHAR_CREATE_SUCCESS,
    CHAR_NAME_TAKEN,
    CHAR_MAX_SLOTS_REACHED,
    LOGIN_FAILED_INVALID_TOKEN,
    STAT_POINTS_INSUFFICIENT,
    STAT_CAP_EXCEEDED,

    // Skill tree — learn/upgrade (system design §16)
    SKILL_NODE_NOT_FOUND,
    SKILL_LEVEL_GATE_NOT_MET,
    SKILL_FACTION_MISMATCH,
    SKILL_RANK_MAXED,
    SKILL_POINTS_INSUFFICIENT,
    SKILL_GOLD_INSUFFICIENT,

    // Loadout save (system design §4.4/§16/§17)
    LOADOUT_ITEM_NOT_OWNED,
    LOADOUT_SKILL_SLOTS_EXCEEDED,
    LOADOUT_WEIGHT_EXCEEDED,

    // Shop (system design §18)
    SHOP_ITEM_NOT_FOUND,
    SHOP_NOTHING_TO_REPAIR,
    SHOP_GOLD_INSUFFICIENT,
    SHOP_TOKEN_INSUFFICIENT,

    // Quests (system design §19)
    QUEST_NOT_FOUND,
    QUEST_NOT_COMPLETE,
    QUEST_ALREADY_CLAIMED,
    QUEST_DAILY_CLAIM_CAP_REACHED,
    QUEST_INVALID_REWARD_CHOICE,

    // Account levers (system design §20)
    DAILY_BATTLE_CAP_REACHED,

    // Ranked ladder (system design §21)
    RANKED_LEVEL_GATE_NOT_MET,

    // Ranked ladder claim (system design §21)
    LADDER_NOT_RANKED_ELIGIBLE,
    LADDER_NO_REWARD_THIS_RANK,
    LADDER_ALREADY_CLAIMED,

    // Character page / title equip (system design §22)
    TITLE_NOT_UNLOCKED,

    // Character customization (system design §23)
    CHAR_INVALID_APPEARANCE
}
