package app.shlinker.feature.shorturls

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.shlinker.core.data.AppTagSettingsRepository
import app.shlinker.core.data.ServerOverviewRepository
import app.shlinker.core.data.ServerProfileRepository
import app.shlinker.core.data.ShortUrlRepository
import app.shlinker.core.model.DisplaySettings
import app.shlinker.core.model.ServerOverview
import app.shlinker.core.model.ShortUrlSort
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ServerOverviewState(
    val visible: Boolean = false,
    val serverId: String? = null,
    val overview: ServerOverview? = null,
    val loading: Boolean = false,
    val error: Exception? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ShortUrlsViewModel @Inject constructor(
    profiles: ServerProfileRepository,
    private val shortUrls: ShortUrlRepository,
    private val savedStateHandle: SavedStateHandle,
    private val overviews: ServerOverviewRepository,
    preferences: AppTagSettingsRepository,
) : ViewModel() {
    val search = savedStateHandle.getStateFlow("search", "")
    val server = profiles.activeProfile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val sort = shortUrls.sort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShortUrlSort.NEWEST)
    val display = preferences.display.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DisplaySettings())
    private val _sortSaving = MutableStateFlow(false)
    val sortSaving = _sortSaving.asStateFlow()
    private val _sortError = MutableStateFlow(false)
    val sortError = _sortError.asStateFlow()
    private val _overview = MutableStateFlow(ServerOverviewState())
    val overview = _overview.asStateFlow()
    private val overviewRequests = MutableStateFlow(0)

    val urls = combine(
        profiles.activeProfile,
        search.map { it.trim() }.debounce(300).distinctUntilChanged(),
        shortUrls.sort,
    ) { server, search, sort -> Triple(server, search, sort) }
        .flatMapLatest { (server, search, sort) ->
            if (server == null) {
                flowOf(
                    PagingData.empty(
                        sourceLoadStates = LoadStates(
                            refresh = LoadState.NotLoading(true),
                            prepend = LoadState.NotLoading(true),
                            append = LoadState.NotLoading(true),
                        ),
                    ),
                )
            } else {
                shortUrls.list(server, search, sort)
            }
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            combine(
                profiles.activeProfile,
                preferences.display.map { it.showServerOverview }.distinctUntilChanged(),
                overviewRequests,
            ) { server, visible, _ -> server to visible }
                .collectLatest { (server, visible) ->
                    if (server == null || !visible) {
                        _overview.value = ServerOverviewState()
                        return@collectLatest
                    }
                    // Keep the previous counts while reloading the same server to avoid flicker.
                    val previous = _overview.value.takeIf { it.serverId == server.id }?.overview
                    _overview.value = ServerOverviewState(true, server.id, previous, loading = true)
                    _overview.value = try {
                        ServerOverviewState(true, server.id, overviews.get(server))
                    } catch (e: IOException) {
                        ServerOverviewState(true, server.id, previous, error = e)
                    }
                }
        }
    }

    fun refreshOverview() {
        overviewRequests.value++
    }

    fun onSearchChange(value: String) {
        savedStateHandle["search"] = value
    }

    fun onSortChange(value: ShortUrlSort) {
        if (_sortSaving.value) return
        _sortSaving.value = true
        _sortError.value = false
        viewModelScope.launch {
            try {
                shortUrls.setSort(value)
            } catch (_: IOException) {
                _sortError.value = true
            } finally {
                _sortSaving.value = false
            }
        }
    }

    fun dismissSortError() {
        _sortError.value = false
    }
}
