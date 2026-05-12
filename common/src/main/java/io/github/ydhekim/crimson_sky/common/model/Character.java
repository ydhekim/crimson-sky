package io.github.ydhekim.crimson_sky.common.model;

public record Character(
    long id,
    long accountId,
    String name,
    Faction faction,
    int level,
    long experience,
    int maxHp,
    int maxMp,
    int baseDef,
    int baseAtk,
    Stats stats,
    Inventory inventory,
    Loadout loadout
) {
}
