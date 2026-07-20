package io.github.ydhekim.crimson_sky.server.achievement;

import io.github.ydhekim.crimson_sky.common.model.Rarity;
import io.github.ydhekim.crimson_sky.server.database.entity.AchievementDefinitionEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure decision core (system design §22): one satisfied and one not-satisfied case per criteria type,
 * plus the two edge cases the branch logic hinges on — a never-won character can't satisfy
 * {@code FASTEST_WIN_TURNS}, and {@code ACCOUNT_CREATED_BEFORE} is inclusive of the cutoff day itself.
 * No database, no Ashley — the whole point of keeping {@link AchievementEvaluator} pure.
 */
class AchievementEvaluatorTest {

    private static AchievementDefinitionEntity def(AchievementScope scope, AchievementCriteriaType type,
                                                   Map<String, Object> params) {
        return new AchievementDefinitionEntity(1L, "KEY", scope, type, params,
            0, 0, null, null, 0, 0, 10, false, "TEST");
    }

    private static CharacterAchievementFacts characterFacts(int totalWins, int streak, Integer fastestWin,
                                                            int level, List<Rarity> granted) {
        return new CharacterAchievementFacts(totalWins, streak, fastestWin, level, granted);
    }

    // --- TOTAL_WINS ---------------------------------------------------------------------------------

    @Test
    void totalWinsSatisfiedAtOrAboveThreshold() {
        AchievementDefinitionEntity def = def(AchievementScope.CHARACTER, AchievementCriteriaType.TOTAL_WINS,
            Map.of("threshold", 10));
        assertTrue(AchievementEvaluator.isSatisfiedForCharacter(def, characterFacts(10, 0, null, 1, List.of())));
        assertFalse(AchievementEvaluator.isSatisfiedForCharacter(def, characterFacts(9, 0, null, 1, List.of())));
    }

    // --- WIN_STREAK ---------------------------------------------------------------------------------

    @Test
    void winStreakSatisfiedAtOrAboveThreshold() {
        AchievementDefinitionEntity def = def(AchievementScope.CHARACTER, AchievementCriteriaType.WIN_STREAK,
            Map.of("threshold", 3));
        assertTrue(AchievementEvaluator.isSatisfiedForCharacter(def, characterFacts(5, 3, null, 1, List.of())));
        assertFalse(AchievementEvaluator.isSatisfiedForCharacter(def, characterFacts(5, 2, null, 1, List.of())));
    }

    // --- FASTEST_WIN_TURNS --------------------------------------------------------------------------

    @Test
    void fastestWinTurnsSatisfiedAtOrBelowMax() {
        AchievementDefinitionEntity def = def(AchievementScope.CHARACTER, AchievementCriteriaType.FASTEST_WIN_TURNS,
            Map.of("maxTurns", 3));
        assertTrue(AchievementEvaluator.isSatisfiedForCharacter(def, characterFacts(1, 1, 3, 1, List.of())));
        assertFalse(AchievementEvaluator.isSatisfiedForCharacter(def, characterFacts(1, 1, 4, 1, List.of())));
    }

    @Test
    void fastestWinTurnsIsNeverSatisfiedWithoutAWin() {
        AchievementDefinitionEntity def = def(AchievementScope.CHARACTER, AchievementCriteriaType.FASTEST_WIN_TURNS,
            Map.of("maxTurns", 3));
        // null fastest-win time = the character has never won; a "3-turn win" it doesn't have can't satisfy it.
        assertFalse(AchievementEvaluator.isSatisfiedForCharacter(def, characterFacts(0, 0, null, 1, List.of())));
    }

    // --- CHARACTER_LEVEL ----------------------------------------------------------------------------

    @Test
    void characterLevelSatisfiedAtOrAboveThreshold() {
        AchievementDefinitionEntity def = def(AchievementScope.CHARACTER, AchievementCriteriaType.CHARACTER_LEVEL,
            Map.of("threshold", 10));
        assertTrue(AchievementEvaluator.isSatisfiedForCharacter(def, characterFacts(0, 0, null, 10, List.of())));
        assertFalse(AchievementEvaluator.isSatisfiedForCharacter(def, characterFacts(0, 0, null, 9, List.of())));
    }

    // --- ITEM_ACQUIRED ------------------------------------------------------------------------------

    @Test
    void itemAcquiredSatisfiedWhenAMatchingRarityWasJustGranted() {
        AchievementDefinitionEntity def = def(AchievementScope.CHARACTER, AchievementCriteriaType.ITEM_ACQUIRED,
            Map.of("rarity", "COMMON"));
        assertTrue(AchievementEvaluator.isSatisfiedForCharacter(def,
            characterFacts(0, 0, null, 1, List.of(Rarity.COMMON))));
        // A grant of a different rarity — and an empty grant — both leave it unsatisfied.
        assertFalse(AchievementEvaluator.isSatisfiedForCharacter(def,
            characterFacts(0, 0, null, 1, List.of(Rarity.RARE))));
        assertFalse(AchievementEvaluator.isSatisfiedForCharacter(def,
            characterFacts(0, 0, null, 1, List.of())));
    }

    // --- ACCOUNT_CREATED_BEFORE ---------------------------------------------------------------------

    @Test
    void accountCreatedBeforeSatisfiedWhenCreatedEarlier() {
        AchievementDefinitionEntity def = def(AchievementScope.ACCOUNT, AchievementCriteriaType.ACCOUNT_CREATED_BEFORE,
            Map.of("date", "2026-12-31"));
        Instant before = LocalDate.of(2026, 6, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant after = LocalDate.of(2027, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertTrue(AchievementEvaluator.isSatisfiedForAccount(def, new AccountAchievementFacts(before)));
        assertFalse(AchievementEvaluator.isSatisfiedForAccount(def, new AccountAchievementFacts(after)));
    }

    @Test
    void accountCreatedBeforeIsInclusiveOfTheCutoffDay() {
        AchievementDefinitionEntity def = def(AchievementScope.ACCOUNT, AchievementCriteriaType.ACCOUNT_CREATED_BEFORE,
            Map.of("date", "2026-12-31"));
        // Created on the cutoff date itself (any UTC time that day) still qualifies — the check is inclusive.
        Instant onCutoff = LocalDate.of(2026, 12, 31).atTime(23, 59).toInstant(ZoneOffset.UTC);
        assertTrue(AchievementEvaluator.isSatisfiedForAccount(def, new AccountAchievementFacts(onCutoff)));
    }

    // --- scope symmetry -----------------------------------------------------------------------------

    @Test
    void aMisScopedCriterionNeverSatisfiesRatherThanThrowing() {
        // ACCOUNT_CREATED_BEFORE asked of a character, and a character criterion asked of an account, both
        // return false — a mis-scoped definition simply never unlocks instead of crashing the pass.
        AchievementDefinitionEntity accountDef = def(AchievementScope.ACCOUNT,
            AchievementCriteriaType.ACCOUNT_CREATED_BEFORE, Map.of("date", "2026-12-31"));
        assertFalse(AchievementEvaluator.isSatisfiedForCharacter(accountDef,
            characterFacts(99, 99, 1, 99, List.of(Rarity.COMMON))));

        AchievementDefinitionEntity characterDef = def(AchievementScope.CHARACTER,
            AchievementCriteriaType.TOTAL_WINS, Map.of("threshold", 1));
        assertFalse(AchievementEvaluator.isSatisfiedForAccount(characterDef,
            new AccountAchievementFacts(Instant.EPOCH)));
    }
}
