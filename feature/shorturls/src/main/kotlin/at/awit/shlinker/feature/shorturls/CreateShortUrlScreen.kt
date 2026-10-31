package at.awit.shlinker.feature.shorturls

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun CreateShortUrlRoute(
    serverId: String,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateShortUrlViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(serverId) { viewModel.load(serverId) }
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
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.create_cancel))
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
            state.serverName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            if (state.loading) {
                CircularProgressIndicator()
            } else if (state.created != null) {
                Text(stringResource(R.string.create_success), style = MaterialTheme.typography.titleLarge)
                Text(state.created.shortUrl)
                Button(onClick = { onCopy(state.created.shortUrl) }) { Text(stringResource(R.string.create_copy)) }
                TextButton(onClick = onBack) { Text(stringResource(R.string.create_done)) }
            } else {
                Text(stringResource(R.string.create_intro))
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
                OutlinedTextField(
                    state.domain, { onChange(state.copy(domain = it)) },
                    label = { Text(stringResource(R.string.create_domain)) },
                    supportingText = { Text(stringResource(R.string.create_domain_hint)) },
                    enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    isError = state.error == CreateError.DOMAIN,
                )
                OutlinedTextField(
                    state.tags,
                    { onChange(state.copy(tags = it)) },
                    label = { Text(stringResource(R.string.create_tags)) },
                    supportingText = { Text(stringResource(R.string.create_tags_hint)) },
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let { error ->
                    Text(
                        stringResource(
                            when (error) {
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
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.serverName == null && state.error == CreateError.LOAD) {
                    TextButton(onClick = onRetryLoad) { Text(stringResource(R.string.short_urls_retry)) }
                }
                if (state.busy) CircularProgressIndicator()
                Button(
                    onClick = onSubmit,
                    enabled = enabled && state.longUrl.isNotBlank() && state.error != CreateError.UNCERTAIN,
                ) {
                    Text(stringResource(R.string.create_submit))
                }
                TextButton(onClick = onBack, enabled = !state.busy) {
                    Text(stringResource(R.string.create_cancel))
                }
            }
        }
    }
}
