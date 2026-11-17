package app.shlinker.feature.shorturls

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shlinker.core.data.ServerProfileRepository
import app.shlinker.core.model.ServerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SharedCreateState(
    val loading: Boolean = true,
    val profiles: List<ServerProfile> = emptyList(),
    val selectedId: String? = null,
    val error: IOException? = null,
)

@HiltViewModel
class SharedCreateViewModel @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(SharedCreateState())
    val state = _state.asStateFlow()
    val interrupted: Boolean = saved.get<Boolean>("attempted") == true
    val draftUrl: String? get() = saved["url"]
    val draftTitle: String? get() = saved["title"]
    val draftSlug: String get() = saved["slug"] ?: ""
    private var job: Job? = null

    init {
        observe()
    }

    fun observe() {
        if (!_state.value.loading && _state.value.error == null) return
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            combine(profiles.profiles, profiles.activeProfile) { list, active -> list to active }
                .catch { error ->
                    if (error !is IOException) throw error
                    _state.value = _state.value.copy(loading = false, error = error)
                }
                .collect { (list, active) ->
                    val previous = saved.get<String>("serverId")
                    val selected = if (previous != null) {
                        previous.takeIf { id -> list.any { it.id == id } }
                    } else {
                        active?.id?.takeIf { id -> list.any { it.id == id } }
                    }
                    if (selected != null) saved["serverId"] = selected
                    _state.value = SharedCreateState(profiles = list, selectedId = selected, loading = false)
                }
        }
    }

    fun select(id: String) {
        if (_state.value.profiles.none { it.id == id }) return
        saved["serverId"] = id
        _state.value = _state.value.copy(selectedId = id)
    }

    fun rememberDraft(state: CreateShortUrlState) {
        saved["url"] = state.longUrl
        saved["title"] = state.title
        saved["slug"] = state.slug
    }

    fun markAttempt() {
        saved["attempted"] = true
    }

    fun clearAttempt() {
        if (!interrupted) saved["attempted"] = false
    }
}
