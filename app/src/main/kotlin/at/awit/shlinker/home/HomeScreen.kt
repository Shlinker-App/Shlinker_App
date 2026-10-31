package at.awit.shlinker.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.awit.shlinker.R
import at.awit.shlinker.core.model.ServerProfile

@Composable
fun HomeScreen(onAddServer: () -> Unit, onShortUrls: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    HomeContent(
        state, action, onAddServer, onShortUrls,
        viewModel::setActive, viewModel::rename, viewModel::delete,
        viewModel::changeName, viewModel::confirm, viewModel::dismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    action: ServerActionState,
    onAddServer: () -> Unit,
    onShortUrls: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (ServerProfile) -> Unit,
    onDelete: (ServerProfile) -> Unit,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddServer,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_add_server)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.home_servers),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (state.servers.isEmpty()) {
                item { Text(stringResource(R.string.home_empty), Modifier.padding(16.dp)) }
            }
            if (action.server == null && action.error != null) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(actionErrorMessage(action.error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dismiss)) }
                    }
                }
            }
            items(state.servers, key = { it.id }) { server ->
                val active = server.id == state.activeId
                var menu by remember(server.id) { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(server.name) },
                    supportingContent = { Text(server.url) },
                    leadingContent = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                    trailingContent = {
                        Row {
                            if (active) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = stringResource(R.string.home_server_active),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { menu = true }, enabled = !action.busy) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.home_server_actions, server.name))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.home_rename)) },
                                    onClick = {
                                        menu = false
                                        onRename(server)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.home_delete)) },
                                    onClick = {
                                        menu = false
                                        onDelete(server)
                                    },
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable(enabled = !action.busy) { onSelect(server.id) },
                )
            }
            item {
                Button(
                    onClick = onShortUrls,
                    enabled = state.activeId != null,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(stringResource(R.string.home_short_urls))
                }
            }
        }
    }
    action.server?.let { server ->
        AlertDialog(
            // Avoid wrap-content window remeasurement when the name field changes size.
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = onDismiss,
            title = {
                Text(
                    stringResource(
                        if (action.deleting) R.string.home_delete_title else R.string.home_rename_title,
                    ),
                )
            },
            text = {
                Column {
                    if (action.deleting) {
                        Text(stringResource(R.string.home_delete_message, server.name))
                    } else {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = action.name,
                            onValueChange = onNameChange,
                            label = { Text(stringResource(R.string.home_name)) },
                            singleLine = true,
                            enabled = !action.busy,
                            isError = action.error != null,
                        )
                    }
                    action.error?.let { Text(actionErrorMessage(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = !action.busy) {
                    Text(stringResource(if (action.deleting) R.string.home_delete else R.string.home_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !action.busy) {
                    Text(stringResource(R.string.home_cancel))
                }
            },
        )
    }
}

@Composable
private fun actionErrorMessage(error: ServerActionError): String = stringResource(
    when (error) {
        ServerActionError.NAME_CONFLICT -> R.string.home_name_conflict
        ServerActionError.EMPTY_NAME -> R.string.home_name_empty
        ServerActionError.STORAGE -> R.string.home_storage_error
        ServerActionError.NOT_FOUND -> R.string.home_server_missing
    },
)
