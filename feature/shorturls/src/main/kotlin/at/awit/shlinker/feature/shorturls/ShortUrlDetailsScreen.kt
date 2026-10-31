package at.awit.shlinker.feature.shorturls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.awit.shlinker.core.data.ServerNotFoundException
import at.awit.shlinker.core.data.ShortUrlApiKeyException
import at.awit.shlinker.core.network.ConnectionError
import at.awit.shlinker.core.network.InvalidShortUrlResponseException
import at.awit.shlinker.core.network.ShortUrlRequestException

@Composable
fun ShortUrlDetailsRoute(
    serverId: String,
    shortCode: String,
    domain: String?,
    onBack: () -> Unit,
    viewModel: ShortUrlDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(serverId, shortCode, domain) { viewModel.load(serverId, shortCode, domain) }
    ShortUrlDetailsScreen(state, onBack, { viewModel.load(serverId, shortCode, domain, refresh = true) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShortUrlDetailsScreen(state: ShortUrlDetailsState, onBack: () -> Unit, onRefresh: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.create_cancel))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.loading) {
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
            state.serverName?.let {
                Text(stringResource(R.string.short_urls_server, it), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.details_read_only), style = MaterialTheme.typography.bodyMedium)
            if (state.loading) CircularProgressIndicator()
            state.error?.let {
                Text(detailsErrorMessage(it), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.short_urls_retry)) }
            }
            state.details?.let { details ->
                val url = details.url
                val unavailable = stringResource(R.string.details_unavailable)
                val notSet = stringResource(R.string.details_not_set)
                DetailsSection(stringResource(R.string.details_general)) {
                    DetailField(R.string.details_label_title, url.title?.takeIf { it.isNotBlank() } ?: notSet)
                    DetailField(R.string.details_short_url, url.shortUrl)
                    DetailField(R.string.create_url, url.longUrl)
                    DetailField(R.string.details_short_code, url.shortCode)
                    DetailField(
                        R.string.details_domain,
                        url.domain ?: stringResource(R.string.details_default_domain),
                    )
                    DetailField(R.string.details_tags, url.tags.joinToString(", ").ifEmpty { notSet })
                    DetailField(R.string.details_created, details.dateCreated ?: unavailable)
                }
                DetailsSection(stringResource(R.string.details_visits)) {
                    Text(stringResource(R.string.details_visits_hint))
                    DetailField(R.string.details_total_visits, url.visits?.toString() ?: unavailable)
                    DetailField(R.string.details_non_bot_visits, details.nonBotVisits?.toString() ?: unavailable)
                    DetailField(R.string.details_bot_visits, details.botVisits?.toString() ?: unavailable)
                }
                DetailsSection(stringResource(R.string.details_limits)) {
                    DetailField(
                        R.string.details_valid_since,
                        if (!details.metaAvailable) unavailable else details.validSince ?: notSet,
                    )
                    DetailField(
                        R.string.details_valid_until,
                        if (!details.metaAvailable) unavailable else details.validUntil ?: notSet,
                    )
                    DetailField(
                        R.string.details_max_visits,
                        if (!details.metaAvailable) {
                            unavailable
                        } else {
                            details.maxVisits?.toString() ?: stringResource(R.string.details_unlimited)
                        },
                    )
                    Text(stringResource(R.string.details_dates_hint), style = MaterialTheme.typography.bodySmall)
                }
                DetailsSection(stringResource(R.string.details_behavior)) {
                    DetailField(R.string.details_crawlable, booleanLabel(details.crawlable))
                    DetailField(R.string.details_forward_query, booleanLabel(details.forwardQuery))
                    DetailField(R.string.details_redirect_rules, booleanLabel(details.hasRedirectRules))
                    Text(stringResource(R.string.details_redirect_hint), style = MaterialTheme.typography.bodySmall)
                }
                DetailsSection(stringResource(R.string.details_creation_options)) {
                    DetailField(
                        R.string.details_code_length,
                        url.shortCode.codePointCount(0, url.shortCode.length).toString(),
                    )
                    Text(stringResource(R.string.details_code_length_hint))
                    DetailField(R.string.details_reuse, stringResource(R.string.details_creation_only))
                    Text(stringResource(R.string.details_reuse_hint))
                }
            }
        }
    }
}

@Composable
private fun DetailsSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun DetailField(label: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun booleanLabel(value: Boolean?): String = stringResource(
    when (value) {
        true -> R.string.details_yes
        false -> R.string.details_no
        null -> R.string.details_unavailable
    },
)

@Composable
private fun detailsErrorMessage(error: Exception): String = when {
    error is ServerNotFoundException -> stringResource(R.string.create_error_server_missing)
    error is ShortUrlApiKeyException -> stringResource(R.string.short_urls_key_missing)
    error is InvalidShortUrlResponseException -> stringResource(R.string.details_invalid_response)
    error is ShortUrlRequestException -> {
        val reason = error.error
        if (reason is ConnectionError.HttpError && reason.status == 404) {
            stringResource(R.string.details_missing)
        } else if (reason is ConnectionError.Unknown) {
            stringResource(R.string.details_load_error)
        } else {
            errorMessage(error)
        }
    }
    else -> stringResource(R.string.details_load_error)
}
