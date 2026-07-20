package io.github.ydhekim.crimson_sky.server.achievement;

import io.github.ydhekim.crimson_sky.server.database.entity.AchievementDefinitionEntity;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * The pure decision core of the achievement system (system design §22) — same headless, Ashley-free,
 * database-free shape as {@code ActionResolver}: given a definition and a fact set, is the criterion
 * satisfied? All the DB reads that gather the facts, and all the writes an unlock triggers, live in
 * {@link io.github.ydhekim.crimson_sky.server.service.AchievementUnlockService}; nothing here touches
 * either, so every branch is unit-testable on plain records.
 *
 * <p>Scope is enforced by symmetry: a character criterion asked of an account, or vice versa, returns
 * {@code false} rather than throwing, so a mis-scoped definition simply never unlocks instead of crashing
 * an evaluation pass.
 */
public final class AchievementEvaluator {

    private AchievementEvaluator() {
    }

    public static boolean isSatisfiedForCharacter(AchievementDefinitionEntity def, CharacterAchievementFacts facts) {
        return switch (def.criteriaType()) {
            case TOTAL_WINS -> facts.totalWins() >= intParam(def, "threshold");
            case WIN_STREAK -> facts.currentWinStreak() >= intParam(def, "threshold");
            case FASTEST_WIN_TURNS -> facts.fastestWinTurns() != null
                && facts.fastestWinTurns() <= intParam(def, "maxTurns");
            case CHARACTER_LEVEL -> facts.characterLevel() >= intParam(def, "threshold");
            case ITEM_ACQUIRED -> {
                String rarity = (String) def.criteriaParams().get("rarity");
                yield rarity != null && facts.justGrantedItemRarities().stream()
                    .anyMatch(r -> r.name().equals(rarity));
            }
            case ACCOUNT_CREATED_BEFORE -> false; // account-scoped only, never asked of a character
        };
    }

    public static boolean isSatisfiedForAccount(AchievementDefinitionEntity def, AccountAchievementFacts facts) {
        if (def.criteriaType() != AchievementCriteriaType.ACCOUNT_CREATED_BEFORE) {
            return false; // character-scoped criteria, never asked of an account
        }
        LocalDate cutoff = LocalDate.parse((String) def.criteriaParams().get("date"));
        LocalDate created = facts.accountCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return created.isBefore(cutoff) || created.isEqual(cutoff); // inclusive: created ON the cutoff qualifies
    }

    /**
     * Jackson deserializes JSON numbers as Integer/Long/Double depending on shape, so a param is read as a
     * {@link Number} and narrowed — never cast straight to Integer, which would ClassCastException on a Long.
     */
    private static int intParam(AchievementDefinitionEntity def, String key) {
        return ((Number) def.criteriaParams().get(key)).intValue();
    }
}
