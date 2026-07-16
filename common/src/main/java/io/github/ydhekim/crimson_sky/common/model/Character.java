package io.github.ydhekim.crimson_sky.common.model;

import java.util.Map;

public record Character(
    long id,
    long accountId,
    String name,
    Faction faction,
    int level,
    long experience,
    int maxHp,
    int maxMp,
    int maxStamina,
    int baseDef,
    int baseAtk,
    Stats stats,
    Inventory inventory,
    Loadout loadout,
    /**
     * Learned skill-tree nodes, as a {@code nodeId → current rank} map (system design §16). A node
     * absent from the map is un-learned (rank 0 implied); ranks run 1..3. Same JSONB-blob persistence
     * pattern as {@code stats}/{@code inventory}/{@code loadout}. Never {@code null} — an empty map for
     * a character that has learned nothing.
     */
    Map<String, Integer> skillTree
) {
}
