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

    // Loadout save (system design §4.4/§16)
    LOADOUT_ITEM_NOT_OWNED,
    LOADOUT_SKILL_SLOTS_EXCEEDED
}
