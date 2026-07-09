package io.github.ydhekim.crimson_sky.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.ActionSource;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.ResolvedAction;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.ecs.component.TurnResultComponent;

import java.util.SplittableRandom;

/**
 * Cross-combatant turn orchestration (story B2, system design §4.2) — the {@code TurnOrderSystem}
 * role §4 defers until two-sided battles exist, implemented as a plain orchestrator rather than an
 * {@code IteratingSystem} because per-hit win-condition checks and priority across two entities do
 * not fit the per-entity iteration model. It composes the pure decision layer
 * ({@link ActionResolver}/{@link PetResolver}) with per-hit damage application
 * ({@link DamageCalculator}) and the shared {@link ResultCompiler} ordering.
 *
 * <h2>Turn rules</h2>
 * <ul>
 *   <li><b>Priority</b> is fixed once at construction: higher {@code speed} acts first each turn;
 *       ties are broken by a single seeded coinflip, stable for the whole match (§4.2).</li>
 *   <li>Each turn the higher-priority combatant's <b>full Result Set</b> ({@code [character hits...,
 *       pet hits...]}) resolves and applies first. If it drops the opponent to {@code HP <= 0}, the
 *       battle ends immediately and the lower-priority combatant's set for that turn never runs.</li>
 *   <li><b>Within</b> a Result Set, hits resolve left-to-right with an independent per-hit dodge
 *       roll; the {@code HP <= 0} check runs after <b>every</b> hit, and a kill mid-array skips all
 *       remaining hits in that set (including the pet's).</li>
 *   <li><b>Win condition:</b> {@code HP <= 0} → immediate loss; a turn cap of {@value #TURN_CAP} →
 *       winner is higher remaining HP%, then higher SPD, then a seeded coinflip.</li>
 * </ul>
 *
 * <p>Only 1v1 is exercised at launch; the logic assumes exactly two participants (the opponent is
 * "the other" entry), consistent with system design §7's forward-compatible array model.
 */
public class BattleEngine {

    /** Turn cap (2× the ~20-turn pacing target, §4.2) guarding against a high-mutual-dodge stalemate. */
    public static final int TURN_CAP = 40;

    private final Engine engine;
    private final BattleSession session;
    private final SplittableRandom rng;
    private final Array<BattleParticipant> priorityOrder;

    private long turnNumber;
    private boolean over;
    private BattleParticipant winner;

    /**
     * @param engine  the battle's Ashley engine, used to lazily attach {@link TurnResultComponent}s
     * @param session the session whose two participants and seeded RNG drive the battle
     */
    public BattleEngine(Engine engine, BattleSession session) {
        this.engine = engine;
        this.session = session;
        this.rng = session.rng();
        this.priorityOrder = computePriorityOrder(session.participants());
    }

    /** Higher speed first; a tie is broken by one seeded coinflip, fixed for the whole match (§4.2). */
    private Array<BattleParticipant> computePriorityOrder(Array<BattleParticipant> participants) {
        Array<BattleParticipant> order = new Array<>();
        BattleParticipant a = participants.get(0);
        BattleParticipant b = participants.get(1);
        int sa = a.statsComponent().stats.speed();
        int sb = b.statsComponent().stats.speed();
        boolean aFirst;
        if (sa != sb) {
            aFirst = sa > sb;
        } else {
            aFirst = rng.nextInt(2) == 0; // seeded tiebreak, consumed only on an actual tie
        }
        order.add(aFirst ? a : b);
        order.add(aFirst ? b : a);
        return order;
    }

    /**
     * Resolves exactly one turn. The higher-priority combatant's Result Set resolves first; if it
     * kills, the other's set is skipped and the battle ends. Writes each acting combatant's compiled,
     * damage-applied Result Set onto its {@link TurnResultComponent}.
     *
     * @return the winner if the battle ended this turn, otherwise {@code null}
     */
    public BattleParticipant resolveTurn() {
        if (over) {
            return winner;
        }
        turnNumber++;
        for (BattleParticipant p : priorityOrder) {
            p.battleState().petUsedThisTurn = false; // fresh per-turn pet flag
        }

        for (int i = 0; i < priorityOrder.size; i++) {
            BattleParticipant attacker = priorityOrder.get(i);
            BattleParticipant defender = priorityOrder.get(i == 0 ? 1 : 0);

            resolveResultSet(attacker, defender);

            if (defender.isDefeated()) {
                over = true;
                winner = attacker;
                return winner; // lower-priority combatant's Result Set never runs (§4.2)
            }
        }
        return null;
    }

    /**
     * Runs the battle to a decision: turns until a kill, or the {@value #TURN_CAP} cap, at which point
     * the tiebreak (HP% → SPD → seeded coinflip) decides the winner.
     *
     * @return the winner (never {@code null} once this returns — the cap always yields a decision)
     */
    public BattleParticipant runToCompletion() {
        while (!over && turnNumber < TURN_CAP) {
            resolveTurn();
        }
        if (!over) {
            applyCapTiebreak();
        }
        return winner;
    }

    /** Builds one combatant's Result Set, applying every hit in order with per-hit dodge + win-check. */
    private void resolveResultSet(BattleParticipant attacker, BattleParticipant defender) {
        Stats stats = attacker.statsComponent().stats;

        // Step 1 — character decision, then commit its resource cost.
        CharacterActionResolution charRes = ActionResolver.chooseCharacterAction(
            stats, attacker.weapons().equipped, attacker.skills().equipped,
            attacker.mana().currentMana, attacker.stamina().currentStamina, rng);
        spendResource(attacker, charRes);

        // Step 2 — pet decision, independent of the character outcome (runs even on a Burned cast).
        Pet pet = attacker.pet() != null ? attacker.pet().equipped : null;
        PetActionResolution petRes = PetResolver.choosePetAction(stats, pet, rng);
        attacker.battleState().petUsedThisTurn = pet != null;

        int defenderDef = defender.baseStats().baseDefence;
        int defenderSpeed = defender.statsComponent().stats.speed();

        // Step 3 — apply. Character hits first; then pet hits only if the defender is still standing.
        ResolvedAction characterEntry = applyEntry(
            charRes.action(), charRes.minAttack(), charRes.maxAttack(), charRes.pathStatValue(),
            defender, defenderDef, defenderSpeed);

        ResolvedAction petEntry = null;
        if (petRes != null && !defender.isDefeated()) {
            petEntry = applyEntry(
                petRes.action(), petRes.minAttack(), petRes.maxAttack(), 0 /* pets get no stat bonus */,
                defender, defenderDef, defenderSpeed);
        }
        // If the character's hits killed, the pet entry is intentionally dropped from the Result Set
        // (a kill mid-array skips everything remaining in that set, §4.2).

        writeTurnResult(attacker, ResultCompiler.compile(characterEntry, petEntry));
    }

    /**
     * Applies one Result Set entry: loops the entry's frequency, rolling dodge then (if it lands)
     * damage per hit, subtracting from the defender and stopping the moment HP hits ≤ 0. Returns the
     * entry with its {@code damage} filled in as the total landed damage. A Burned cast applies
     * nothing (damage stays 0).
     */
    private ResolvedAction applyEntry(ResolvedAction entry, int minAttack, int maxAttack,
                                      int pathStatValue, BattleParticipant defender,
                                      int defenderDef, int defenderSpeed) {
        if (entry.failed()) {
            return entry; // Burned cast — no hits land
        }
        int total = 0;
        for (int hit = 0; hit < entry.frequency(); hit++) {
            if (DamageCalculator.rollDodge(defenderSpeed, rng)) {
                continue; // dodged — no damage draw consumed for this hit
            }
            int damage = DamageCalculator.rollHitDamage(minAttack, maxAttack, pathStatValue, defenderDef, rng);
            defender.health().currentHealth -= damage;
            total += damage;
            if (defender.isDefeated()) {
                break; // per-hit win condition: skip the rest of this entry
            }
        }
        return new ResolvedAction(entry.source(), entry.label(), entry.frequency(), entry.failed(), total);
    }

    /** Draws the committed action's cost from the matching pool and mirrors it onto Battle State. */
    private void spendResource(BattleParticipant attacker, CharacterActionResolution charRes) {
        int cost = charRes.resourceCost();
        if (charRes.action().source() == ActionSource.WEAPON) {
            attacker.stamina().currentStamina -= cost;
            attacker.battleState().spentStamina += cost;
        } else if (charRes.action().source() == ActionSource.SKILL && !charRes.action().failed()) {
            attacker.mana().currentMana -= cost;
            attacker.battleState().spentMana += cost;
        }
        // PUNCH and Burned casts cost nothing.
    }

    private void writeTurnResult(BattleParticipant attacker, Array<ResolvedAction> compiled) {
        TurnResultComponent turnResult = attacker.turnResult();
        if (turnResult == null) {
            turnResult = engine.createComponent(TurnResultComponent.class);
            attacker.entity().add(turnResult);
        }
        turnResult.actions.clear();
        turnResult.actions.addAll(compiled);
        turnResult.turnNumber = turnNumber;
    }

    /** Turn-cap decision (§4.2): higher remaining HP% → higher SPD → seeded coinflip. */
    private void applyCapTiebreak() {
        BattleParticipant a = priorityOrder.get(0);
        BattleParticipant b = priorityOrder.get(1);
        float fa = a.healthFraction();
        float fb = b.healthFraction();
        if (fa != fb) {
            winner = fa > fb ? a : b;
        } else {
            int sa = a.statsComponent().stats.speed();
            int sb = b.statsComponent().stats.speed();
            if (sa != sb) {
                winner = sa > sb ? a : b;
            } else {
                winner = rng.nextInt(2) == 0 ? a : b;
            }
        }
        over = true;
    }

    public boolean isOver() {
        return over;
    }

    public BattleParticipant winner() {
        return winner;
    }

    public long turnNumber() {
        return turnNumber;
    }

    /** The fixed priority order (index 0 = acts first each turn). Exposed for inspection/tests. */
    public Array<BattleParticipant> priorityOrder() {
        return priorityOrder;
    }
}
