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
 * <p>{@code dodgeChanceBonus}/{@code critChanceBonus} are the two the in-battle math reads (§0/§14).
 * {@code weightCapacityBonus} is enforced <b>outside</b> a battle — {@code CharacterService.saveLoadout}
 * gates on it at loadout-save time (§17) via the same {@code PassiveEffects} aggregation that fills this
 * component, so no combat code reads the field; it is carried here to keep the component a complete
 * picture of what's equipped. {@code resourceCostReduction} is populated by no v1.0 tree node and read by
 * nothing yet — forward compatibility with the resource-economy epic, like its {@code PassiveEffectType}.
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
