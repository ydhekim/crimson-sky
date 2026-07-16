package io.github.ydhekim.crimson_sky.server.combat;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Difficulty;
import io.github.ydhekim.crimson_sky.common.model.Faction;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Tameness;
import io.github.ydhekim.crimson_sky.common.model.Weapon;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * Synthesizes a throwaway opponent when no persisted character qualifies (system design §7, story B4).
 * The bot is a plain {@link Character} record — never written to the database, never given a real id —
 * so everything downstream ({@code BattleParticipant.fromCharacter}, the whole engine) treats it
 * exactly like a real opponent without knowing the difference.
 *
 * <p><b>Transparency (§7, non-negotiable):</b> nothing about a bot may be observable from the client.
 * Its display name is drawn from the same plausible-name pool a player might pick, with no marker,
 * prefix, or id convention to reverse-engineer; "was this a bot" travels only in the server-internal
 * {@code AttackResult}, never in {@code AttackResponse}.
 *
 * <p><b>Calibration is load-bearing, not cosmetic (§7):</b> bot fights count fully toward Elo and
 * rewards, and a player can never discount a loss as "just a bot". The Elo → stat-budget curve below
 * is a deliberately simple first pass — linear, clamped — and expects a tuning pass (ideally Monte
 * Carlo, like every other number in §4.2) before this ships to real players.
 */
public class BotFactory {

    /** Stat budget at the default starting Elo (1000), spread across the eight stats. */
    static final int BASE_STAT_BUDGET = 200;

    /** Extra stat points per Elo point above (or below) 1000. */
    static final float BUDGET_PER_ELO = 0.25f;

    static final int MIN_STAT_BUDGET = 120;
    static final int MAX_STAT_BUDGET = 640;

    /** Every stat gets at least this much, so no archetype is degenerate (e.g. 0 SPD → never dodges). */
    private static final int MIN_STAT = 5;

    private final Random random;

    public BotFactory() {
        this(new Random());
    }

    /** Test seam: a seeded {@link Random} makes archetype/name choice reproducible. */
    public BotFactory(Random random) {
        this.random = random;
    }

    /**
     * An opponent scaled to {@code elo}, using a randomly chosen archetype. Stat weights are
     * normalized, so an archetype decides the *shape* of a build and the Elo-derived budget decides
     * its *size*.
     */
    public Character createBot(int elo) {
        Archetype archetype = Archetype.VALUES.get(random.nextInt(Archetype.VALUES.size()));
        Stats stats = archetype.distribute(statBudget(elo));
        return assemble(displayName(), stats, archetype.loadout());
    }

    /** Elo → total stat points: linear around the 1000 baseline, clamped at both ends. */
    static int statBudget(int elo) {
        int budget = Math.round(BASE_STAT_BUDGET + (elo - 1000) * BUDGET_PER_ELO);
        return Math.max(MIN_STAT_BUDGET, Math.min(MAX_STAT_BUDGET, budget));
    }

    /**
     * Wraps stats + loadout into a battle-ready {@link Character}. The derived pools use system design
     * §4.2's formulas directly, since a bot has no persisted row for them to have been stored on.
     */
    private Character assemble(String name, Stats stats, Loadout loadout) {
        int maxHp = 100 + stats.vitality() * 11;
        int maxMp = 50 + stats.spirit() * 5;
        int maxStamina = 50 + stats.strength() * 5;
        int baseDef = Math.round((stats.vitality() + stats.spirit()) * 0.6f);
        int baseAtk = Math.round((stats.strength() + stats.intelligence()) * 0.6f);

        return new Character(
            0L /* no persisted id — a bot has no row in `characters` */,
            0L /* no owning account */,
            name,
            random.nextBoolean() ? Faction.A : Faction.B,
            1, 0,
            maxHp, maxMp, maxStamina, baseDef, baseAtk,
            stats,
            new Inventory(new Array<>(), new Array<>(), new Array<>()),
            loadout,
            new HashMap<>());
    }

    /**
     * Indistinguishable from a player-chosen name by construction: no prefix, suffix, or numbering a
     * client could pattern-match on.
     */
    private String displayName() {
        return NAMES.get(random.nextInt(NAMES.size()));
    }

    private static final List<String> NAMES = List.of(
        "Kayra", "Doruk", "Ilkay", "Serra", "Bora", "Nihal", "Tarik", "Esen",
        "Volkan", "Derya", "Alper", "Sena", "Ozan", "Melis", "Cengiz", "Irem");

    // --- Starter content (docs/planning/04-starter-content.md) -------------------------------------
    // Duplicated as constants rather than seeded into the DB, deliberately: Epic E makes content
    // data-driven, and a bot must not depend on a `weapons`/`skills` table that doesn't exist yet.

    // Durability (§17) is full (20/20) on every bot weapon: a bot is synthesized fresh for one battle and
    // has no inventory to wear down, so it must never start a fight holding a broken weapon.
    private static final Weapon TWIN_DAGGERS = new Weapon(1L, "Twin Daggers", "", Rarity.COMMON, 2f, 8, 18, 8, 20, 20);
    private static final Weapon STEEL_LONGSWORD = new Weapon(2L, "Steel Longsword", "", Rarity.UNCOMMON, 15f, 12, 28, 15, 20, 20);
    private static final Weapon WARHAMMER = new Weapon(3L, "Warhammer", "", Rarity.RARE, 40f, 15, 45, 25, 20, 20);

    // ACTIVE skills leave the three trailing passive fields empty (null, 0, null), per §16's Skill shape.
    private static final Skill SPARK = new Skill(1L, "Spark", "", SkillType.ACTIVE, 12, Difficulty.EASY, 20, 40, null, 0, null);
    private static final Skill LIGHTNING_BOLT = new Skill(2L, "Lightning Bolt", "", SkillType.ACTIVE, 28, Difficulty.MEDIUM, 30, 60, null, 0, null);
    private static final Skill METEOR = new Skill(4L, "Meteor", "", SkillType.ACTIVE, 70, Difficulty.MYTHIC, 70, 110, null, 0, null);

    private static final Pet HOUND = new Pet(2L, "Hound", "", Tameness.STUBBORN, 35, 5, 10, 20);
    private static final Pet WOLF = new Pet(3L, "Wolf", "", Tameness.TRACEABLE, 50, 8, 15, 25);
    private static final Pet BEAR = new Pet(4L, "Bear", "", Tameness.LOYAL, 80, 15, 20, 36);

    /**
     * The curated build shapes §7 calls for: a STR/Warhammer tank, an INT/Meteor nuker, and a SPD/DEX
     * dodge-and-poke skirmisher. Weights are relative, normalized against the Elo-derived budget, so
     * adding an archetype never requires rebalancing the others' numbers.
     */
    enum Archetype {
        /** Bruiser: heavy weapon, deep HP/Stamina, slow. */
        TANK(4, 2, 5, 1, 1, 2, 1, 2, Array.with(WARHAMMER, STEEL_LONGSWORD), Array.with(SPARK), Array.with(BEAR)),

        /** Nuker: Meteor first, Lightning Bolt as the affordable fallback once mana thins out. */
        NUKER(1, 1, 2, 5, 4, 4, 1, 2, Array.with(STEEL_LONGSWORD), Array.with(METEOR, LIGHTNING_BOLT), Array.with(WOLF)),

        /** Skirmisher: fast, evasive, spams a cheap weapon. */
        DODGER(2, 5, 2, 1, 2, 1, 5, 2, Array.with(TWIN_DAGGERS), Array.with(SPARK), Array.with(HOUND));

        static final List<Archetype> VALUES = List.of(values());

        private final int[] weights; // str, dex, vit, int, wis, spi, spd, ins
        private final Array<Weapon> weapons;
        private final Array<Skill> skills;
        private final Array<Pet> pets;

        Archetype(int str, int dex, int vit, int intelligence, int wis, int spi, int spd, int ins,
                  Array<Weapon> weapons, Array<Skill> skills, Array<Pet> pets) {
            this.weights = new int[]{str, dex, vit, intelligence, wis, spi, spd, ins};
            this.weapons = weapons;
            this.skills = skills;
            this.pets = pets;
        }

        /** Splits {@code budget} across the eight stats by weight, with a per-stat floor. */
        Stats distribute(int budget) {
            int totalWeight = 0;
            for (int weight : weights) {
                totalWeight += weight;
            }
            int[] points = new int[weights.length];
            for (int i = 0; i < weights.length; i++) {
                points[i] = Math.max(MIN_STAT, Math.round((float) budget * weights[i] / totalWeight));
            }
            return new Stats(points[0], points[1], points[2], points[3], points[4], points[5], points[6], points[7]);
        }

        /** A fresh {@link Loadout} per call — pouches are per-battle, never shared between bots. */
        Loadout loadout() {
            return new Loadout(new Array<>(weapons), new Array<>(skills), new Array<>(pets));
        }
    }
}
