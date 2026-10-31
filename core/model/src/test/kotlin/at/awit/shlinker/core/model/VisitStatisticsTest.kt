package at.awit.shlinker.core.model

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisitStatisticsTest {
    @Test fun periodsIncludeTodayAndUseCalendarDaysRatherThanMultiplesOf24Hours() {
        val now = Instant.parse("2026-10-31T12:34:56Z")
        val zone = ZoneId.of("Europe/Vienna")
        val expected = mapOf(
            VisitPeriod.TODAY to "2026-10-30T23:00:00Z",
            VisitPeriod.LAST_7_DAYS to "2026-10-24T22:00:00Z",
            VisitPeriod.LAST_4_WEEKS to "2026-10-03T22:00:00Z",
            VisitPeriod.LAST_90_DAYS to "2026-08-02T22:00:00Z",
        )
        for ((period, start) in expected) {
            assertEquals(Instant.parse(start), period.window(now, zone).since)
            assertEquals(now, period.window(now, zone).until)
        }
        assertNull(VisitPeriod.ALL_TIME.window(now, zone).since)
    }

    @Test fun todayAcrossDstAndDifferentZonesUsesTheSelectedLocalMidnight() {
        val spring = Instant.parse("2026-03-29T22:00:00Z")
        val winter = Instant.parse("2026-10-25T23:00:00Z")
        val zone = ZoneId.of("Europe/Vienna")
        assertEquals(23, ChronoUnit.HOURS.between(VisitPeriod.LAST_7_DAYS.window(spring, zone).since, spring) % 24)
        assertEquals(1, ChronoUnit.HOURS.between(VisitPeriod.LAST_7_DAYS.window(winter, zone).since, winter) % 24)
        val now = Instant.parse("2026-11-01T01:30:00Z")
        assertEquals(
            Instant.parse("2026-10-31T07:00:00Z"),
            VisitPeriod.TODAY.window(now, ZoneId.of("America/Los_Angeles")).since,
        )
    }

    @Test fun largeCountsAndZeroAreDistinctFromMissingAndBotsAreDerivedExactly() {
        assertEquals(2L, VisitStatistics(Long.MAX_VALUE, Long.MAX_VALUE - 2).bots)
        assertEquals(0L, VisitStatistics(0, 0).bots)
        for ((total, nonBots) in listOf(-1L to 0L, 3L to -1L, 3L to 4L)) {
            try {
                VisitStatistics(total, nonBots)
                throw AssertionError("Expected invalid counts")
            } catch (_: IllegalArgumentException) {
                // Invalid counts must not appear as successful zero counts.
            }
        }
    }

    @Test fun liveEndTimeUsesServerCompatibleMillisecondPrecision() {
        assertEquals(
            Instant.parse("2026-10-31T12:34:56.123Z"),
            VisitPeriod.ALL_TIME.window(
                Instant.parse("2026-10-31T12:34:56.123456789Z"),
                ZoneId.of("UTC"),
            ).until,
        )
    }
}
