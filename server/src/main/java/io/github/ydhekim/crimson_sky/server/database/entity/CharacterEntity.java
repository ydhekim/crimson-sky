package io.github.ydhekim.crimson_sky.server.database.entity;

import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import org.jdbi.v3.json.Json;

public record CharacterEntity(
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
    @Json Stats stats,
    @Json Inventory inventory,
    @Json Loadout loadout) {

    public Character toCommonModel() {
        return new Character(id, accountId, name, faction, level, experience, maxHp, maxMp, baseDef, baseAtk, stats, inventory, loadout);
    }

    public static CharacterEntity fromCommonModel(long accountId, Character c) {
        return new CharacterEntity(
            c.id(), accountId, c.name(), c.faction(), c.level(), c.experience(),
            c.maxHp(), c.maxMp(), c.baseDef(), c.baseAtk(),
            c.stats(), c.inventory(), c.loadout()
        );
    }
}
