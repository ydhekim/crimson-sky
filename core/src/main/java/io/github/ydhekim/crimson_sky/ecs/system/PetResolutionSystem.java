package io.github.ydhekim.crimson_sky.ecs.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.github.ydhekim.crimson_sky.combat.PetResolver;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.ecs.component.PetActionComponent;
import io.github.ydhekim.crimson_sky.ecs.component.PetSlotComponent;
import io.github.ydhekim.crimson_sky.ecs.component.StatsComponent;

import java.util.SplittableRandom;

/**
 * Runs the GDD §3 Step 2 pet check for each combatant, storing the outcome on a
 * {@link PetActionComponent} (story A2). The Insight roll itself lives in the pure
 * {@link PetResolver}; this system only bridges ECS components in and out. Runs
 * <b>independently</b> of {@link ActionResolutionSystem} — the pet acts (or not) regardless of
 * whether the character's action succeeded, was Burned, or fell back to Punch.
 *
 * <p>Discrete/turn-based and RNG-seeded on the same reproducible sequence as the rest of the cascade
 * (see {@link ActionResolutionSystem} for the shared conventions). Entities with no
 * {@link PetSlotComponent} (or an empty one) simply never append a pet action.
 */
public class PetResolutionSystem extends IteratingSystem {

    private final SplittableRandom rng;

    private final ComponentMapper<StatsComponent> statsMapper = ComponentMapper.getFor(StatsComponent.class);
    private final ComponentMapper<PetSlotComponent> petMapper = ComponentMapper.getFor(PetSlotComponent.class);
    private final ComponentMapper<PetActionComponent> petActionMapper = ComponentMapper.getFor(PetActionComponent.class);

    public PetResolutionSystem(SplittableRandom rng) {
        // A pet check needs stats (Insight) and a pet slot; stats-only entities are skipped here.
        super(Family.all(StatsComponent.class, PetSlotComponent.class).get());
        this.rng = rng;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        StatsComponent stats = statsMapper.get(entity);
        PetSlotComponent petSlot = petMapper.get(entity);

        // null pet ⇒ PetResolver consumes no roll and returns null (no pet action this turn).
        ResolvedAction petAction = PetResolver.resolvePetAction(stats.stats, petSlot.equipped, rng);

        PetActionComponent actionComponent = petActionMapper.get(entity);
        if (actionComponent == null) {
            actionComponent = getEngine().createComponent(PetActionComponent.class);
            entity.add(actionComponent);
        }
        actionComponent.action = petAction; // may be null when the pet did not act
    }
}
