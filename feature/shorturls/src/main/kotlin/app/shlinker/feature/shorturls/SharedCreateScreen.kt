package app.shlinker.feature.shorturls

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shlinker.core.model.ServerProfile
import app.shlinker.core.model.hostname
import app.shlinker.core.model.isValidDestinationUrl
import kotlinx.coroutines.launch

@Composable
fun SharedCreateRoute(
    url: String?,
    title: String,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    shareModel: SharedCreateViewModel = hiltViewModel(),
    createModel: CreateShortUrlViewModel = hiltViewModel(),
) {
    val servers by shareModel.state.collectAsStateWithLifecycle()
    val form by createModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(servers.selectedId) {
        servers.selectedId?.let {
            createModel.load(
                it,
                preserveDraft = true,
                initialUrl = shareModel.draftUrl ?: url.orEmpty(),
                initialTitle = shareModel.draftTitle ?: title,
                initialSlug = shareModel.draftSlug,
            )
        }
    }
    LaunchedEffect(form.longUrl, form.title, form.slug, form.loading) {
        if (!form.loading) shareModel.rememberDraft(form)
    }
    LaunchedEffect(form.error, form.busy) {
        if (!form.busy && form.error != null && form.error !in listOf(CreateError.UNCERTAIN, CreateError.SERVER)) {
            shareModel.clearAttempt()
        }
    }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var actionMessage by remember { mutableStateOf<String?>(null) }
    val copied = stringResource(R.string.short_urls_copied)
    val actionError = stringResource(R.string.shared_action_error)
    SharedCreateScreen(
        servers, form, shareModel.interrupted, url == null, actionMessage,
        onChange = createModel::change,
        onSelectServer = shareModel::select,
        onCreate = {
            shareModel.rememberDraft(form)
            if (isValidDestinationUrl(form.longUrl.trim()) && form.domainReady) shareModel.markAttempt()
            createModel.submit()
        },
        onRetryDomains = createModel::loadDomains,
        onRetryProfiles = shareModel::observe,
        onRetryForm = { servers.selectedId?.let { createModel.load(it, preserveDraft = true) } },
        onClose = onClose,
        onOpenApp = onOpenApp,
        onCopy = { value ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Shlink", value)))
                actionMessage = copied
            }
        },
        onShare = { value -> if (shareShortUrl(context, value) != null) actionMessage = actionError },
    )
}

@Composable
internal fun SharedCreateScreen(
    servers: SharedCreateState,
    form: CreateShortUrlState,
    interrupted: Boolean,
    invalidInput: Boolean,
    actionMessage: String?,
    onChange: (CreateShortUrlState) -> Unit,
    onSelectServer: (String) -> Unit,
    onCreate: () -> Unit,
    onRetryDomains: () -> Unit,
    onRetryProfiles: () -> Unit,
    onRetryForm: () -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val selected = servers.profiles.firstOrNull { it.id == servers.selectedId }
    val locked =
        form.busy ||
            form.created != null ||
            interrupted ||
            form.error in listOf(CreateError.UNCERTAIN, CreateError.SERVER)
    var expanded by remember { mutableStateOf(false) }
    BackHandler(enabled = form.busy) {}
    // Sets the background and the readable content color, as Scaffold does on the other screens.
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.shared_title), style = MaterialTheme.typography.headlineSmall)
            if (servers.loading) CircularProgressIndicator()
            if (servers.error != null) {
                Text(stringResource(R.string.shared_profiles_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetryProfiles) { Text(stringResource(R.string.short_urls_retry)) }
            }
            if (interrupted) {
                Text(stringResource(R.string.shared_interrupted), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onOpenApp) { Text(stringResource(R.string.shared_open_app)) }
            }
            if (!servers.loading && servers.error == null && servers.profiles.isEmpty()) {
                Text(stringResource(R.string.shared_no_servers))
                Button(onClick = onOpenApp) { Text(stringResource(R.string.shared_setup)) }
            }
            val selectable = servers.profiles.size > 1 || (servers.profiles.size == 1 && selected == null)
            // A locked selection is shown as plain text; a disabled button would be too faint to read.
            if (selected != null && (!selectable || locked)) {
                ServerLabel(selected)
            } else if (selectable) {
                Column {
                    TextButton(onClick = { expanded = true }, enabled = !locked) {
                        if (selected != null) {
                            ServerLabel(selected)
                        } else {
                            val choose = stringResource(R.string.shared_choose_server)
                            Text(stringResource(R.string.short_urls_server, choose))
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        servers.profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(profile.name)
                                        Text(profile.hostname, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    onSelectServer(profile.id)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
            form.created?.let { created ->
                Text(stringResource(R.string.create_success), style = MaterialTheme.typography.titleMedium)
                Text(created.shortUrl)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { onCopy(created.shortUrl) }) {
                        Icon(Icons.Outlined.ContentCopy, stringResource(R.string.short_urls_copy, created.shortUrl))
                    }
                    IconButton(onClick = { onShare(created.shortUrl) }) {
                        Icon(Icons.Outlined.Share, stringResource(R.string.short_urls_share, created.shortUrl))
                    }
                }
                ShlinkQrCodeContent(created.shortUrl, maxImageWidth = 240.dp)
            } ?: run {
                if (invalidInput && !interrupted) {
                    Text(stringResource(R.string.shared_invalid), color = MaterialTheme.colorScheme.error)
                }
                val enabled = !locked &&
                    selected != null &&
                    selected.id == form.profileId &&
                    !form.loading &&
                    servers.error == null
                OutlinedTextField(
                    form.longUrl,
                    { onChange(form.copy(longUrl = it)) },
                    Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    label = { Text(stringResource(R.string.create_url)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    isError = form.error == CreateError.URL,
                )
                OutlinedTextField(
                    form.slug,
                    { onChange(form.copy(slug = it)) },
                    Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    label = { Text(stringResource(R.string.create_slug)) },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    form.title,
                    { onChange(form.copy(title = it)) },
                    Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    label = { Text(stringResource(R.string.create_label_title)) },
                )
                if (selected != null && !form.loading) {
                    if (form.domainReady && !form.manualDomain) {
                        Text(
                            stringResource(R.string.shared_domain, form.domain),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        CreateDomainField(form, enabled, onChange, onRetryDomains)
                    }
                }
                if (form.loading && selected != null || form.busy) CircularProgressIndicator()
                form.error?.let { error ->
                    Text(stringResource(createErrorMessage(error)), color = MaterialTheme.colorScheme.error)
                    if (error == CreateError.LOAD || error == CreateError.MISSING_SERVER) {
                        TextButton(onClick = onRetryForm, enabled = !locked) {
                            Text(stringResource(R.string.short_urls_retry))
                        }
                    }
                }
                if (form.error == CreateError.SERVER) {
                    Text(stringResource(R.string.shared_interrupted), color = MaterialTheme.colorScheme.error)
                }
                if (form.error in listOf(CreateError.UNCERTAIN, CreateError.SERVER)) {
                    TextButton(onClick = onOpenApp) { Text(stringResource(R.string.shared_open_app)) }
                }
                Text(stringResource(R.string.shared_defaults_hint), style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = onCreate,
                    enabled = enabled && form.serverName != null && form.domainReady && form.longUrl.isNotBlank(),
                ) { Text(stringResource(R.string.create_submit)) }
            }
            actionMessage?.let { Text(it) }
            TextButton(onClick = onClose, enabled = !form.busy) {
                Text(stringResource(if (form.created == null) R.string.shared_cancel else R.string.create_done))
            }
        }
    }
}

@Composable
private fun ServerLabel(profile: ServerProfile) {
    Column {
        Text(stringResource(R.string.short_urls_server, profile.name))
        Text(profile.hostname, style = MaterialTheme.typography.bodySmall)
    }
}
