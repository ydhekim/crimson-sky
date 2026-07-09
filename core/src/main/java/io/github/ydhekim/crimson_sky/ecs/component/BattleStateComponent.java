package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

/**
 * GDD §4 "Battle State (Memory)" — volatile per-battle bookkeeping that resets every battle and is
 * <b>never persisted</b> (system design §3/§8). Lives only on entities inside an active
 * {@code BattleSession}.
 *
 * <ul>
 *   <li>{@code spentMana}/{@code spentStamina} — cumulative resource drawn this battle. The battle
 *       turn resolution subtracts these from {@code maxMp}/{@code maxStamina} to get the remaining
 *       pool the pouch affordability walk checks (§4.4). Mirrors on the live
 *       {@link ManaComponent#currentMana}/{@link StaminaComponent#currentStamina} pools.</li>
 *   <li>{@code petUsedThisTurn} — cleared at the start of each turn; set once the pet's independent
 *       Insight check has been rolled for the current turn.</li>
 * </ul>
 *
 * <p><b>Corrected from the original design:</b> the pre-Stamina single {@code weaponDepleted}
 * boolean is intentionally <b>not</b> present — it could not represent per-weapon availability
 * across a multi-item pouch and was superseded by {@code spentStamina} (system design §3).
 */
public class BattleStateComponent implements Component, Poolable {
    public int spentMana;
    public int spentStamina;
    public boolean petUsedThisTurn;

    @Override
    public void reset() {
        spentMana = 0;
        spentStamina = 0;
        petUsedThisTurn = false;
    }
}
