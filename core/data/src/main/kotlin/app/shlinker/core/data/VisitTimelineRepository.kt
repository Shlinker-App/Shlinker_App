package app.shlinker.core.data

import app.shlinker.core.model.VisitStatistics
import app.shlinker.core.model.VisitTimelineBucket
import app.shlinker.core.model.VisitWindow
import app.shlinker.core.model.timelineCutoffs
import app.shlinker.core.network.InvalidVisitStatisticsException
import app.shlinker.core.network.ShortUrlDetailsService
import app.shlinker.core.network.VisitStatisticsService
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

class TimelineStartUnavailableException : IOException("The shlink creation date is missing or invalid")

class VisitTimelineRepository @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val service: VisitStatisticsService,
    private val details: ShortUrlDetailsService,
) {
    suspend fun get(
        serverId: String,
        shortCode: String,
        domain: String?,
        window: VisitWindow,
        summary: VisitStatistics,
    ): List<VisitTimelineBucket> {
        val server = profiles.profiles.first().firstOrNull { it.id == serverId }
            ?: throw ServerNotFoundException(serverId)
        val key = when (val result = profiles.getApiKey(serverId)) {
            is ApiKeyResult.Available -> result.apiKey
            else -> throw ShortUrlApiKeyException()
        }
        val created = if (window.since == null) {
            val date = details.get(server.url, key, shortCode, domain).dateCreated
                ?: throw TimelineStartUnavailableException()
            try {
                OffsetDateTime.parse(date).toInstant()
            } catch (_: DateTimeParseException) {
                throw TimelineStartUnavailableException()
            }.also { if (it > window.until) throw TimelineStartUnavailableException() }
        } else {
            null
        }
        val cutoffs = timelineCutoffs(window, created)
        var previous = VisitStatistics(0, 0)
        return cutoffs.mapIndexed { index, cutoff ->
            coroutineContext.ensureActive()
            // Cumulative differences avoid double-counting Shlink's inclusive, second-precision date boundaries.
            val cumulative = if (index == cutoffs.lastIndex) {
                summary
            } else {
                service.get(server.url, key, shortCode, domain, window.copy(until = cutoff))
            }
            if (cumulative.nonBots < previous.nonBots || cumulative.bots < previous.bots) {
                throw InvalidVisitStatisticsException()
            }
            val counts = VisitStatistics(cumulative.total - previous.total, cumulative.nonBots - previous.nonBots)
            previous = cumulative
            VisitTimelineBucket(
                since = if (index == 0) window.since else cutoffs[index - 1],
                until = cutoff,
                sinceExclusive = index > 0,
                counts = counts,
            )
        }
    }
}
