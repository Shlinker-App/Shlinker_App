package at.awit.shlinker.feature.shorturls

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import at.awit.shlinker.core.data.ShortUrlApiKeyException
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.model.ShortUrlSort
import at.awit.shlinker.core.network.ConnectionError
import at.awit.shlinker.core.network.InvalidShortUrlResponseException
import at.awit.shlinker.core.network.ShortUrlRequestException
import kotlinx.coroutines.launch

@Composable
fun ShortUrlsRoute(onBack: () -> Unit, viewModel: ShortUrlsViewModel = hiltViewModel()) {
    val search by viewModel.search.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val sortSaving by viewModel.sortSaving.collectAsStateWithLifecycle()
    val sortError by viewModel.sortError.collectAsStateWithLifecycle()
    val urls = viewModel.urls.collectAsLazyPagingItems()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val copied = stringResource(R.string.short_urls_copied)
    ShortUrlsScreen(
        urls = urls,
        search = search,
        serverName = server?.name,
        onSearchChange = viewModel::onSearchChange,
        onBack = onBack,
        onCopy = { url ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Short URL", url)))
                snackbar.showSnackbar(copied)
            }
        },
        snackbar = snackbar,
        sort = sort,
        onSortChange = viewModel::onSortChange,
        sortSaving = sortSaving,
        sortError = sortError,
        onDismissSortError = viewModel::dismissSortError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShortUrlsScreen(
    urls: LazyPagingItems<ShortUrl>,
    search: String,
    serverName: String?,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    sort: ShortUrlSort = ShortUrlSort.NEWEST,
    onSortChange: (ShortUrlSort) -> Unit = {},
    sortSaving: Boolean = false,
    sortError: Boolean = false,
    onDismissSortError: () -> Unit = {},
) {
    var sortMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(search, serverName, sort) { listState.scrollToItem(0) }
    val refresh = urls.loadState.refresh
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.short_urls_title))
                        serverName?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.short_urls_back))
                    }
                },
                actions = {
                    IconButton(onClick = { sortMenu = true }, enabled = !sortSaving) {
                        Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.short_urls_sort))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        ShortUrlSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(shortUrlSortLabel(option)) },
                                leadingIcon = {
                                    if (sort == option) Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                },
                                onClick = {
                                    sortMenu = false
                                    onSortChange(option)
                                },
                            )
                        }
                    }
                    IconButton(onClick = urls::refresh, enabled = refresh !is LoadState.Loading) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.short_urls_refresh))
                    }
                },
            )
        },
        snackbarHost = {
            if (sortError) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = onDismissSortError) { Text(stringResource(R.string.short_urls_dismiss)) }
                    },
                ) { Text(stringResource(R.string.short_urls_sort_error)) }
            } else {
                SnackbarHost(snackbar)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                shortUrlSortLabel(sort),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                label = { Text(stringResource(R.string.short_urls_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PullToRefreshBox(
                isRefreshing = refresh is LoadState.Loading,
                onRefresh = urls::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (refresh is LoadState.Error) {
                        item { LoadError(refresh.error, urls::retry) }
                    }
                    if (refresh is LoadState.NotLoading && urls.itemCount == 0) {
                        item {
                            Text(
                                stringResource(
                                    when {
                                        serverName == null -> R.string.short_urls_no_server
                                        search.isBlank() -> R.string.short_urls_empty
                                        else -> R.string.short_urls_no_results
                                    },
                                ),
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    items(count = urls.itemCount) { index ->
                        urls[index]?.let { url ->
                            ShortUrlRow(url, onCopy)
                            HorizontalDivider()
                        }
                    }
                    when (val append = urls.loadState.append) {
                        is LoadState.Loading -> item {
                            CircularProgressIndicator(Modifier.padding(16.dp))
                        }
                        is LoadState.Error -> item { LoadError(append.error, urls::retry) }
                        is LoadState.NotLoading -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun shortUrlSortLabel(sort: ShortUrlSort): String = stringResource(
    when (sort) {
        ShortUrlSort.NEWEST -> R.string.short_urls_sort_newest
        ShortUrlSort.OLDEST -> R.string.short_urls_sort_oldest
        ShortUrlSort.MOST_VISITED -> R.string.short_urls_sort_most
        ShortUrlSort.LEAST_VISITED -> R.string.short_urls_sort_least
    },
)

@Composable
private fun ShortUrlRow(url: ShortUrl, onCopy: (String) -> Unit) {
    ListItem(
        headlineContent = {
            Text(url.title?.takeIf { it.isNotBlank() } ?: url.shortUrl, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!url.title.isNullOrBlank()) {
                    Text(url.shortUrl, color = MaterialTheme.colorScheme.primary)
                }
                Text(url.longUrl, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (url.tags.isNotEmpty()) {
                    Text(url.tags.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        trailingContent = {
            IconButton(onClick = { onCopy(url.shortUrl) }) {
                Icon(Icons.Outlined.ContentCopy, stringResource(R.string.short_urls_copy, url.shortUrl))
            }
        },
    )
}

@Composable
private fun LoadError(error: Throwable, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(errorMessage(error), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = retry) { Text(stringResource(R.string.short_urls_retry)) }
    }
}

@Composable
private fun errorMessage(error: Throwable): String = when (error) {
    is ShortUrlApiKeyException -> stringResource(R.string.short_urls_key_missing)
    is InvalidShortUrlResponseException -> stringResource(R.string.short_urls_invalid_response)
    is ShortUrlRequestException -> when (val reason = error.error) {
        ConnectionError.InvalidApiKey -> stringResource(R.string.short_urls_key_invalid)
        ConnectionError.HostNotFound -> stringResource(R.string.short_urls_host_not_found)
        ConnectionError.Unreachable -> stringResource(R.string.short_urls_unreachable)
        ConnectionError.Timeout -> stringResource(R.string.short_urls_timeout)
        is ConnectionError.Certificate -> stringResource(R.string.short_urls_certificate)
        is ConnectionError.HttpError -> stringResource(R.string.short_urls_http_error, reason.status)
        else -> stringResource(R.string.short_urls_load_error)
    }
    else -> stringResource(R.string.short_urls_load_error)
}
