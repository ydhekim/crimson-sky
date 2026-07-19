package io.github.ydhekim.crimson_sky.server.quest;

/**
 * The three v1.0 quests, curated in code (system design §19) — the same treatment {@code BotFactory}'s
 * starter content and {@code SkillTreeCatalog}'s nodes get (Epic E makes all of it data-driven later, at
 * once, not one system at a time).
 *
 * <p>Each quest is a stable string {@code id} (the value stored in {@code quest_claims.quest_id}), a
 * player-facing {@code description}, its {@link QuestPeriodType}, and the number of wins that completes it.
 * The reward is <b>not</b> modelled here — it differs in kind per quest (a consumable, a chosen consumable,
 * gold) and is applied by {@code QuestService}, which owns the {@code ShopService}/{@code AccountDao} it
 * reaches into. Look these up through {@link QuestCatalog#find}.
 */
public enum QuestDefinition {
    DAILY_WIN_2("daily.win2", "Win 2 battles today", QuestPeriodType.DAILY, 2),
    WEEKLY_WIN_10("weekly.win10", "Win 10 battles this week", QuestPeriodType.WEEKLY, 10),
    REPEATABLE_WIN_1("repeatable.win1", "Win 1 battle", QuestPeriodType.REPEATABLE, 1);

    public final String id;
    public final String description;
    public final QuestPeriodType periodType;
    public final int targetWins;

    QuestDefinition(String id, String description, QuestPeriodType periodType, int targetWins) {
        this.id = id;
        this.description = description;
        this.periodType = periodType;
        this.targetWins = targetWins;
    }
}
