package io.github.ydhekim.crimson_sky.server.quest;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shared period boundaries (system design §19/§20). Driven through the package-private {@link Clock}
 * overloads so "today"/"this week" are fixed facts of the test, not of when it runs.
 */
class QuestPeriodsTest {

    @Test
    void startOfTodayIsUtcMidnightOfTheClocksDay() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-15T13:45:30Z"), ZoneOffset.UTC);
        assertEquals(Instant.parse("2026-07-15T00:00:00Z"), QuestPeriods.startOfToday(clock));
    }

    @Test
    void startOfTodayTakesTheUtcDayEvenLateInTheDay() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-15T23:59:59Z"), ZoneOffset.UTC);
        assertEquals(Instant.parse("2026-07-15T00:00:00Z"), QuestPeriods.startOfToday(clock));
    }

    @Test
    void startOfWeekIsTheSameMondayForEveryDayOfThatWeek() {
        // 2024-01-01 is a Monday; 2024-01-07 the Sunday that closes the same week. All seven land on the
        // one Monday-midnight instant.
        Instant expectedMonday = Instant.parse("2024-01-01T00:00:00Z");
        for (int dayOfMonth = 1; dayOfMonth <= 7; dayOfMonth++) {
            String day = String.format("2024-01-%02dT12:00:00Z", dayOfMonth);
            Clock clock = Clock.fixed(Instant.parse(day), ZoneOffset.UTC);
            assertEquals(expectedMonday, QuestPeriods.startOfWeek(clock),
                "every day Mon–Sun of a week resolves to that week's Monday (" + day + ")");
        }
    }

    @Test
    void startOfWeekRollsOverAtTheSundayToMondayBoundary() {
        // Sunday still belongs to the week that opened the previous Monday...
        Clock sunday = Clock.fixed(Instant.parse("2024-01-07T23:59:59Z"), ZoneOffset.UTC);
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), QuestPeriods.startOfWeek(sunday));

        // ...and the very next instant, Monday, opens a new week.
        Clock monday = Clock.fixed(Instant.parse("2024-01-08T00:00:00Z"), ZoneOffset.UTC);
        assertEquals(Instant.parse("2024-01-08T00:00:00Z"), QuestPeriods.startOfWeek(monday));
    }

    @Test
    void startOfMonthAndPreviousMonthLandOnTheFirstAtUtcMidnight() {
        // Mid-month, mid-day: both boundaries snap to the 1st at UTC midnight, this month and the last.
        Clock clock = Clock.fixed(Instant.parse("2026-07-15T13:45:30Z"), ZoneOffset.UTC);
        assertEquals(Instant.parse("2026-07-01T00:00:00Z"), QuestPeriods.startOfMonth(clock));
        assertEquals(Instant.parse("2026-06-01T00:00:00Z"), QuestPeriods.startOfPreviousMonth(clock));
    }

    @Test
    void startOfMonthTakesTheUtcMonthEvenOnTheLastInstantOfTheMonth() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-31T23:59:59Z"), ZoneOffset.UTC);
        assertEquals(Instant.parse("2026-07-01T00:00:00Z"), QuestPeriods.startOfMonth(clock));
        assertEquals(Instant.parse("2026-06-01T00:00:00Z"), QuestPeriods.startOfPreviousMonth(clock));
    }

    @Test
    void startOfPreviousMonthRollsBackAcrossTheYearBoundary() {
        // January's previous month is the prior December — the year has to decrement, not just the month.
        Clock january = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), QuestPeriods.startOfMonth(january));
        assertEquals(Instant.parse("2025-12-01T00:00:00Z"), QuestPeriods.startOfPreviousMonth(january));
    }
}
