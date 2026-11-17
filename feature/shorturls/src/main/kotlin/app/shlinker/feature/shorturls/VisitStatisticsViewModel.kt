package app.shlinker.feature.shorturls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shlinker.core.data.ServerNotFoundException
import app.shlinker.core.data.VisitStatisticsRepository
import app.shlinker.core.data.VisitTimelineRepository
import app.shlinker.core.model.VisitPeriod
import app.shlinker.core.model.VisitStatistics
import app.shlinker.core.model.VisitTimelineBucket
import app.shlinker.core.model.VisitWindow
import app.shlinker.core.model.window
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VisitStatisticsState(
    val period: VisitPeriod = VisitPeriod.LAST_4_WEEKS,
    val window: VisitWindow? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val loading: Boolean = true,
    val serverName: String? = null,
    val counts: VisitStatistics? = null,
    val error: Exception? = null,
    val timelineLoading: Boolean = false,
    val timeline: List<VisitTimelineBucket>? = null,
    val timelineError: Exception? = null,
)

@HiltViewModel
class VisitStatisticsViewModel @Inject constructor(
    private val repository: VisitStatisticsRepository,
    private val timelineRepository: VisitTimelineRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(VisitStatisticsState())
    val state = _state.asStateFlow()
    private var identity: Triple<String, String, String?>? = null
    private var job: Job? = null

    fun load(serverId: String, shortCode: String, domain: String?) {
        val requested = Triple(serverId, shortCode, domain)
        if (identity == requested) return
        if (identity?.first != serverId) {
            _state.value = _state.value.copy(serverName = null)
        }
        identity = requested
        request(_state.value.period)
    }

    fun select(period: VisitPeriod) {
        if (_state.value.period == period) return
        request(period)
    }

    fun refresh() {
        if (_state.value.loading || _state.value.timelineLoading) return
        request(_state.value.period)
    }

    private fun request(period: VisitPeriod) {
        val requested = identity ?: return
        job?.cancel()
        val zone = ZoneId.systemDefault()
        val window = period.window(Instant.now(), zone)
        _state.value = VisitStatisticsState(
            period = period,
            zone = zone,
            window = window,
            serverName = _state.value.serverName,
        )
        job = viewModelScope.launch {
            try {
                val result = repository.get(requested.first, requested.second, requested.third, window)
                _state.value =
                    _state.value.copy(
                        loading = false,
                        serverName = result.serverName,
                        counts = result.counts,
                        timelineLoading = result.counts.total > 0,
                        timeline = if (result.counts.total == 0L) emptyList() else null,
                    )
                if (result.counts.total > 0) {
                    try {
                        val timeline = timelineRepository.get(
                            requested.first,
                            requested.second,
                            requested.third,
                            window,
                            result.counts,
                        )
                        _state.value = _state.value.copy(timelineLoading = false, timeline = timeline)
                    } catch (e: ServerNotFoundException) {
                        _state.value = _state.value.copy(timelineLoading = false, timelineError = e)
                    } catch (e: IOException) {
                        _state.value = _state.value.copy(timelineLoading = false, timelineError = e)
                    }
                }
            } catch (e: ServerNotFoundException) {
                _state.value = _state.value.copy(loading = false, error = e)
            } catch (e: IOException) {
                _state.value = _state.value.copy(loading = false, error = e)
            }
        }
    }
}
