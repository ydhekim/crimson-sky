package io.github.ydhekim.crimson_sky.common.model;

/**
 * A skill a character can carry into battle. Two flavors, distinguished by {@link SkillType}:
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
 * </ul>
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
    PassiveEffectType passiveEffect,   // null when type == ACTIVE
    int passiveMagnitude,              // 0 when type == ACTIVE
    StatName passiveTargetStat         // null unless passiveEffect == STAT_BONUS
) {
}
