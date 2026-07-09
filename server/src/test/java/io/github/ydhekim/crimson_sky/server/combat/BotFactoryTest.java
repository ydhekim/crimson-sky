package io.github.ydhekim.crimson_sky.server.combat;

import com.badlogic.ashley.core.Engine;
import io.github.ydhekim.crimson_sky.combat.BattleParticipant;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.SkillType;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story B4 / system design §7: a synthesized opponent is a usable {@link Character} at any Elo, and
 * its stat budget tracks Elo. Exact numbers are deliberately not asserted — the Elo → budget curve is
 * an explicit first pass awaiting a tuning pass, so pinning it here would just be re-stating the
 * implementation. What must hold is the direction and the usability.
 */
class BotFactoryTest {

    @BeforeEach
    void installHeadlessGdx() {
        HeadlessGdx.install();
    }

    private static int totalStats(Stats stats) {
        return stats.strength() + stats.dexterity() + stats.vitality() + stats.intelligence()
            + stats.wisdom() + stats.spirit() + stats.speed() + stats.insight();
    }

    @Test
    void producesAUsableCharacterAcrossAWideEloRange() {
        for (int elo : new int[]{500, 1000, 1500, 2500}) {
            Character bot = new BotFactory(new Random(elo)).createBot(elo);

            assertNotNull(bot.name());
            assertFalse(bot.name().isBlank(), "a bot needs a display name at Elo " + elo);
            assertEquals(0L, bot.id(), "a bot is never persisted, so it carries no real id");

            assertTrue(bot.maxHp() > 0 && bot.maxMp() > 0 && bot.maxStamina() > 0,
                "derived pools must be positive at Elo " + elo);
            assertTrue(totalStats(bot.stats()) > 0);

            boolean hasWeapon = bot.loadout().weapons().size > 0;
            boolean hasSkill = bot.loadout().skills().size > 0;
            assertTrue(hasWeapon || hasSkill, "a bot must be able to act at Elo " + elo);
        }
    }

    @Test
    void everyArchetypeYieldsABattleReadyParticipant() {
        // Each archetype in turn, rather than trusting one random draw to cover them.
        for (BotFactory.Archetype archetype : BotFactory.Archetype.values()) {
            Stats stats = archetype.distribute(BotFactory.BASE_STAT_BUDGET);
            assertTrue(totalStats(stats) > 0, archetype + " distributed no stat points");

            assertTrue(archetype.loadout().weapons().size + archetype.loadout().skills().size > 0,
                archetype + " can neither swing nor cast");
            archetype.loadout().skills().forEach(skill ->
                assertEquals(SkillType.ACTIVE, skill.type(), "only ACTIVE skills belong in a pouch (§4.4)"));
        }

        // And the whole thing survives the real battle-setup path, not just record construction.
        Character bot = new BotFactory(new Random(7L)).createBot(1200);
        BattleParticipant participant = BattleParticipant.fromCharacter(new Engine(), bot);
        assertTrue(participant.health().currentHealth > 0);
        assertNotNull(participant.battleState());
    }

    @Test
    void statBudgetGrowsWithEloAndIsClampedAtBothEnds() {
        assertTrue(BotFactory.statBudget(1500) > BotFactory.statBudget(1000),
            "a higher-rated attacker faces a bigger bot");
        assertTrue(BotFactory.statBudget(1000) > BotFactory.statBudget(500),
            "a lower-rated attacker faces a smaller one");

        assertEquals(BotFactory.MIN_STAT_BUDGET, BotFactory.statBudget(-10_000), "clamped at the bottom");
        assertEquals(BotFactory.MAX_STAT_BUDGET, BotFactory.statBudget(100_000), "clamped at the top");
    }

    @Test
    void botStatsScaleUpWithElo() {
        // Same seed → same archetype, so the only variable is the Elo-derived budget.
        int lowElo = totalStats(new BotFactory(new Random(1L)).createBot(800).stats());
        int highElo = totalStats(new BotFactory(new Random(1L)).createBot(2000).stats());

        assertTrue(highElo > lowElo, "stat budget must move with Elo (" + lowElo + " → " + highElo + ")");
    }
}
