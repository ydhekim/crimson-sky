package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

/**
 * The physical-path resource pool, mirroring {@link ManaComponent} exactly (system design §4.2/§4.4)
 * — Stamina is to weapons what Mana is to skills. {@code currentStamina} is the live pool the pouch
 * affordability walk checks against each weapon's {@code staminaCost()}; it starts at
 * {@code maxStamina} ({@code Character.maxStamina}) and is drawn down as weapons are used across a
 * battle, rotating the pouch to the next affordable weapon as it depletes.
 */
public class StaminaComponent implements Component, Poolable {
    public int maxStamina;
    public int currentStamina;

    @Override
    public void reset() {
        maxStamina = 0;
        currentStamina = 0;
    }
}
