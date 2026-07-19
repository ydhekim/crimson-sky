package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.Pool.Poolable;
import io.github.ydhekim.crimson_sky.common.model.Skill;

/**
 * The ordered potion "pouch" a character carries into a battle (system design §18) — the sibling of
 * {@link SkillSlotComponent}, holding the {@code CONSUMABLE} skills that component deliberately excludes.
 * <b>Array index is priority</b>: the first potion whose resource has dropped to its threshold and still
 * has a charge left is the one that fires, in place of the whole weapon/skill cascade for that turn.
 *
 * <p>{@code remainingCharges} is index-aligned with {@code equipped} and <b>battle-scoped</b>: seeded once
 * at battle setup from each potion's <i>Inventory</i> charge count (§18's source-of-truth rule, the same one
 * durability and pet health follow), then decremented in real time as the fight goes on. Tracking it here
 * rather than reading {@code Skill.charges} is what stops a 2-charge potion from healing a third time in one
 * long fight — the record itself is immutable, and the persisted decrement only happens post-battle.
 */
public class ConsumableSlotComponent implements Component, Poolable {
    public final Array<Skill> equipped = new Array<>();
    public final IntArray remainingCharges = new IntArray();

    @Override
    public void reset() {
        equipped.clear();
        remainingCharges.clear();
    }
}
