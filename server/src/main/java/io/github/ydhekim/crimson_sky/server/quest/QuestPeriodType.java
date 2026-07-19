package io.github.ydhekim.crimson_sky.server.quest;

/**
 * Which period a quest resets on (system design §19). Governs both which {@link QuestPeriods} boundary its
 * win count is measured from and how its claim is guarded: {@code DAILY}/{@code WEEKLY} allow one claim per
 * shared period boundary (the {@code quest_claims} {@code UNIQUE} triple), {@code REPEATABLE} allows several
 * per day up to a cap (a {@code claimed_at}-windowed count).
 */
public enum QuestPeriodType {
    DAILY,
    WEEKLY,
    REPEATABLE
}
