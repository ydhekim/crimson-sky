package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import io.github.ydhekim.crimson_sky.common.model.ActionSource;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.Skill;

/**
 * Pure implementation of the potion check (system design §18) — the game's first conditional/reactive
 * logic, deliberately isolated to one early check rather than threaded through the cascade. Kept free of
 * Ashley and any LibGDX rendering type for the same reason as {@link ActionResolver}/{@link PetResolver}:
 * a plain function of (current/max pools, pouch) that unit-tests headlessly (system design §9).
 *
 * <p>{@link BattleEngine} runs this <b>before</b> the character-action cascade each turn. A triggering
 * potion becomes that turn's entire character action — the weapon/skill cascade is skipped — while the
 * pet's independent action is unaffected either way.
 *
 * <p><b>It takes no {@code SplittableRandom}, and that is the design, not an omission</b> (§18): a potion's
 * restore amount is flat and its trigger is a threshold comparison, so this is the one branch of the whole
 * cascade that consumes no RNG. A build carrying a potion that never fires therefore draws exactly the same
 * stream a potion-less build would.
 *
 * <p><b>A malformed potion restores nothing rather than throwing</b> — the same tolerance {@code PassiveEffects}
 * gives a passive with no effect type, and for a sharper reason: an equipped {@code Skill} arrives from a
 * client-submitted {@code Loadout} ({@code saveLoadout}), so a {@code CONSUMABLE} naming no
 * {@link io.github.ydhekim.crimson_sky.common.model.ResourceType} is a shape the server can be handed, and it
 * must cost that player a potion that never fires — not the whole battle.
 */
public final class ConsumableResolver {

    private ConsumableResolver() {
    }

    /**
     * The first equipped potion, in priority order, whose resource has dropped to its threshold and which
     * still has a charge left this battle; {@code null} when none triggers (the usual case — the cascade
     * then proceeds completely unchanged).
     *
     * @param equipped         the priority-ordered potion pouch (index 0 = checked first); may be empty
     * @param remainingCharges battle-scoped charges, index-aligned with {@code equipped}
     */
    static ConsumableActionResolution chooseConsumable(
            int currentHealth, int maxHealth, int currentMana, int maxMana,
            int currentStamina, int maxStamina, Array<Skill> equipped, IntArray remainingCharges) {
        for (int i = 0; i < equipped.size; i++) {
            Skill skill = equipped.get(i);
            if (remainingCharges.get(i) <= 0) {
                continue; // spent this battle — skipped exactly like an unaffordable weapon (§18)
            }
            if (skill.restoresResource() == null) {
                continue; // malformed: a CONSUMABLE naming no resource restores nothing (see class doc)
            }
            boolean triggers = switch (skill.restoresResource()) {
                case HEALTH -> CombatMath.isBelowThreshold(currentHealth, maxHealth, skill.thresholdPercent());
                case MANA -> CombatMath.isBelowThreshold(currentMana, maxMana, skill.thresholdPercent());
                case STAMINA -> CombatMath.isBelowThreshold(currentStamina, maxStamina, skill.thresholdPercent());
            };
            if (triggers) {
                // The six-arg constructor, not the decision-layer convenience: a potion's `damage` (here,
                // the amount restored to the actor) is final the moment it is chosen — there is no
                // mitigation math for BattleEngine to fill in later.
                ResolvedAction action = new ResolvedAction(ActionSource.CONSUMABLE, skill.name(), 1, false,
                    skill.restoreAmount(), skill.id());
                return new ConsumableActionResolution(i, action, skill.restoresResource(), skill.restoreAmount());
            }
        }
        return null;
    }
}
