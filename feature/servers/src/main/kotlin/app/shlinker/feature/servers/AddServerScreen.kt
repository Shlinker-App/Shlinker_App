package app.shlinker.feature.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shlinker.core.model.ServerUrl
import app.shlinker.core.network.CertificateProblem
import app.shlinker.core.network.ConnectionError
import app.shlinker.core.ui.R as UiR
import app.shlinker.core.ui.theme.ShlinkerTheme
import kotlinx.coroutines.launch

@Composable
fun AddServerRoute(
    onSaved: (serverId: String) -> Unit,
    onBack: (() -> Unit)?,
    viewModel: AddServerViewModel = hiltViewModel(),
    serverId: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(serverId) { serverId?.let(viewModel::load) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AddServerEvent.Saved -> onSaved(event.serverId)
            }
        }
    }
    AddServerScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onUrlChange = viewModel::onUrlChange,
        onUrlFocusLost = viewModel::onUrlFocusLost,
        onApiKeyChange = viewModel::onApiKeyChange,
        onSubmit = viewModel::onSubmit,
        onCancel = viewModel::onCancelConnect,
        onBack = onBack,
        onRetryLoad = { serverId?.let(viewModel::load) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerScreen(
    state: AddServerUiState,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onUrlFocusLost: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onBack: (() -> Unit)?,
    onRetryLoad: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.editing) R.string.edit_server_title else R.string.add_server_title))
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.add_server_back))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.editing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.edit_server_banner_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(stringResource(R.string.edit_server_banner_body))
                        }
                    }
                }
            }
            Text(
                stringResource(if (state.editing) R.string.edit_server_intro else R.string.add_server_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.add_server_name)) },
                supportingText = { Text(stringResource(R.string.add_server_name_hint)) },
                singleLine = true,
                enabled = !state.isConnecting && !state.loading && !state.loadFailed,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            UrlField(state, onUrlChange, onUrlFocusLost)

            ApiKeyField(state, onApiKeyChange, onSubmit)

            AnimatedVisibility(visible = state.connectionError != null) {
                state.connectionError?.let { ErrorCard(it) }
            }
            state.saveError?.let { error ->
                Text(
                    stringResource(
                        when (error) {
                            ServerSaveError.DUPLICATE -> R.string.server_save_duplicate
                            ServerSaveError.NAME_CONFLICT -> R.string.server_save_name_conflict
                            ServerSaveError.CANNOT_CHECK_DUPLICATE -> R.string.server_save_cannot_check
                            ServerSaveError.STORAGE -> R.string.server_save_storage
                            ServerSaveError.NOT_FOUND -> R.string.server_save_not_found
                            ServerSaveError.KEY_UNAVAILABLE -> UiR.string.server_error_api_key_unavailable
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.loading) CircularProgressIndicator()
            if (state.loadFailed) {
                TextButton(onClick = onRetryLoad) { Text(stringResource(R.string.edit_server_retry)) }
            }

            SubmitRow(state, onSubmit, onCancel)

            // Keep this information visible rather than hiding it behind an info icon.
            HintCard(
                icon = Icons.Outlined.Lock,
                title = stringResource(UiR.string.server_hint_tls_title),
                body = stringResource(UiR.string.server_hint_tls_body),
            )

            HintCard(
                icon = Icons.Outlined.Key,
                title = stringResource(UiR.string.server_hint_api_key_title),
                body = stringResource(UiR.string.server_hint_api_key),
            )

            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun UrlField(state: AddServerUiState, onUrlChange: (String) -> Unit, onUrlFocusLost: () -> Unit) {
    var hadFocus by remember { mutableStateOf(false) }
    val error = state.urlError
    OutlinedTextField(
        value = state.url,
        onValueChange = onUrlChange,
        label = { Text(stringResource(R.string.add_server_url)) },
        placeholder = { Text("s.example.org") },
        isError = error != null,
        supportingText = {
            Text(
                when {
                    error != null -> urlErrorMessage(error)
                    state.normalizedUrlPreview != null ->
                        stringResource(R.string.add_server_url_preview, state.normalizedUrlPreview)
                    else -> stringResource(UiR.string.server_hint_url)
                },
            )
        },
        singleLine = true,
        enabled = !state.isConnecting && !state.loading && !state.loadFailed,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            autoCorrectEnabled = false,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (hadFocus && !it.isFocused) onUrlFocusLost()
                hadFocus = it.isFocused
            },
    )
}

@Composable
internal fun ApiKeyField(state: AddServerUiState, onApiKeyChange: (String) -> Unit, onSubmit: () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    var pasteError by remember { mutableStateOf<Int?>(null) }
    var pasting by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = {
            pasteError = null
            onApiKeyChange(it)
        },
        label = { Text(stringResource(R.string.add_server_api_key)) },
        isError = state.apiKeyMissing || pasteError != null,
        supportingText = if (pasteError != null || state.apiKeyMissing) {
            { Text(stringResource(pasteError ?: UiR.string.server_error_api_key_empty)) }
        } else if (state.editing) {
            { Text(stringResource(R.string.edit_server_key_hint)) }
        } else {
            null
        },
        singleLine = true,
        enabled = !state.isConnecting && !state.loading && !state.loadFailed,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    enabled = !state.isConnecting && !state.loading && !state.loadFailed && !pasting,
                    onClick = {
                        pasting = true
                        pasteError = null
                        scope.launch {
                            try {
                                val clip = clipboard.getClipEntry()?.clipData
                                val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                                if (text.isNullOrBlank()) {
                                    pasteError = R.string.add_server_paste_empty
                                } else {
                                    onApiKeyChange(text)
                                }
                            } catch (_: SecurityException) {
                                pasteError = R.string.add_server_paste_denied
                            } finally {
                                pasting = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.add_server_paste))
                }
                IconButton(
                    onClick = { visible = !visible },
                    enabled = !state.isConnecting && !state.loading && !state.loadFailed,
                ) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        stringResource(if (visible) R.string.add_server_hide_key else R.string.add_server_show_key),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) onSubmit() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SubmitRow(state: AddServerUiState, onSubmit: () -> Unit, onCancel: () -> Unit) {
    if (state.isConnecting) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.add_server_connecting), modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.add_server_cancel)) }
        }
    } else {
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_server_submit))
        }
    }
}

@Composable
private fun HintCard(icon: ImageVector, title: String?, body: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ErrorCard(error: ConnectionError) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color.Unspecified)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.conn_error_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(connectionErrorMessage(error), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun urlErrorMessage(reason: ServerUrl.Reason): String = stringResource(
    when (reason) {
        ServerUrl.Reason.EMPTY -> UiR.string.server_error_url_empty
        ServerUrl.Reason.MALFORMED -> UiR.string.server_error_url_malformed
        ServerUrl.Reason.INSECURE_HTTP -> UiR.string.server_error_url_http
        ServerUrl.Reason.UNSUPPORTED_SCHEME -> UiR.string.server_error_url_scheme
        ServerUrl.Reason.HAS_QUERY_OR_FRAGMENT -> UiR.string.server_error_url_query
    },
)

@Composable
internal fun connectionErrorMessage(error: ConnectionError): String = when (error) {
    ConnectionError.HostNotFound -> stringResource(R.string.conn_error_host_not_found)
    ConnectionError.Unreachable -> stringResource(R.string.conn_error_unreachable)
    ConnectionError.Timeout -> stringResource(R.string.conn_error_timeout)
    ConnectionError.InvalidApiKey -> stringResource(R.string.conn_error_invalid_key)
    is ConnectionError.Certificate -> stringResource(
        when (error.problem) {
            CertificateProblem.UNTRUSTED -> R.string.conn_error_cert_untrusted
            CertificateProblem.HOSTNAME_MISMATCH -> R.string.conn_error_cert_hostname
            CertificateProblem.EXPIRED -> R.string.conn_error_cert_expired
            CertificateProblem.OTHER -> R.string.conn_error_cert_other
        },
    )
    is ConnectionError.NotShlink -> stringResource(R.string.conn_error_not_shlink, error.httpStatus)
    is ConnectionError.Unhealthy -> stringResource(R.string.conn_error_unhealthy)
    is ConnectionError.UnsupportedVersion ->
        stringResource(R.string.conn_error_unsupported_version, error.version.orEmpty())
    is ConnectionError.HttpError -> stringResource(R.string.conn_error_http, error.status) +
        (error.detail?.let { "\n$it" } ?: "")
    is ConnectionError.Unknown -> stringResource(R.string.conn_error_unknown, error.message)
}

@PreviewLightDark
@Composable
private fun AddServerScreenPreview() {
    ShlinkerTheme(dynamicColor = false) {
        AddServerScreen(
            state = AddServerUiState(
                url = "s.example.org",
                apiKey = "abc",
                normalizedUrlPreview = "https://s.example.org",
                connectionError = ConnectionError.Certificate(CertificateProblem.UNTRUSTED),
            ),
            onNameChange = {},
            onUrlChange = {},
            onUrlFocusLost = {},
            onApiKeyChange = {},
            onSubmit = {},
            onCancel = {},
            onBack = {},
        )
    }
}
