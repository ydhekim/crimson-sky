package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.ecs.CharacterMapper;
import io.github.ydhekim.crimson_sky.ecs.component.BaseStatsComponent;
import io.github.ydhekim.crimson_sky.ecs.component.BattleStateComponent;
import io.github.ydhekim.crimson_sky.ecs.component.HealthComponent;
import io.github.ydhekim.crimson_sky.ecs.component.LoadoutComponent;
import io.github.ydhekim.crimson_sky.ecs.component.ManaComponent;
import io.github.ydhekim.crimson_sky.ecs.component.PassiveModifiersComponent;
import io.github.ydhekim.crimson_sky.ecs.component.PetSlotComponent;
import io.github.ydhekim.crimson_sky.ecs.component.SkillSlotComponent;
import io.github.ydhekim.crimson_sky.ecs.component.StaminaComponent;
import io.github.ydhekim.crimson_sky.ecs.component.StatsComponent;
import io.github.ydhekim.crimson_sky.ecs.component.TurnResultComponent;
import io.github.ydhekim.crimson_sky.ecs.component.WeaponSlotComponent;

/**
 * One combatant inside a {@link BattleSession}: a thin wrapper over an Ashley {@link Entity} whose
 * components hold all simulation state (per the "simulation state lives strictly in ECS components"
 * rule in CLAUDE.md), exposing typed accessors {@link BattleEngine} reads/writes. Holding an entity
 * reference plus its {@link BattleStateComponent} is exactly the shape system design §7 specifies.
 *
 * <p><b>Architectural note (flagged for close-out):</b> §7 describes the participant as "a character
 * entity reference + its BattleStateComponent" but doesn't pin how the battle-ready entity is
 * assembled. {@link #fromCharacter} is that seam — it reuses {@link CharacterMapper} for the base
 * entity, then layers the battle-only components (the priority pouches drawn from the loadout, plus
 * a fresh {@link BattleStateComponent}). Populating pouches here rather than in {@code CharacterMapper}
 * keeps loadout→pouch selection a battle-setup concern (system design §4.1), leaving the mapper as the
 * pure DTO→base-entity bridge.
 */
public class BattleParticipant {

    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<ManaComponent> MANA = ComponentMapper.getFor(ManaComponent.class);
    private static final ComponentMapper<StaminaComponent> STAMINA = ComponentMapper.getFor(StaminaComponent.class);
    private static final ComponentMapper<StatsComponent> STATS = ComponentMapper.getFor(StatsComponent.class);
    private static final ComponentMapper<BaseStatsComponent> BASE_STATS = ComponentMapper.getFor(BaseStatsComponent.class);
    private static final ComponentMapper<WeaponSlotComponent> WEAPONS = ComponentMapper.getFor(WeaponSlotComponent.class);
    private static final ComponentMapper<SkillSlotComponent> SKILLS = ComponentMapper.getFor(SkillSlotComponent.class);
    private static final ComponentMapper<PetSlotComponent> PET = ComponentMapper.getFor(PetSlotComponent.class);
    private static final ComponentMapper<PassiveModifiersComponent> PASSIVES = ComponentMapper.getFor(PassiveModifiersComponent.class);
    private static final ComponentMapper<BattleStateComponent> BATTLE_STATE = ComponentMapper.getFor(BattleStateComponent.class);
    private static final ComponentMapper<TurnResultComponent> TURN_RESULT = ComponentMapper.getFor(TurnResultComponent.class);

    private final Entity entity;

    /** Wraps an already battle-ready entity (all combat components present). */
    public BattleParticipant(Entity entity) {
        this.entity = entity;
    }

    /**
     * Assembles a battle-ready participant from a persisted {@link Character}: base entity via
     * {@link CharacterMapper} (id/name/health/mana/stamina/stats/base-stats/loadout), then the
     * battle-only pouches (all loadout weapons in order, at their <i>inventory</i> durability; ACTIVE
     * skills only, PASSIVE filtered out per §4.4; the first loadout pet with battle-scoped HP), the
     * {@link PassiveModifiersComponent} derived from equipped PASSIVE skills (§16), and a zeroed
     * {@link BattleStateComponent}. The entity is added to {@code engine} so its lifecycle is owned there.
     *
     * <p><b>Passive derivation (§16), the single per-battle translation boundary:</b> equipped
     * {@code PASSIVE} skills are read once here, via {@link PassiveEffects} — the same pure aggregation
     * {@code CharacterService.saveLoadout} runs for §17's weight gate, so the two can never compute a
     * different bonus for the same character. A {@code STAT_BONUS} passive folds into the
     * {@link StatsComponent}'s named stat; the flat knobs land on {@link PassiveModifiersComponent}.
     * {@code ACTIVE} skills are untouched by this — they still populate the priority pouch as before.
     *
     * <p><b>Durability comes from Inventory, never Loadout (§17):</b> both records hold their own copy of
     * an equipped weapon, and {@code saveLoadout} lets a client submit its own copy at any time, so the
     * two can drift — and the post-battle decrement only ever writes inventory. Each equipped weapon is
     * therefore cross-referenced by id against {@code character.inventory()} and takes <i>that</i> copy's
     * {@code currentDurability}. A weapon whose id isn't in inventory keeps its own value: defensive
     * (saveLoadout already enforces item ownership), and the case a bot legitimately hits — a synthesized
     * opponent has a loadout but an empty inventory.
     */
    public static BattleParticipant fromCharacter(Engine engine, Character character) {
        Entity entity = CharacterMapper.createEntity(engine, character);

        WeaponSlotComponent weaponSlot = engine.createComponent(WeaponSlotComponent.class);
        SkillSlotComponent skillSlot = engine.createComponent(SkillSlotComponent.class);
        PetSlotComponent petSlot = engine.createComponent(PetSlotComponent.class);
        PassiveModifiersComponent passiveModifiers = engine.createComponent(PassiveModifiersComponent.class);
        StatsComponent statsCmp = STATS.get(entity);

        LoadoutComponent loadoutCmp = entity.getComponent(LoadoutComponent.class);
        if (loadoutCmp != null && loadoutCmp.loadout != null) {
            Loadout loadout = loadoutCmp.loadout;

            if (loadout.weapons() != null) {
                for (Weapon weapon : loadout.weapons()) {
                    weaponSlot.equipped.add(atInventoryDurability(weapon, character.inventory()));
                }
            }
            if (loadout.skills() != null) {
                for (Skill skill : loadout.skills()) {
                    if (skill.type() == SkillType.ACTIVE) { // passives never enter the priority pouch
                        skillSlot.equipped.add(skill);
                    }
                }
            }
            if (loadout.pets() != null && loadout.pets().size > 0) {
                Pet pet = loadout.pets().first();
                petSlot.equipped = pet;
                petSlot.currentHealth = pet.healthPoint();
            }

            if (statsCmp != null && statsCmp.stats != null) {
                statsCmp.stats = PassiveEffects.applyStatBonuses(statsCmp.stats, loadout);
            }
            passiveModifiers.dodgeChanceBonus = PassiveEffects.totalDodgeChanceBonus(loadout);
            passiveModifiers.critChanceBonus = PassiveEffects.totalCritChanceBonus(loadout);
            passiveModifiers.resourceCostReduction = PassiveEffects.totalResourceCostReduction(loadout);
            passiveModifiers.weightCapacityBonus = PassiveEffects.totalWeightCapacityBonus(loadout);
        }
        entity.add(weaponSlot);
        entity.add(skillSlot);
        entity.add(petSlot);
        entity.add(passiveModifiers);
        entity.add(engine.createComponent(BattleStateComponent.class));

        engine.addEntity(entity);
        return new BattleParticipant(entity);
    }

    /**
     * {@code equipped} carrying the durability its {@code inventory} counterpart (same id) currently has —
     * the §17 source-of-truth cross-reference. Falls back to {@code equipped}'s own value when the id
     * isn't owned (see {@link #fromCharacter}).
     */
    private static Weapon atInventoryDurability(Weapon equipped, Inventory inventory) {
        if (inventory == null || inventory.weapons() == null) {
            return equipped;
        }
        for (Weapon owned : inventory.weapons()) {
            if (owned.id() == equipped.id()) {
                return equipped.withCurrentDurability(owned.currentDurability());
            }
        }
        return equipped;
    }

    public Entity entity() {
        return entity;
    }

    public HealthComponent health() {
        return HEALTH.get(entity);
    }

    public ManaComponent mana() {
        return MANA.get(entity);
    }

    public StaminaComponent stamina() {
        return STAMINA.get(entity);
    }

    public StatsComponent statsComponent() {
        return STATS.get(entity);
    }

    public BaseStatsComponent baseStats() {
        return BASE_STATS.get(entity);
    }

    public WeaponSlotComponent weapons() {
        return WEAPONS.get(entity);
    }

    public SkillSlotComponent skills() {
        return SKILLS.get(entity);
    }

    public PetSlotComponent pet() {
        return PET.get(entity);
    }

    public PassiveModifiersComponent passiveModifiers() {
        return PASSIVES.get(entity);
    }

    public BattleStateComponent battleState() {
        return BATTLE_STATE.get(entity);
    }

    public TurnResultComponent turnResult() {
        return TURN_RESULT.get(entity);
    }

    /** Convenience: HP has dropped to or below zero (the loss condition, §4.2). */
    public boolean isDefeated() {
        return health().currentHealth <= 0;
    }

    /** Remaining HP as a fraction of max, used for the turn-cap tiebreak (§4.2). */
    public float healthFraction() {
        HealthComponent hp = health();
        return hp.maxHealth <= 0 ? 0f : (float) hp.currentHealth / hp.maxHealth;
    }
}
