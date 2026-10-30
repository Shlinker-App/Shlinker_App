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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ShortUrlsViewModel @Inject constructor(
    profiles: ServerProfileRepository,
    shortUrls: ShortUrlRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val search = savedStateHandle.getStateFlow("search", "")
    val server = profiles.activeProfile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val urls = combine(
        profiles.activeProfile,
        search.map { it.trim() }.debounce(300).distinctUntilChanged(),
    ) { server, search -> server to search }
        .flatMapLatest { (server, search) ->
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
                shortUrls.list(server, search)
            }
        }
        .cachedIn(viewModelScope)

    fun onSearchChange(value: String) {
        savedStateHandle["search"] = value
    }
}
