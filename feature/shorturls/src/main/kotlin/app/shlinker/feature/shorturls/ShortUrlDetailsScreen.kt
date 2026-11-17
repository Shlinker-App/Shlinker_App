package app.shlinker.feature.shorturls

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCode
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shlinker.core.data.ServerNotFoundException
import app.shlinker.core.data.ShortUrlApiKeyException
import app.shlinker.core.network.ConnectionError
import app.shlinker.core.network.DeleteShortUrlException
import app.shlinker.core.network.DeleteShortUrlFailure
import app.shlinker.core.network.InvalidShortUrlResponseException
import app.shlinker.core.network.ShortUrlRequestException
import app.shlinker.core.network.UpdateShortUrlException
import app.shlinker.core.network.UpdateShortUrlFailure

@Composable
fun ShortUrlDetailsRoute(
    serverId: String,
    shortCode: String,
    domain: String?,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onStatistics: () -> Unit,
    onRules: () -> Unit,
    rulesRevision: Int = 0,
    viewModel: ShortUrlDetailsViewModel = hiltViewModel(),
    tagsViewModel: TagSuggestionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tags by tagsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(serverId, shortCode, domain, rulesRevision) {
        viewModel.load(serverId, shortCode, domain, refresh = rulesRevision > 0)
    }
    LaunchedEffect(state.form != null, serverId) { if (state.form != null) tagsViewModel.load(serverId) }
    LaunchedEffect(state.changeRevision) {
        if (state.changeRevision > 0) onChanged()
        if (state.deleted) onBack()
    }
    ShortUrlDetailsScreen(
        state,
        onBack,
        { viewModel.load(serverId, shortCode, domain, refresh = true) },
        viewModel::edit,
        viewModel::change,
        viewModel::save,
        viewModel::cancelEdit,
        viewModel::requestDelete,
        viewModel::cancelDelete,
        viewModel::delete,
        tags,
        { tagsViewModel.load(serverId, retry = true) },
        onStatistics,
        onRules,
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
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onDelete: () -> Unit,
    tagSuggestions: TagSuggestionsState,
    onRetryTags: () -> Unit,
    onStatistics: () -> Unit,
    onRules: () -> Unit,
) {
    var discard by remember { mutableStateOf(false) }
    var qrUrl by rememberSaveable { mutableStateOf<String?>(null) }
    qrUrl?.let { ShlinkQrDialog(it) { qrUrl = null } }
    val editing = state.form != null
    val cancelEdit = {
        if (state.details?.let { state.form?.hasChanges(it) } == true) discard = true else onCancelEdit()
    }
    val back = {
        if (!state.saving) {
            if (editing && !state.needsReload) cancelEdit() else onBack()
        }
    }
    BackHandler(enabled = editing || state.saving) { back() }
    if (state.confirmingDelete) {
        val maxDialogTextHeight = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height.toDp() * 0.6f
        }
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(stringResource(R.string.delete_title)) },
            text = {
                Column(
                    Modifier.heightIn(max = maxDialogTextHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.delete_warning))
                    Text(stringResource(R.string.short_urls_server, state.serverName.orEmpty()))
                    Text(state.details?.url?.shortUrl.orEmpty())
                }
            },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text(stringResource(R.string.edit_picker_cancel)) }
            },
        )
    }
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
                    IconButton(onClick = onRefresh, enabled = !state.loading && !editing && !state.saving) {
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
                state.error?.let { DetailsError(it, onRefresh, reload = state.needsReload) }
                if (state.saving) CircularProgressIndicator()
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
                    tagSuggestions,
                    onRetryTags,
                )
                state.validation?.let {
                    Text(
                        stringResource(
                            when (it) {
                                EditValidation.URL -> R.string.create_error_url
                                EditValidation.SINCE, EditValidation.UNTIL -> R.string.edit_error_dates
                                EditValidation.SINCE_RANGE, EditValidation.UNTIL_RANGE -> R.string.edit_error_date_range
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
                TextButton(onClick = cancelEdit, enabled = !state.saving && !state.needsReload) {
                    Text(stringResource(R.string.edit_cancel))
                }
            } else {
                state.details?.let { details ->
                    DetailsSection(stringResource(R.string.details_actions)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onEdit, enabled = !state.saving && !state.needsReload) {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                                Text(stringResource(R.string.edit_action))
                            }
                            TextButton(
                                onClick = { qrUrl = details.url.shortUrl },
                                enabled = !state.saving && !state.needsReload,
                            ) {
                                Icon(Icons.Outlined.QrCode, contentDescription = null)
                                Text(stringResource(R.string.qr_show))
                            }
                            TextButton(onClick = onStatistics, enabled = !state.saving && !state.needsReload) {
                                Icon(Icons.Outlined.BarChart, contentDescription = null)
                                Text(stringResource(R.string.details_statistics))
                            }
                            TextButton(onClick = onRules, enabled = !state.saving && !state.needsReload) {
                                Icon(Icons.AutoMirrored.Outlined.AltRoute, contentDescription = null)
                                Text(stringResource(R.string.details_rules_action))
                            }
                        }
                    }
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
                    TextButton(onClick = onRequestDelete, enabled = !state.saving && !state.needsReload) {
                        Text(stringResource(R.string.delete_action), color = MaterialTheme.colorScheme.error)
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
    error is DeleteShortUrlException -> stringResource(
        when (error.failure) {
            DeleteShortUrlFailure.UNAUTHORIZED -> R.string.short_urls_key_invalid
            DeleteShortUrlFailure.NOT_FOUND -> R.string.delete_missing
            DeleteShortUrlFailure.VISITS_THRESHOLD -> R.string.delete_threshold
            DeleteShortUrlFailure.UNCERTAIN -> R.string.delete_uncertain
        },
    )
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
