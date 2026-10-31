package at.awit.shlinker.core.model

import java.time.Instant
import java.time.temporal.ChronoUnit

data class VisitTimelineBucket(
    val since: Instant?,
    val until: Instant,
    val sinceExclusive: Boolean,
    val counts: VisitStatistics,
)

fun timelineCutoffs(window: VisitWindow, created: Instant? = null): List<Instant> {
    val start = (window.since ?: requireNotNull(created)).truncatedTo(ChronoUnit.SECONDS)
    val end = window.until.truncatedTo(ChronoUnit.SECONDS)
    require(start <= end)
    val seconds = ChronoUnit.SECONDS.between(start, end) + 1
    val count = minOf(12L, seconds).toInt()
    return (1..count).map { index ->
        start.plusSeconds(seconds / count * index + seconds % count * index / count - 1)
    }
}
