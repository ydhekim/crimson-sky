package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;

/**
 * Merges a combatant's character action and (optional) pet action into the ordered Result Set the
 * GDD §3 Step 3 describes — {@code [character action, pet action]}, matching the GDD's own
 * {@code [3x Hammer, 2x Wolf]} shape. Shared by
 * {@link io.github.ydhekim.crimson_sky.ecs.system.ResultCompilationSystem} (decision-only, damage 0)
 * and {@link BattleEngine} (which passes already-damage-applied entries), so the ordering rule lives
 * in exactly one place.
 */
public final class ResultCompiler {

    private ResultCompiler() {
    }

    /**
     * @param characterAction the character's resolved action (never {@code null} — always at least a Punch)
     * @param petAction       the pet's action, or {@code null} if the pet did not act this turn
     * @return a new ordered array: character first, pet (if any) second
     */
    public static Array<ResolvedAction> compile(ResolvedAction characterAction, ResolvedAction petAction) {
        Array<ResolvedAction> actions = new Array<>();
        actions.add(characterAction);
        if (petAction != null) {
            actions.add(petAction);
        }
        return actions;
    }
}
