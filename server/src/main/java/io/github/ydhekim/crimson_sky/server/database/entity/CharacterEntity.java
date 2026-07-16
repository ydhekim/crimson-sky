package io.github.ydhekim.crimson_sky.server.database.entity;

import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import org.jdbi.v3.json.Json;

import java.util.HashMap;
import java.util.Map;

public record CharacterEntity(
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
    @Json Stats stats,
    @Json Inventory inventory,
    @Json Loadout loadout,
    @Json Map<String, Integer> skillTree) {

    public Character toCommonModel() {
        return new Character(id, accountId, name, faction, level, experience, maxHp, maxMp, maxStamina, baseDef, baseAtk,
            stats, inventory, loadout, skillTree != null ? skillTree : new HashMap<>());
    }

    public static CharacterEntity fromCommonModel(long accountId, Character c) {
        return new CharacterEntity(
            c.id(), accountId, c.name(), c.faction(), c.level(), c.experience(),
            c.maxHp(), c.maxMp(), c.maxStamina(), c.baseDef(), c.baseAtk(),
            c.stats(), c.inventory(), c.loadout(),
            c.skillTree() != null ? c.skillTree() : new HashMap<>()
        );
    }
}
