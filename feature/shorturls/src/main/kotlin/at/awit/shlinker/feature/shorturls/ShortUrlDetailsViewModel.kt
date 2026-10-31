package at.awit.shlinker.feature.shorturls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.awit.shlinker.core.data.ServerNotFoundException
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.data.ShortUrlDetailsRepository
import at.awit.shlinker.core.model.ShortUrlDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ShortUrlDetailsState(
    val serverName: String? = null,
    val loading: Boolean = true,
    val details: ShortUrlDetails? = null,
    val error: Exception? = null,
)

@HiltViewModel
class ShortUrlDetailsViewModel @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val repository: ShortUrlDetailsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ShortUrlDetailsState())
    val state = _state.asStateFlow()
    private var identity: Triple<String, String, String?>? = null
    private var job: Job? = null

    fun load(serverId: String, shortCode: String, domain: String?, refresh: Boolean = false) {
        val requested = Triple(serverId, shortCode, domain)
        if (!refresh && identity == requested) return
        job?.cancel()
        identity = requested
        _state.value = ShortUrlDetailsState()
        job = viewModelScope.launch {
            try {
                val server = profiles.profiles.first().firstOrNull { it.id == serverId }
                    ?: throw ServerNotFoundException(serverId)
                _state.value = _state.value.copy(serverName = server.name)
                val details = repository.get(serverId, shortCode, domain)
                _state.value = _state.value.copy(loading = false, details = details)
            } catch (e: ServerNotFoundException) {
                _state.value = _state.value.copy(loading = false, error = e)
            } catch (e: IOException) {
                _state.value = _state.value.copy(loading = false, error = e)
            }
        }
    }
}
