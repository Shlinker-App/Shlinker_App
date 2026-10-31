package at.awit.shlinker.feature.shorturls

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import at.awit.shlinker.core.network.UpdateShortUrlException
import at.awit.shlinker.core.network.UpdateShortUrlFailure

@Composable
fun ShortUrlDetailsRoute(
    serverId: String,
    shortCode: String,
    domain: String?,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    viewModel: ShortUrlDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(serverId, shortCode, domain) { viewModel.load(serverId, shortCode, domain) }
    LaunchedEffect(state.changeRevision) { if (state.changeRevision > 0) onChanged() }
    ShortUrlDetailsScreen(
        state,
        onBack,
        { viewModel.load(serverId, shortCode, domain, refresh = true) },
        viewModel::edit,
        viewModel::change,
        viewModel::save,
        viewModel::cancelEdit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShortUrlDetailsScreen(
    state: ShortUrlDetailsState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onChange: (ShortUrlEditForm) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    var discard by remember { mutableStateOf(false) }
    val editing = state.form != null
    val back = {
        if (!state.saving) {
            if (editing && !state.needsReload) discard = true else onBack()
        }
    }
    BackHandler(enabled = editing || state.saving) { back() }
    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(stringResource(R.string.edit_discard_title)) },
            text = { Text(stringResource(R.string.edit_discard_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    discard = false
                    onCancelEdit()
                }) {
                    Text(stringResource(R.string.edit_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { discard = false }) { Text(stringResource(R.string.edit_keep)) }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing) R.string.edit_title else R.string.details_title)) },
                navigationIcon = {
                    IconButton(onClick = back, enabled = !state.saving) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(if (editing) R.string.edit_cancel else R.string.create_cancel),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.loading && !editing) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.short_urls_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.serverName?.let {
                Text(stringResource(R.string.short_urls_server, it), style = MaterialTheme.typography.titleMedium)
            }
            if (state.loading) CircularProgressIndicator()
            if (!editing) {
                state.error?.let { DetailsError(it, onRefresh) }
            }
            if (state.saved) Text(stringResource(R.string.edit_saved))
            val form = state.form
            if (form != null) {
                Text(state.details?.url?.shortUrl.orEmpty(), style = MaterialTheme.typography.titleMedium)
                ShortUrlEditFields(
                    form,
                    !state.saving && !state.needsReload,
                    state.validation,
                    onChange,
                )
                state.validation?.let {
                    Text(
                        stringResource(
                            when (it) {
                                EditValidation.URL -> R.string.create_error_url
                                EditValidation.DATES -> R.string.edit_error_dates
                                EditValidation.LIMIT -> R.string.edit_error_limit
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.error?.let {
                    DetailsError(it, onRefresh.takeIf { state.needsReload }, reload = state.needsReload)
                }
                if (state.saving) CircularProgressIndicator()
                Button(onClick = onSave, enabled = !state.saving && !state.needsReload) {
                    Text(stringResource(R.string.edit_save))
                }
                TextButton(onClick = { discard = true }, enabled = !state.saving && !state.needsReload) {
                    Text(stringResource(R.string.edit_cancel))
                }
            } else {
                state.details?.let { details ->
                    Button(onClick = onEdit) { Text(stringResource(R.string.edit_action)) }
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
                }
            }
        }
    }
}

@Composable
private fun DetailsError(error: Exception, onRetry: (() -> Unit)?, reload: Boolean = false) {
    Text(detailsErrorMessage(error), color = MaterialTheme.colorScheme.error)
    if (onRetry != null) {
        TextButton(onClick = onRetry) {
            Text(stringResource(if (reload) R.string.edit_reload else R.string.short_urls_retry))
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
    error is UpdateShortUrlException -> stringResource(
        when (error.failure) {
            UpdateShortUrlFailure.INVALID_INPUT -> R.string.edit_error_invalid
            UpdateShortUrlFailure.UNAUTHORIZED -> R.string.short_urls_key_invalid
            UpdateShortUrlFailure.NOT_FOUND -> R.string.details_missing
            UpdateShortUrlFailure.SERVER, UpdateShortUrlFailure.UNCERTAIN -> R.string.edit_error_uncertain
        },
    )
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
