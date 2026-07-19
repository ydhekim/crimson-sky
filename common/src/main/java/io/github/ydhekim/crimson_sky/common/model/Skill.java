package io.github.ydhekim.crimson_sky.common.model;

/**
 * A skill a character can carry into battle. Three flavors, distinguished by {@link SkillType}:
 *
 * <ul>
 *   <li><b>{@code ACTIVE}</b> — a magical-path attack. Like {@link Weapon}, damage is a
 *       {@code minAttack}..{@code maxAttack} range (system design §4.2) — the wielder's INT bonus is
 *       added on top at hit time. {@code manaCost} is drawn from the Mana pool per cast and
 *       {@code difficultyToAct} soft-penalizes the skill-cast roll and its frequency (§4.3). The
 *       three trailing passive fields are meaningless here: {@code passiveEffect} is {@code null},
 *       {@code passiveMagnitude} is {@code 0}, {@code passiveTargetStat} is {@code null}.</li>
 *   <li><b>{@code PASSIVE}</b> — a permanent bonus that only applies while equipped (skill-tree
 *       content, system design §16). The ACTIVE-only fields ({@code manaCost}/{@code minAttack}/
 *       {@code maxAttack}) are unused; a passive never enters the turn cascade. Instead
 *       {@code passiveEffect} says what it does and {@code passiveMagnitude} how much, folded once at
 *       {@code BattleParticipant.fromCharacter()}. {@code passiveTargetStat} names the stat raised,
 *       and is only meaningful when {@code passiveEffect == STAT_BONUS}.</li>
 *   <li><b>{@code CONSUMABLE}</b> — a potion (system design §18). It never attacks; it restores
 *       {@code restoresResource} by a flat {@code restoreAmount} the moment that pool drops to or below
 *       {@code thresholdPercent} of its max, in place of the turn's whole weapon/skill cascade. The four
 *       trailing fields are meaningful only here, and everything above them ({@code manaCost}/
 *       {@code difficultyToAct}/the attack range/the passive trio) is unused.</li>
 * </ul>
 *
 * <p><b>Potency is a property of the potion, not the drinker (§18):</b> {@code restoreAmount} is a flat
 * quantity — no range, no stat bonus, no roll. A bigger potion is a separately authored instance ("Small
 * Health Potion" at 100, "Medium" at 200), the same content-authoring pattern the three starter
 * {@link Weapon}s already use, which is why a potion trigger is the one branch of the cascade that
 * consumes no RNG at all.
 *
 * <p><b>{@code charges} is persisted state, like {@link Weapon#currentDurability()} (§18):</b>
 * {@link Inventory} is its source of truth and a {@link Loadout}'s copy can go stale, so combat
 * cross-references the equipped id against inventory at battle setup. Unlike durability, it depletes once
 * per <i>trigger</i> rather than once per battle — a long fight can genuinely cross the threshold twice.
 * At {@code 0} the potion simply never triggers again; there is no repair for a spent one.
 *
 * <p><b>Skill-tree rank convention (§16):</b> the tree grants one {@code Skill} instance per node,
 * whose {@code passiveMagnitude} already carries the <i>full, rank-scaled</i> magnitude for its
 * current rank (per-rank increment × rank, computed once at grant time in {@code SkillTreeCatalog}).
 * Combat reads {@code passiveMagnitude} directly, so an equipped passive needs no rank lookup.
 */
public record Skill(
    long id,
    String name,
    String description,
    SkillType type,
    int manaCost,
    Difficulty difficultyToAct,
    int minAttack,
    int maxAttack,
    PassiveEffectType passiveEffect,   // null unless type == PASSIVE
    int passiveMagnitude,              // 0 unless type == PASSIVE
    StatName passiveTargetStat,        // null unless passiveEffect == STAT_BONUS
    ResourceType restoresResource,     // null unless type == CONSUMABLE
    int thresholdPercent,              // 0 unless type == CONSUMABLE
    int restoreAmount,                 // 0 unless type == CONSUMABLE
    int charges                        // 0 unless type == CONSUMABLE
) {

    /**
     * A copy with {@code charges} reduced by {@code times}, floored at 0 — this battle's tally of actual
     * triggers (§18). The post-battle write's counterpart to {@link Weapon#worn()}, taking a count rather
     * than assuming 1, because a potion is spent per trigger and not per battle.
     */
    public Skill consumed(int times) {
        return new Skill(id, name, description, type, manaCost, difficultyToAct, minAttack, maxAttack,
            passiveEffect, passiveMagnitude, passiveTargetStat,
            restoresResource, thresholdPercent, restoreAmount, Math.max(0, charges - times));
    }

    /**
     * A copy carrying {@code remaining} as its current charge count — the battle-setup cross-reference
     * that pulls an equipped potion's charges off the {@link Inventory} copy (§18), mirroring
     * {@link Weapon#withCurrentDurability(int)}.
     */
    public Skill withCharges(int remaining) {
        return new Skill(id, name, description, type, manaCost, difficultyToAct, minAttack, maxAttack,
            passiveEffect, passiveMagnitude, passiveTargetStat,
            restoresResource, thresholdPercent, restoreAmount, remaining);
    }
}
