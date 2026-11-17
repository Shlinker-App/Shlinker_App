package app.shlinker.core.model

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class VisitPeriod(val days: Long?) { TODAY(1), LAST_7_DAYS(7), LAST_4_WEEKS(28), LAST_90_DAYS(90), ALL_TIME(null) }

data class VisitWindow(val since: Instant?, val until: Instant) {
    init {
        require(since == null || since <= until)
    }
}

fun VisitPeriod.window(now: Instant, zone: ZoneId): VisitWindow = VisitWindow(
    since = days?.let { now.atZone(zone).toLocalDate().minusDays(it - 1).atStartOfDay(zone).toInstant() },
    until = now.truncatedTo(ChronoUnit.MILLIS),
)

data class VisitStatistics(val total: Long, val nonBots: Long) {
    init {
        require(total >= 0 && nonBots in 0..total)
    }
    val bots: Long get() = total - nonBots
}
