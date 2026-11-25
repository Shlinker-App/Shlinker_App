package app.shlinker.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shlinker.R
import app.shlinker.core.model.ServerProfile
import app.shlinker.core.model.ServerSort
import app.shlinker.core.ui.DangerZoneDialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ServersScreen(
    onBack: () -> Unit,
    onAddServer: () -> Unit,
    onServerSelected: () -> Unit,
    onEditServer: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.selected.collect { onServerSelected() } }
    HomeContent(
        state, action, onAddServer,
        viewModel::select, viewModel::rename, viewModel::delete,
        viewModel::changeName, viewModel::confirm, viewModel::dismiss,
        viewModel::setSort, viewModel::reorder,
        onEditServer,
        onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    action: ServerActionState,
    onAddServer: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (ServerProfile) -> Unit,
    onDelete: (ServerProfile) -> Unit,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onSort: (ServerSort) -> Unit,
    onReorder: (List<String>) -> Unit,
    onEditServer: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    var displayed by remember { mutableStateOf(state.servers) }
    var dragging by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    LaunchedEffect(state.servers, state.sort, action.error) {
        displayed = state.servers
    }
    val manual = state.sort == ServerSort.MANUAL
    val controlsEnabled = !action.busy && !dragging
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val source = displayed.indexOfFirst { it.id == from.key }
        val target = displayed.indexOfFirst { it.id == to.key }
        if (source >= 0 && target >= 0) {
            displayed = displayed.toMutableList().apply { add(target, removeAt(source)) }
        }
    }
    val moveUp = stringResource(R.string.home_move_up)
    val moveDown = stringResource(R.string.home_move_down)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.servers_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = controlsEnabled) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.servers_back))
                    }
                },
                actions = {
                    IconButton(onClick = { sortMenu = true }, enabled = controlsEnabled) {
                        Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.home_sort))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        ServerSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(serverSortLabel(sort)) },
                                leadingIcon = {
                                    if (state.sort == sort) Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                },
                                onClick = {
                                    sortMenu = false
                                    onSort(sort)
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = {
            if (action.server == null && action.error != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dismiss)) }
                    },
                ) {
                    Text(actionErrorMessage(action.error))
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (controlsEnabled) onAddServer() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_add_server)) },
                modifier = Modifier.semantics {
                    if (!controlsEnabled) disabled()
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Text(
                    serverSortLabel(state.sort),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (state.servers.isEmpty()) {
                item { Text(stringResource(R.string.home_empty), Modifier.padding(16.dp)) }
            }
            items(displayed, key = { it.id }) { server ->
                ReorderableItem(reorderState, key = server.id, enabled = manual && !action.busy) { isDragging ->
                    val active = server.id == state.activeId
                    var menu by remember(server.id) { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(server.name) },
                        supportingContent = { Text(server.url) },
                        leadingContent = {
                            if (manual) {
                                Icon(
                                    Icons.Filled.DragHandle,
                                    contentDescription = stringResource(R.string.home_drag, server.name),
                                    modifier = Modifier
                                        .draggableHandle(
                                            enabled = !action.busy,
                                            onDragStarted = { dragging = true },
                                            onDragStopped = {
                                                dragging = false
                                                if (displayed != state.servers) onReorder(displayed.map { it.id })
                                            },
                                        )
                                        .size(48.dp).padding(12.dp),
                                    tint = if (isDragging) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            } else {
                                Icon(Icons.Outlined.Dns, contentDescription = null)
                            }
                        },
                        trailingContent = {
                            Row {
                                if (active) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = stringResource(R.string.home_server_active),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(onClick = { menu = true }, enabled = controlsEnabled) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        stringResource(R.string.home_server_actions, server.name),
                                    )
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.home_edit)) },
                                        onClick = {
                                            menu = false
                                            onEditServer(server.id)
                                        },
                                    )
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
                        modifier = Modifier.clickable(enabled = controlsEnabled) { onSelect(server.id) }.semantics {
                            if (manual && controlsEnabled) {
                                val index = displayed.indexOfFirst { it.id == server.id }
                                customActions = buildList {
                                    fun move(target: Int) {
                                        onReorder(
                                            displayed.toMutableList().apply {
                                                add(target, removeAt(index))
                                            }.map { it.id },
                                        )
                                    }
                                    if (index > 0) {
                                        add(
                                            CustomAccessibilityAction(moveUp) {
                                                move(index - 1)
                                                true
                                            },
                                        )
                                    }
                                    if (index <
                                        displayed.lastIndex
                                    ) {
                                        add(
                                            CustomAccessibilityAction(moveDown) {
                                                move(index + 1)
                                                true
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    action.server?.takeIf { action.deleting }?.let { server ->
        DangerZoneDialog(
            onDismissRequest = onDismiss,
            text = {
                Column {
                    Text(stringResource(R.string.home_delete_message, server.name))
                    action.error?.let { Text(actionErrorMessage(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = !action.busy) {
                    Text(stringResource(R.string.home_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !action.busy) {
                    Text(stringResource(R.string.home_cancel))
                }
            },
        )
    }
    action.server?.takeUnless { action.deleting }?.let {
        AlertDialog(
            // Avoid wrap-content window remeasurement when the name field changes size.
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.home_rename_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = action.name,
                        onValueChange = onNameChange,
                        label = { Text(stringResource(R.string.home_name)) },
                        singleLine = true,
                        enabled = !action.busy,
                        isError = action.error != null,
                    )
                    action.error?.let { Text(actionErrorMessage(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = !action.busy) {
                    Text(stringResource(R.string.home_save))
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
        ServerActionError.ORDER_CHANGED -> R.string.home_order_changed
    },
)

@Composable
private fun serverSortLabel(sort: ServerSort): String = stringResource(
    when (sort) {
        ServerSort.MANUAL -> R.string.home_sort_manual
        ServerSort.NAME_ASC -> R.string.home_sort_name_asc
        ServerSort.NAME_DESC -> R.string.home_sort_name_desc
        ServerSort.HOSTNAME_ASC -> R.string.home_sort_host_asc
        ServerSort.HOSTNAME_DESC -> R.string.home_sort_host_desc
    },
)
