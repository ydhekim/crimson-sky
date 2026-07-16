package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

/**
 * The aggregate of a character's equipped non-stat passives, computed once at
 * {@code BattleParticipant.fromCharacter()} — the single per-battle translation boundary §16 calls for
 * — and read by {@code BattleEngine} while resolving hits. {@code STAT_BONUS} passives are <b>not</b>
 * held here; they fold directly into {@code StatsComponent} at the same boundary. This component only
 * carries the flat, non-stat knobs the damage/evasion math consumes.
 *
 * <p>{@code dodgeChanceBonus}/{@code critChanceBonus} are the two wired into combat this pass (§0/§14).
 * {@code resourceCostReduction} and {@code weightCapacityBonus} are populated by no v1.0 tree node and
 * read by no combat code yet — carried for forward compatibility with the resource economy / weight-cap
 * epics, exactly like their {@code PassiveEffectType} values.
 */
public class PassiveModifiersComponent implements Component, Poolable {
    public int dodgeChanceBonus;
    public int critChanceBonus;
    public int resourceCostReduction;  // unused this pass — no node grants it yet, no combat code reads it
    public int weightCapacityBonus;    // unused this pass — Epic N (weight cap) doesn't exist yet

    @Override
    public void reset() {
        dodgeChanceBonus = 0;
        critChanceBonus = 0;
        resourceCostReduction = 0;
        weightCapacityBonus = 0;
    }
}
