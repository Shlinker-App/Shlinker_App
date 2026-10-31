package at.awit.shlinker.feature.shorturls

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.data.ShortUrlRepository
import at.awit.shlinker.core.model.ShortUrlSort
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ShortUrlsViewModel @Inject constructor(
    profiles: ServerProfileRepository,
    private val shortUrls: ShortUrlRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val search = savedStateHandle.getStateFlow("search", "")
    val server = profiles.activeProfile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val sort = shortUrls.sort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShortUrlSort.NEWEST)
    private val _sortSaving = MutableStateFlow(false)
    val sortSaving = _sortSaving.asStateFlow()
    private val _sortError = MutableStateFlow(false)
    val sortError = _sortError.asStateFlow()

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
