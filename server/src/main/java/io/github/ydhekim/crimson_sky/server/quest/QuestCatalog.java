package io.github.ydhekim.crimson_sky.server.quest;

/**
 * The v1.0 quest catalog (system design §19) — the lookup surface over {@link QuestDefinition}, mirroring
 * {@code SkillTreeCatalog.find}. Content is code, not data (deferred to Epic E1/M5 with everything else).
 */
public final class QuestCatalog {

    private QuestCatalog() {
    }

    /** The quest with {@code questId}, or {@code null} if no such quest exists in the v1.0 catalog. */
    public static QuestDefinition find(String questId) {
        if (questId == null) {
            return null;
        }
        for (QuestDefinition quest : QuestDefinition.values()) {
            if (quest.id.equals(questId)) {
                return quest;
            }
        }
        return null;
    }
}
