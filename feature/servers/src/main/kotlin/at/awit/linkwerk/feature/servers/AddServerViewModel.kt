package at.awit.linkwerk.feature.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.awit.linkwerk.core.data.ServerProfileRepository
import at.awit.linkwerk.core.model.ServerUrl
import at.awit.linkwerk.core.network.ConnectionError
import at.awit.linkwerk.core.network.ConnectionResult
import at.awit.linkwerk.core.network.ConnectionTester
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddServerUiState(
    val name: String = "",
    val url: String = "",
    val apiKey: String = "",
    val urlError: ServerUrl.Reason? = null,
    val apiKeyMissing: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionError: ConnectionError? = null,
    /** Normalisierte URL als Vorschau, sobald gültig – zeigt, was tatsächlich gespeichert wird. */
    val normalizedUrlPreview: String? = null,
) {
    val canSubmit: Boolean get() = !isConnecting && url.isNotBlank() && apiKey.isNotBlank()
}

sealed interface AddServerEvent {
    data class Saved(val serverId: String) : AddServerEvent
}

@HiltViewModel
class AddServerViewModel @Inject constructor(
    private val repository: ServerProfileRepository,
    private val connectionTester: ConnectionTester,
) : ViewModel() {

    private val _state = MutableStateFlow(AddServerUiState())
    val state: StateFlow<AddServerUiState> = _state.asStateFlow()

    private val _events = Channel<AddServerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var connectJob: Job? = null

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }

    fun onUrlChange(value: String) = _state.update {
        it.copy(
            url = value,
            urlError = null,
            connectionError = null,
            normalizedUrlPreview = (ServerUrl.normalize(value) as? ServerUrl.Result.Valid)?.url,
        )
    }

    fun onApiKeyChange(value: String) = _state.update {
        it.copy(apiKey = value, apiKeyMissing = false, connectionError = null)
    }

    /** Beim Verlassen des URL-Feldes prüfen – nicht schon beim Tippen meckern. */
    fun onUrlFocusLost() = _state.update { s ->
        if (s.url.isBlank()) s else s.copy(urlError = (ServerUrl.normalize(s.url) as? ServerUrl.Result.Invalid)?.reason)
    }

    fun onSubmit() {
        val s = _state.value
        if (s.isConnecting) return

        val normalized = ServerUrl.normalize(s.url)
        val apiKey = s.apiKey.trim()
        val urlError = (normalized as? ServerUrl.Result.Invalid)?.reason
        if (urlError != null || apiKey.isEmpty()) {
            _state.update { it.copy(urlError = urlError, apiKeyMissing = apiKey.isEmpty()) }
            return
        }
        val url = (normalized as ServerUrl.Result.Valid).url

        _state.update { it.copy(isConnecting = true, connectionError = null) }
        connectJob = viewModelScope.launch {
            when (val result = connectionTester.test(url, apiKey)) {
                is ConnectionResult.Success -> {
                    val profile = repository.add(name = s.name, url = url, apiKey = apiKey)
                    _state.update { it.copy(isConnecting = false) }
                    _events.send(AddServerEvent.Saved(profile.id))
                }
                is ConnectionResult.Failure -> _state.update {
                    it.copy(isConnecting = false, connectionError = result.error)
                }
            }
        }
    }

    fun onCancelConnect() {
        connectJob?.cancel()
        _state.update { it.copy(isConnecting = false) }
    }
}
