package io.github.ydhekim.crimson_sky.server.quest;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

/**
 * The shared period-boundary math for the quest system (system design §19/§20). A quest's live progress is
 * "wins since the start of the current period", so every quest reduces to one of these boundaries passed to
 * {@code BattleHistoryDao.countWins}. Epic Q's daily battle cap imports {@link #startOfToday()} for the same
 * reason — hence its own package, distinct from {@code server.content} (skill-tree specific).
 *
 * <p>The package-private {@link Clock}-taking overloads are the test seam — a real {@link #startOfWeek()}
 * call is untestable without one, since "today" moves — the same seeded/stubbed-dependency convention
 * {@code RewardService}'s {@code Random} and {@code BotFactory}'s {@code Random} already use.
 */
public final class QuestPeriods {

    private QuestPeriods() {
    }

    /** UTC midnight of today (system design §19/§20) — the daily quest and, later, the daily battle cap. */
    public static Instant startOfToday() {
        return startOfToday(Clock.systemUTC());
    }

    /** UTC midnight of the most recent Monday (system design §19) — the weekly quest boundary. */
    public static Instant startOfWeek() {
        return startOfWeek(Clock.systemUTC());
    }

    static Instant startOfToday(Clock clock) {
        return LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    static Instant startOfWeek(Clock clock) {
        LocalDate today = LocalDate.now(clock);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
