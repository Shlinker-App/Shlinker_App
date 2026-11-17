package app.shlinker.feature.shorturls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shlinker.core.data.ServerNotFoundException
import app.shlinker.core.data.ShortUrlApiKeyException
import app.shlinker.core.data.TimelineStartUnavailableException
import app.shlinker.core.model.VisitPeriod
import app.shlinker.core.network.ConnectionError
import app.shlinker.core.network.InvalidVisitStatisticsException
import app.shlinker.core.network.ShortUrlRequestException
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun VisitStatisticsRoute(
    serverId: String,
    shortCode: String,
    domain: String?,
    onBack: () -> Unit,
    viewModel: VisitStatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(serverId, shortCode, domain) { viewModel.load(serverId, shortCode, domain) }
    VisitStatisticsScreen(state, shortCode, domain, onBack, viewModel::select, viewModel::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VisitStatisticsScreen(
    state: VisitStatisticsState,
    shortCode: String,
    domain: String?,
    onBack: () -> Unit,
    onPeriod: (VisitPeriod) -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_statistics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.stats_back))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.loading && !state.timelineLoading) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.short_urls_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(
                    R.string.short_urls_server,
                    state.serverName ?: stringResource(
                        if (state.loading) R.string.stats_server_loading else R.string.stats_server_unavailable,
                    ),
                ),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(R.string.stats_code, shortCode))
            Text(stringResource(R.string.stats_domain, domain ?: stringResource(R.string.details_default_domain)))
            Text(stringResource(R.string.stats_period), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisitPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.period == period,
                        onClick = { onPeriod(period) },
                        label = { Text(stringResource(period.label())) },
                    )
                }
            }
            state.window?.let { window ->
                val format = DateTimeFormatter.ofPattern("yyyy / MM / dd HH:mm:ss z", Locale.ENGLISH)
                    .withZone(state.zone)
                Text(
                    stringResource(
                        R.string.stats_window,
                        window.since?.let(format::format) ?: stringResource(R.string.stats_beginning),
                        format.format(window.until),
                        state.zone.id,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.loading) CircularProgressIndicator()
            state.error?.let {
                Text(statisticsError(it), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.short_urls_retry)) }
            }
            state.counts?.let { counts ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CountField(R.string.details_total_visits, counts.total)
                        CountField(R.string.details_non_bot_visits, counts.nonBots)
                        CountField(R.string.stats_bots, counts.bots)
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(R.string.stats_timeline_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (state.timelineLoading) {
                                Text(stringResource(R.string.stats_timeline_loading))
                                CircularProgressIndicator()
                            }
                            state.timelineError?.let {
                                Text(statisticsError(it), color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = onRefresh) { Text(stringResource(R.string.short_urls_retry)) }
                            }
                            state.timeline?.let { buckets ->
                                if (buckets.isEmpty()) {
                                    Text(stringResource(R.string.stats_timeline_empty))
                                } else {
                                    VisitTimelineChart(buckets, state.zone)
                                }
                            }
                            Text(
                                stringResource(R.string.stats_timeline_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Text(stringResource(R.string.stats_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CountField(label: Int, value: Long) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}

private fun VisitPeriod.label(): Int = when (this) {
    VisitPeriod.TODAY -> R.string.stats_today
    VisitPeriod.LAST_7_DAYS -> R.string.stats_7
    VisitPeriod.LAST_4_WEEKS -> R.string.stats_4_weeks
    VisitPeriod.LAST_90_DAYS -> R.string.stats_90
    VisitPeriod.ALL_TIME -> R.string.stats_all
}

@Composable
private fun statisticsError(error: Exception): String = when {
    error is ServerNotFoundException -> stringResource(R.string.create_error_server_missing)
    error is ShortUrlApiKeyException -> stringResource(R.string.short_urls_key_missing)
    error is InvalidVisitStatisticsException -> stringResource(R.string.stats_invalid)
    error is TimelineStartUnavailableException -> stringResource(R.string.stats_timeline_start_error)
    error is ShortUrlRequestException -> {
        val reason = error.error
        if (reason is ConnectionError.HttpError && reason.status == 404) {
            stringResource(R.string.details_missing)
        } else if (reason is ConnectionError.Unknown) {
            stringResource(R.string.stats_load_error)
        } else {
            errorMessage(error)
        }
    }
    else -> stringResource(R.string.stats_load_error)
}
