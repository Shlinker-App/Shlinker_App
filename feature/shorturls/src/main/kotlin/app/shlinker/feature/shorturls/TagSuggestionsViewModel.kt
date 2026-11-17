package app.shlinker.feature.shorturls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shlinker.core.data.ServerNotFoundException
import app.shlinker.core.data.ServerTagsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TagSuggestionsState(
    val tags: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: Exception? = null,
)

@HiltViewModel
class TagSuggestionsViewModel @Inject constructor(private val repository: ServerTagsRepository) : ViewModel() {
    private val _state = MutableStateFlow(TagSuggestionsState())
    val state = _state.asStateFlow()
    private var serverId: String? = null
    private var job: Job? = null

    fun load(id: String, retry: Boolean = false) {
        if (serverId == id && (!retry || _state.value.loading)) return
        job?.cancel()
        serverId = id
        _state.value = TagSuggestionsState(loading = true)
        job = viewModelScope.launch {
            try {
                _state.value = TagSuggestionsState(tags = repository.get(id))
            } catch (e: ServerNotFoundException) {
                _state.value = TagSuggestionsState(error = e)
            } catch (e: IOException) {
                _state.value = TagSuggestionsState(error = e)
            }
        }
    }
}
