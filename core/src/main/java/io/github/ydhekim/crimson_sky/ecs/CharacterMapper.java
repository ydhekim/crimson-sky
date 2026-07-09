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
import io.github.ydhekim.crimson_sky.ecs.component.StaminaComponent;
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
        healthCmp.maxHealth = character.maxHp();
        healthCmp.currentHealth = character.maxHp(); // start at max
        entity.add(healthCmp);

        ManaComponent manaCmp = engine.createComponent(ManaComponent.class);
        manaCmp.maxMana = character.maxMp();
        manaCmp.currentMana = character.maxMp(); // start at max
        entity.add(manaCmp);

        StaminaComponent staminaCmp = engine.createComponent(StaminaComponent.class);
        staminaCmp.maxStamina = character.maxStamina();
        staminaCmp.currentStamina = character.maxStamina(); // start at max
        entity.add(staminaCmp);

        BaseStatsComponent baseStatsCmp = engine.createComponent(BaseStatsComponent.class);
        baseStatsCmp.baseDefence = character.baseDef();
        baseStatsCmp.baseAttackPower = character.baseAtk();
        entity.add(baseStatsCmp);

        StatsComponent statsCmp = engine.createComponent(StatsComponent.class);
        statsCmp.stats = character.stats();
        entity.add(statsCmp);

        InventoryComponent invCmp = engine.createComponent(InventoryComponent.class);
        invCmp.inventory = character.inventory();
        entity.add(invCmp);

        LoadoutComponent loadoutCmp = engine.createComponent(LoadoutComponent.class);
        loadoutCmp.loadout = character.loadout();
        entity.add(loadoutCmp);

        return entity;
    }
}
