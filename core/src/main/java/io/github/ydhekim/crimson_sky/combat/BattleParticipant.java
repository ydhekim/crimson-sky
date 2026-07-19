package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
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
import io.github.ydhekim.crimson_sky.ecs.component.ConsumableSlotComponent;
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
    private static final ComponentMapper<ConsumableSlotComponent> CONSUMABLES = ComponentMapper.getFor(ConsumableSlotComponent.class);
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
     * skills in the priority pouch and CONSUMABLE skills in their own, at their <i>inventory</i> charges,
     * PASSIVE in neither per §4.4; the first loadout pet at its <i>inventory</i> health), the
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
     *
     * <p><b>Pet health comes from Inventory too (§18)</b>, for exactly the same reasons and through the
     * same shape of cross-reference — pet wear was designed as durability's mirror, so it resolves the
     * Loadout-vs-Inventory drift the same way rather than inventing a second answer. <b>Potion charges
     * likewise (§18)</b>: a client could otherwise hand back a full flask the database knows is empty.
     */
    public static BattleParticipant fromCharacter(Engine engine, Character character) {
        Entity entity = CharacterMapper.createEntity(engine, character);

        WeaponSlotComponent weaponSlot = engine.createComponent(WeaponSlotComponent.class);
        SkillSlotComponent skillSlot = engine.createComponent(SkillSlotComponent.class);
        ConsumableSlotComponent consumableSlot = engine.createComponent(ConsumableSlotComponent.class);
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
                    } else if (skill.type() == SkillType.CONSUMABLE) {
                        Skill crossReferenced = atInventoryCharges(skill, character.inventory());
                        consumableSlot.equipped.add(crossReferenced);
                        consumableSlot.remainingCharges.add(crossReferenced.charges());
                    }
                    // PASSIVE skills are folded in below, via PassiveEffects — they enter no pouch at all.
                }
            }
            if (loadout.pets() != null && loadout.pets().size > 0) {
                Pet crossReferenced = atInventoryPetHealth(loadout.pets().first(), character.inventory());
                petSlot.equipped = crossReferenced;
                petSlot.currentHealth = crossReferenced.currentHealth();
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
        entity.add(consumableSlot);
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

    /**
     * {@code equipped} carrying the health its {@code inventory} counterpart (same id) currently has — the
     * §18 source-of-truth cross-reference, identical in shape to {@link #atInventoryDurability} because pet
     * health and weapon durability are deliberately the same mechanic. Falls back to {@code equipped}'s own
     * value when the id isn't owned (a bot's synthesized loadout, per {@link #fromCharacter}).
     */
    private static Pet atInventoryPetHealth(Pet equipped, Inventory inventory) {
        if (inventory == null || inventory.pets() == null) {
            return equipped;
        }
        for (Pet owned : inventory.pets()) {
            if (owned.id() == equipped.id()) {
                return equipped.withCurrentHealth(owned.currentHealth());
            }
        }
        return equipped;
    }

    /**
     * {@code equipped} carrying the charges its {@code inventory} counterpart (same id) currently has — the
     * §18 source-of-truth cross-reference, the same shape as {@link #atInventoryDurability} and
     * {@link #atInventoryPetHealth}. Falls back to {@code equipped}'s own value when the id isn't owned (a
     * bot's synthesized loadout, per {@link #fromCharacter}).
     *
     * <p>Indexed rather than for-each, unlike its two siblings: this is called from <i>inside</i> the loop
     * over {@code loadout.skills()}, and a {@code gdx.utils.Array} caches one iterator — so a caller that
     * handed the same array as both loadout and inventory (a fixture shortcut, not a shape real data takes)
     * would blow up on nested iteration rather than quietly reading what it meant to.
     */
    private static Skill atInventoryCharges(Skill equipped, Inventory inventory) {
        if (inventory == null || inventory.skills() == null) {
            return equipped;
        }
        Array<Skill> owned = inventory.skills();
        for (int i = 0; i < owned.size; i++) {
            Skill candidate = owned.get(i);
            if (candidate.id() == equipped.id() && candidate.type() == SkillType.CONSUMABLE) {
                return equipped.withCharges(candidate.charges());
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

    public ConsumableSlotComponent consumables() {
        return CONSUMABLES.get(entity);
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
