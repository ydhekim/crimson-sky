package io.github.ydhekim.crimson_sky.server.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The quest catalog lookup (system design §19), matching {@code SkillTreeCatalog.find}'s null-on-miss
 * convention exactly.
 */
class QuestCatalogTest {

    @Test
    void resolvesEachKnownQuestId() {
        assertEquals(QuestDefinition.DAILY_WIN_2, QuestCatalog.find("daily.win2"));
        assertEquals(QuestDefinition.WEEKLY_WIN_10, QuestCatalog.find("weekly.win10"));
        assertEquals(QuestDefinition.REPEATABLE_WIN_1, QuestCatalog.find("repeatable.win1"));
    }

    @Test
    void returnsNullForAnUnknownQuestId() {
        assertNull(QuestCatalog.find("no.such.quest"));
    }

    @Test
    void returnsNullForANullId() {
        assertNull(QuestCatalog.find(null));
    }
}
