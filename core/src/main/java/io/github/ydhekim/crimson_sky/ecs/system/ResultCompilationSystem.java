package io.github.ydhekim.crimson_sky.ecs.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.combat.ResultCompiler;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.ecs.component.CharacterActionComponent;
import io.github.ydhekim.crimson_sky.ecs.component.PetActionComponent;
import io.github.ydhekim.crimson_sky.ecs.component.TurnResultComponent;

/**
 * Runs the GDD §3 Step 3 compilation for each combatant, merging the character action
 * ({@link CharacterActionComponent}, written by {@link ActionResolutionSystem}) and the optional pet
 * action ({@link PetActionComponent}, written by {@link PetResolutionSystem}) into the ordered
 * Result Set on a {@link TurnResultComponent} (story A3). Ordering ({@code [character, pet]}) lives
 * in the shared {@link ResultCompiler}.
 *
 * <p><b>Decision-only.</b> The entries produced here carry {@code damage = 0}: this system compiles
 * <i>what</i> the Result Set is, not <i>how much HP it removes</i>. Per-hit damage/dodge/win-condition
 * application is {@link io.github.ydhekim.crimson_sky.combat.BattleEngine}'s job once two participants
 * exist (system design §4.2/A5). The array serializes directly into {@code CombatActionResponse.actions()}.
 */
public class ResultCompilationSystem extends IteratingSystem {

    private final ComponentMapper<CharacterActionComponent> characterActionMapper = ComponentMapper.getFor(CharacterActionComponent.class);
    private final ComponentMapper<PetActionComponent> petActionMapper = ComponentMapper.getFor(PetActionComponent.class);
    private final ComponentMapper<TurnResultComponent> turnResultMapper = ComponentMapper.getFor(TurnResultComponent.class);

    private long turnNumber;

    public ResultCompilationSystem() {
        // Compilation needs at least a resolved character action; the pet action is optional.
        super(Family.all(CharacterActionComponent.class).get());
    }

    /** Advances the turn counter stamped onto each {@link TurnResultComponent} this pass. */
    public void setTurnNumber(long turnNumber) {
        this.turnNumber = turnNumber;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ResolvedAction characterAction = characterActionMapper.get(entity).action;

        PetActionComponent petActionComponent = petActionMapper.get(entity);
        ResolvedAction petAction = petActionComponent != null ? petActionComponent.action : null;

        Array<ResolvedAction> compiled = ResultCompiler.compile(characterAction, petAction);

        TurnResultComponent turnResult = turnResultMapper.get(entity);
        if (turnResult == null) {
            turnResult = getEngine().createComponent(TurnResultComponent.class);
            entity.add(turnResult);
        }
        turnResult.actions.clear();
        turnResult.actions.addAll(compiled);
        turnResult.turnNumber = turnNumber;
    }
}
