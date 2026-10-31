package at.awit.shlinker.core.model

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitTimelineTest {
    @Test fun allPresetsHaveAtMost12StrictlyIncreasingCutsAndIncludeExactFinalSecond() {
        val now = Instant.parse("2026-10-31T12:34:56.789Z")
        for (period in VisitPeriod.entries) {
            val window = period.window(now, ZoneId.of("Europe/Vienna"))
            val cuts = timelineCutoffs(window, Instant.parse("2020-01-01T00:00:00Z"))
            assertEquals(12, cuts.size)
            assertEquals(Instant.parse("2026-10-31T12:34:56Z"), cuts.last())
            assertTrue(cuts.zipWithNext().all { (first, second) -> first < second })
            assertTrue(cuts.all { it.nano == 0 })
        }
    }

    @Test fun veryShortWindowsNeverRepeatACutOrExtendBeyondTheRange() {
        val start = Instant.parse("2026-10-31T00:00:00Z")
        assertEquals(listOf(start), timelineCutoffs(VisitWindow(start, start.plusMillis(500))))
        assertEquals(
            (0L..4L).map { start.plusSeconds(it) },
            timelineCutoffs(VisitWindow(start, start.plusSeconds(4))),
        )
        val cuts = timelineCutoffs(VisitWindow(start, start.plusSeconds(100)))
        val lengths = listOf(cuts.first().epochSecond - start.epochSecond + 1) +
            cuts.zipWithNext().map { (first, second) -> second.epochSecond - first.epochSecond }
        assertEquals(101L, lengths.sum())
        assertTrue(lengths.max() - lengths.min() <= 1)
    }

    @Test fun allTimeNeedsAKnownStartAndInvalidFutureStartIsRejected() {
        val now = Instant.parse("2026-10-31T00:00:00Z")
        for (start in listOf(null, now.plusSeconds(1))) {
            try {
                timelineCutoffs(VisitWindow(null, now), start)
                throw AssertionError("Expected missing/invalid start")
            } catch (_: IllegalArgumentException) {
                assertTrue(true)
            }
        }
    }
}
