package app.shlinker.feature.shorturls

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shlinker.core.model.parseTagInput
import kotlinx.coroutines.launch

@Composable
fun CreateShortUrlRoute(
    serverId: String,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateShortUrlViewModel = hiltViewModel(),
    tagsViewModel: TagSuggestionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tags by tagsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(serverId) { viewModel.load(serverId) }
    LaunchedEffect(serverId) { tagsViewModel.load(serverId) }
    val needsRefresh =
        state.created != null || state.error == CreateError.UNCERTAIN || state.error == CreateError.SERVER
    BackHandler(enabled = state.busy || needsRefresh) {
        if (!state.busy) onCreated()
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val copied = stringResource(R.string.short_urls_copied)
    CreateShortUrlScreen(
        state,
        viewModel::change,
        viewModel::submit,
        { viewModel.load(serverId) },
        { if (needsRefresh) onCreated() else onBack() },
        {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Shlink", it)))
                snackbar.showSnackbar(copied)
            }
        },
        snackbar,
        tags,
        { tagsViewModel.load(serverId, retry = true) },
        viewModel::loadDomains,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateShortUrlScreen(
    state: CreateShortUrlState,
    onChange: (CreateShortUrlState) -> Unit,
    onSubmit: () -> Unit,
    onRetryLoad: () -> Unit,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    tagSuggestions: TagSuggestionsState,
    onRetryTags: () -> Unit,
    onRetryDomains: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !state.busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Text(stringResource(R.string.create_cancel))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.create_title), style = MaterialTheme.typography.headlineMedium)
            state.serverName?.let {
                Text(stringResource(R.string.short_urls_server, it), style = MaterialTheme.typography.titleMedium)
            }
            if (state.loading) {
                CircularProgressIndicator()
            } else if (state.created != null) {
                Text(stringResource(R.string.create_success), style = MaterialTheme.typography.titleLarge)
                Text(state.created.shortUrl)
                Button(onClick = { onCopy(state.created.shortUrl) }) { Text(stringResource(R.string.create_copy)) }
                TextButton(onClick = onBack) { Text(stringResource(R.string.create_done)) }
            } else {
                Text(stringResource(R.string.create_intro))
                Text(
                    stringResource(R.string.create_details_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.create_behavior_defaults,
                        stringResource(if (state.defaults.forwardQuery) R.string.details_yes else R.string.details_no),
                        stringResource(
                            when (state.defaults.crawlable) {
                                null -> R.string.create_server_default
                                true -> R.string.details_yes
                                false -> R.string.details_no
                            },
                        ),
                    ),
                )
                val enabled = !state.busy && state.serverName != null
                OutlinedTextField(
                    state.longUrl,
                    { onChange(state.copy(longUrl = it)) },
                    label = { Text(stringResource(R.string.create_url)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
                    enabled = enabled,
                    singleLine = true,
                    isError = state.error == CreateError.URL,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    state.title,
                    { onChange(state.copy(title = it)) },
                    label = { Text(stringResource(R.string.create_label_title)) },
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    state.slug,
                    { onChange(state.copy(slug = it)) },
                    label = { Text(stringResource(R.string.create_slug)) },
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                CreateDomainField(state, enabled, onChange, onRetryDomains)
                TagInput(state.tags, state.tagInput, enabled) { tags, input ->
                    onChange(state.copy(tags = tags, tagInput = input))
                }
                TagSuggestions(tagSuggestions, state.tags, state.tagInput, enabled, onRetryTags) {
                    onChange(state.copy(tags = (state.tags + it).distinct(), tagInput = ""))
                }
                if (state.serverName != null && state.appTag.enabled) {
                    Text(
                        stringResource(R.string.create_app_tag, state.appTag.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.error?.let { error ->
                    Text(
                        stringResource(createErrorMessage(error)),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.serverName == null && state.error == CreateError.LOAD) {
                    TextButton(onClick = onRetryLoad) { Text(stringResource(R.string.short_urls_retry)) }
                }
                if (state.busy) CircularProgressIndicator()
                Button(
                    onClick = onSubmit,
                    enabled = enabled &&
                        state.domainReady &&
                        state.longUrl.isNotBlank() &&
                        state.error != CreateError.UNCERTAIN,
                ) {
                    Text(stringResource(R.string.create_submit))
                }
            }
        }
    }
}

internal fun createErrorMessage(error: CreateError): Int = when (error) {
    CreateError.URL -> R.string.create_error_url
    CreateError.DOMAIN -> R.string.create_error_domain
    CreateError.LOAD -> R.string.create_error_load
    CreateError.MISSING_SERVER -> R.string.create_error_server_missing
    CreateError.MISSING_KEY -> R.string.short_urls_key_missing
    CreateError.INVALID -> R.string.create_error_invalid
    CreateError.CONFLICT -> R.string.create_error_conflict
    CreateError.AUTH -> R.string.short_urls_key_invalid
    CreateError.SERVER -> R.string.create_error_server
    CreateError.UNCERTAIN -> R.string.create_error_uncertain
}

@Composable
internal fun TagInput(
    tags: List<String>,
    tagInput: String,
    enabled: Boolean,
    onChange: (List<String>, String) -> Unit,
) {
    fun commit(input: String) {
        onChange((tags + parseTagInput(input)).distinct(), "")
    }
    Column {
        OutlinedTextField(
            tagInput,
            { input ->
                if (input.any { it == ',' || it == '\n' || it == '\r' }) {
                    commit(input)
                } else {
                    onChange(tags, input)
                }
            },
            label = { Text(stringResource(R.string.create_tags)) },
            supportingText = { Text(stringResource(R.string.create_tags_hint)) },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { commit(tagInput) }),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                val remove = stringResource(R.string.create_tag_remove, tag)
                InputChip(
                    selected = false,
                    onClick = { onChange(tags - tag, tagInput) },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = remove },
                )
            }
        }
    }
}
