package io.github.ydhekim.crimson_sky.ecs;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.ecs.component.BaseStatsComponent;
import io.github.ydhekim.crimson_sky.ecs.component.HealthComponent;
import io.github.ydhekim.crimson_sky.ecs.component.IdComponent;
import io.github.ydhekim.crimson_sky.ecs.component.InventoryComponent;
import io.github.ydhekim.crimson_sky.ecs.component.LevelComponent;
import io.github.ydhekim.crimson_sky.ecs.component.LoadoutComponent;
import io.github.ydhekim.crimson_sky.ecs.component.ManaComponent;
import io.github.ydhekim.crimson_sky.ecs.component.NameComponent;
import io.github.ydhekim.crimson_sky.ecs.component.StatsComponent;

/**
 * Utility to map common Character records to ECS Entities and vice-versa.
 */
public class CharacterMapper {

    /**
     * Creates an ECS Entity from a shared model Character.
     */
    public static Entity createEntity(Engine engine, Character character) {
        Entity entity = engine.createEntity();

        IdComponent idCmp = engine.createComponent(IdComponent.class);
        idCmp.id = character.id();
        entity.add(idCmp);

        NameComponent nameCmp = engine.createComponent(NameComponent.class);
        nameCmp.name = character.name();
        entity.add(nameCmp);

        LevelComponent levelCmp = engine.createComponent(LevelComponent.class);
        levelCmp.level = character.level();
        levelCmp.experience = character.experience();
        entity.add(levelCmp);

        HealthComponent healthCmp = engine.createComponent(HealthComponent.class);
        healthCmp.maxHealth = character.maxHealth();
        healthCmp.currentHealth = character.maxHealth(); // start at max
        entity.add(healthCmp);

        ManaComponent manaCmp = engine.createComponent(ManaComponent.class);
        manaCmp.maxMana = character.maxMana();
        manaCmp.currentMana = character.maxMana(); // start at max
        entity.add(manaCmp);

        BaseStatsComponent baseStatsCmp = engine.createComponent(BaseStatsComponent.class);
        baseStatsCmp.baseDefence = character.baseDefence();
        baseStatsCmp.baseAttackPower = character.baseAttackPower();
        entity.add(baseStatsCmp);

        StatsComponent statsCmp = engine.createComponent(StatsComponent.class);
        statsCmp.stats = character.stats();
        entity.add(statsCmp);

        InventoryComponent invCmp = engine.createComponent(InventoryComponent.class);
        invCmp.weapons.addAll(character.weapons());
        invCmp.skills.addAll(character.skills());
        invCmp.pets.addAll(character.pets());
        entity.add(invCmp);

        LoadoutComponent loadoutCmp = engine.createComponent(LoadoutComponent.class);
        loadoutCmp.loadout = character.loadout();
        entity.add(loadoutCmp);

        return entity;
    }
}
