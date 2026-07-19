package io.github.ydhekim.crimson_sky.server.content;

import io.github.ydhekim.crimson_sky.common.model.Difficulty;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.PassiveEffectType;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import io.github.ydhekim.crimson_sky.common.model.StatName;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The hardcoded v1.0 skill tree (system design §16). Content — which nodes exist, their gates, effects,
 * and costs — is not DB-driven yet (same as weapons/skills/pets today, deferred to Epic E1/M5), so it
 * lives here as constants, duplicated rather than seeded into a table that doesn't exist.
 *
 * <p><b>Scope decision (§16, stated plainly):</b> all 20 v1.0 nodes are {@code PASSIVE}. The tree's
 * whole point was unlocking selectable passives; brand-new {@code ACTIVE} tree content (new attack
 * skills balanced against the Spark/Lightning Bolt/Fireball/Meteor tier system) is a separate, larger
 * content question, deferred. Every node is {@code STAT_BONUS}, {@code DODGE_CHANCE_BONUS}, or
 * {@code CRIT_CHANCE_BONUS} — the three effect types this pass wires into combat.
 *
 * <p><b>Structure:</b> three level-gated branches (Physical / Magical / Universal), each with tiers at
 * levels 1/20/40 and two nodes per tier (18 nodes); plus a Faction branch — not level-gated, matched to
 * the character's own {@link Faction} — with one node each ({@code faction.crimson.n1} crit,
 * {@code faction.skyborn.n1} dodge). Faction mapping: {@link Faction#A} → Crimson (crit),
 * {@link Faction#B} → Skyborn (dodge), matching the creation screen's placeholder descriptions.
 *
 * <p><b>Magnitude convention:</b> {@link Node#magnitudePerRank} is the <i>per-rank increment</i>. The
 * granted {@link Skill} from {@link Node#skillAtRank(int)} carries the full, rank-scaled magnitude
 * ({@code magnitudePerRank × rank}), so rank 3 of Precision Strikes (+3%/rank) grants a Skill with
 * {@code passiveMagnitude == 9} and combat reads it directly with no rank arithmetic.
 *
 * <p><b>Cost (§16):</b> per rank, paid each rank (not once). Tier 1 — 1 SP + 10 gold; tier 2 and
 * Faction — 3 SP + 60 gold; tier 3 — 6 SP + 150 gold. {@value #MAX_RANK} ranks per node.
 */
public final class SkillTreeCatalog {

    public static final int MAX_RANK = 3;

    private SkillTreeCatalog() {
    }

    /**
     * One tree node. {@code skillId} is the stable numeric {@link Skill#id()} shared across all ranks of
     * this node (starting at 1000, clear of starter content's 1–4 range) — learning grants it, upgrading
     * replaces the same id's inventory entry in place. {@code levelGate} is the level required to learn
     * the node and is ignored for Faction nodes; {@code requiredFaction} is non-null only for Faction
     * nodes, whose access is gated by faction match instead of level.
     */
    public record Node(
        String nodeId,
        long skillId,
        String name,
        int levelGate,
        Faction requiredFaction,
        PassiveEffectType effect,
        StatName targetStat,      // null unless effect == STAT_BONUS
        int magnitudePerRank,
        int skillPointCostPerRank,
        int goldCostPerRank
    ) {
        public boolean isFactionNode() {
            return requiredFaction != null;
        }

        /**
         * The {@link Skill} granted for {@code rank} (1..{@value #MAX_RANK}) — a {@code PASSIVE} whose
         * {@code passiveMagnitude} is the full rank-scaled value ({@code magnitudePerRank × rank}). The
         * ACTIVE-only fields are neutral ({@code manaCost 0}, {@code Difficulty.EASY}, {@code 0..0}
         * range) since a passive never enters the turn cascade, as are the CONSUMABLE-only ones (§18): no
         * resource, no threshold, no charges — the tree grants no potions.
         */
        public Skill skillAtRank(int rank) {
            return new Skill(skillId, name, "", SkillType.PASSIVE, 0, Difficulty.EASY, 0, 0,
                effect, magnitudePerRank * rank, targetStat, null, 0, 0, 0);
        }
    }

    // --- Per-rank cost tiers (§16) ------------------------------------------------------------------
    private static final int T1_SP = 1;
    private static final int T1_GOLD = 10;
    private static final int T2_SP = 3;
    private static final int T2_GOLD = 60;
    private static final int T3_SP = 6;
    private static final int T3_GOLD = 150;

    private static final Map<String, Node> NODES = buildCatalog();

    /** The node with {@code nodeId}, or {@code null} if no such node exists in the v1.0 catalog. */
    public static Node find(String nodeId) {
        return NODES.get(nodeId);
    }

    private static Map<String, Node> buildCatalog() {
        Map<String, Node> nodes = new LinkedHashMap<>();
        // Physical branch
        add(nodes, stat("physical.t1.n1", 1000L, "Iron Grip", 1, StatName.STRENGTH, 2, T1_SP, T1_GOLD));
        add(nodes, stat("physical.t1.n2", 1001L, "Swift Hands", 1, StatName.DEXTERITY, 2, T1_SP, T1_GOLD));
        add(nodes, stat("physical.t2.n1", 1002L, "Battle Fortitude", 20, StatName.VITALITY, 4, T2_SP, T2_GOLD));
        add(nodes, crit("physical.t2.n2", 1003L, "Precision Strikes", 20, 3, T2_SP, T2_GOLD));
        add(nodes, stat("physical.t3.n1", 1004L, "Juggernaut", 40, StatName.STRENGTH, 6, T3_SP, T3_GOLD));
        add(nodes, crit("physical.t3.n2", 1005L, "Executioner", 40, 5, T3_SP, T3_GOLD));
        // Magical branch
        add(nodes, stat("magical.t1.n1", 1006L, "Arcane Focus", 1, StatName.INTELLIGENCE, 2, T1_SP, T1_GOLD));
        add(nodes, stat("magical.t1.n2", 1007L, "Mind's Clarity", 1, StatName.WISDOM, 2, T1_SP, T1_GOLD));
        add(nodes, stat("magical.t2.n1", 1008L, "Deep Reserves", 20, StatName.SPIRIT, 4, T2_SP, T2_GOLD));
        add(nodes, stat("magical.t2.n2", 1009L, "Spell Weaving", 20, StatName.INTELLIGENCE, 4, T2_SP, T2_GOLD));
        add(nodes, stat("magical.t3.n1", 1010L, "Archmage's Insight", 40, StatName.WISDOM, 6, T3_SP, T3_GOLD));
        add(nodes, stat("magical.t3.n2", 1011L, "Overwhelming Power", 40, StatName.INTELLIGENCE, 6, T3_SP, T3_GOLD));
        // Universal branch
        add(nodes, stat("universal.t1.n1", 1012L, "Fleet Foot", 1, StatName.SPEED, 2, T1_SP, T1_GOLD));
        add(nodes, stat("universal.t1.n2", 1013L, "Keen Senses", 1, StatName.INSIGHT, 2, T1_SP, T1_GOLD));
        add(nodes, dodge("universal.t2.n1", 1014L, "Evasive Instinct", 20, 3, T2_SP, T2_GOLD));
        add(nodes, stat("universal.t2.n2", 1015L, "Tracker's Eye", 20, StatName.INSIGHT, 4, T2_SP, T2_GOLD));
        add(nodes, dodge("universal.t3.n1", 1016L, "Untouchable", 40, 5, T3_SP, T3_GOLD));
        add(nodes, stat("universal.t3.n2", 1017L, "Overdrive", 40, StatName.SPEED, 6, T3_SP, T3_GOLD));
        // Faction branch — not level-gated; gated by faction match instead. Faction cost == tier-2 cost.
        add(nodes, new Node("faction.crimson.n1", 1018L, "Crimson Fury", 0, Faction.A,
            PassiveEffectType.CRIT_CHANCE_BONUS, null, 5, T2_SP, T2_GOLD));
        add(nodes, new Node("faction.skyborn.n1", 1019L, "Skyborn Grace", 0, Faction.B,
            PassiveEffectType.DODGE_CHANCE_BONUS, null, 5, T2_SP, T2_GOLD));
        return nodes;
    }

    private static void add(Map<String, Node> nodes, Node node) {
        nodes.put(node.nodeId(), node);
    }

    private static Node stat(String id, long skillId, String name, int levelGate, StatName targetStat,
                             int magnitudePerRank, int spCost, int goldCost) {
        return new Node(id, skillId, name, levelGate, null, PassiveEffectType.STAT_BONUS, targetStat,
            magnitudePerRank, spCost, goldCost);
    }

    private static Node crit(String id, long skillId, String name, int levelGate,
                             int magnitudePerRank, int spCost, int goldCost) {
        return new Node(id, skillId, name, levelGate, null, PassiveEffectType.CRIT_CHANCE_BONUS, null,
            magnitudePerRank, spCost, goldCost);
    }

    private static Node dodge(String id, long skillId, String name, int levelGate,
                              int magnitudePerRank, int spCost, int goldCost) {
        return new Node(id, skillId, name, levelGate, null, PassiveEffectType.DODGE_CHANCE_BONUS, null,
            magnitudePerRank, spCost, goldCost);
    }
}
