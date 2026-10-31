package at.awit.shlinker.feature.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.awit.shlinker.core.data.ApiKeyResult
import at.awit.shlinker.core.data.DuplicateCheckUnavailableException
import at.awit.shlinker.core.data.DuplicateServerException
import at.awit.shlinker.core.data.ServerNameConflictException
import at.awit.shlinker.core.data.ServerNotFoundException
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.model.ServerUrl
import at.awit.shlinker.core.network.ConnectionError
import at.awit.shlinker.core.network.ConnectionResult
import at.awit.shlinker.core.network.ConnectionTester
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val saveError: ServerSaveError? = null,
    /** Normalisierte URL als Vorschau, sobald gültig – zeigt, was tatsächlich gespeichert wird. */
    val normalizedUrlPreview: String? = null,
    val editing: Boolean = false,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
) {
    val canSubmit: Boolean get() = !loading &&
        !loadFailed &&
        !isConnecting &&
        url.isNotBlank() &&
        (editing || apiKey.isNotBlank())
}

enum class ServerSaveError { DUPLICATE, NAME_CONFLICT, CANNOT_CHECK_DUPLICATE, STORAGE, NOT_FOUND, KEY_UNAVAILABLE }

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
    private var editingId: String? = null

    fun load(id: String) {
        if (editingId == id && !_state.value.loadFailed) return
        editingId = id
        _state.value = AddServerUiState(editing = true, loading = true)
        viewModelScope.launch {
            try {
                val profile = repository.profiles.first().firstOrNull { it.id == id }
                _state.value = if (profile == null) {
                    AddServerUiState(editing = true, loadFailed = true, saveError = ServerSaveError.NOT_FOUND)
                } else {
                    AddServerUiState(editing = true, name = profile.name, url = profile.url)
                }
            } catch (_: IOException) {
                _state.value = AddServerUiState(editing = true, loadFailed = true, saveError = ServerSaveError.STORAGE)
            }
        }
    }

    fun onNameChange(value: String) = _state.update {
        it.copy(name = value, saveError = it.saveError.takeUnless { error -> error == ServerSaveError.NAME_CONFLICT })
    }

    fun onUrlChange(value: String) = _state.update {
        it.copy(
            url = value,
            urlError = null,
            connectionError = null,
            saveError = null,
            normalizedUrlPreview = (ServerUrl.normalize(value) as? ServerUrl.Result.Valid)?.url,
        )
    }

    fun onApiKeyChange(value: String) = _state.update {
        it.copy(apiKey = value, apiKeyMissing = false, connectionError = null, saveError = null)
    }

    /** Beim Verlassen des URL-Feldes prüfen – nicht schon beim Tippen meckern. */
    fun onUrlFocusLost() = _state.update { s ->
        if (s.url.isBlank()) s else s.copy(urlError = (ServerUrl.normalize(s.url) as? ServerUrl.Result.Invalid)?.reason)
    }

    fun onSubmit() {
        val s = _state.value
        if (s.isConnecting || s.loading || s.loadFailed) return

        val normalized = ServerUrl.normalize(s.url)
        val enteredKey = s.apiKey.trim()
        val urlError = (normalized as? ServerUrl.Result.Invalid)?.reason
        if (urlError != null || (!s.editing && enteredKey.isEmpty())) {
            _state.update { it.copy(urlError = urlError, apiKeyMissing = !s.editing && enteredKey.isEmpty()) }
            return
        }
        val url = (normalized as ServerUrl.Result.Valid).url

        _state.update { it.copy(isConnecting = true, connectionError = null, saveError = null) }
        connectJob = viewModelScope.launch {
            val id = editingId
            val apiKey = try {
                if (enteredKey.isNotEmpty()) {
                    enteredKey
                } else {
                    when (val key = repository.getApiKey(requireNotNull(id))) {
                        is ApiKeyResult.Available -> key.apiKey
                        else -> {
                            _state.update { it.copy(isConnecting = false, saveError = ServerSaveError.KEY_UNAVAILABLE) }
                            return@launch
                        }
                    }
                }
            } catch (_: IOException) {
                _state.update { it.copy(isConnecting = false, saveError = ServerSaveError.STORAGE) }
                return@launch
            }
            when (val result = connectionTester.test(url, apiKey)) {
                is ConnectionResult.Success -> {
                    val profile = try {
                        if (id == null) {
                            repository.add(name = s.name, url = url, apiKey = apiKey)
                        } else {
                            repository.update(id, name = s.name, url = url, apiKey = enteredKey.ifEmpty { null })
                        }
                    } catch (_: ServerNotFoundException) {
                        _state.update { it.copy(isConnecting = false, saveError = ServerSaveError.NOT_FOUND) }
                        return@launch
                    } catch (_: DuplicateServerException) {
                        _state.update { it.copy(isConnecting = false, saveError = ServerSaveError.DUPLICATE) }
                        return@launch
                    } catch (_: ServerNameConflictException) {
                        _state.update { it.copy(isConnecting = false, saveError = ServerSaveError.NAME_CONFLICT) }
                        return@launch
                    } catch (_: DuplicateCheckUnavailableException) {
                        _state.update {
                            it.copy(isConnecting = false, saveError = ServerSaveError.CANNOT_CHECK_DUPLICATE)
                        }
                        return@launch
                    } catch (_: IOException) {
                        _state.update { it.copy(isConnecting = false, saveError = ServerSaveError.STORAGE) }
                        return@launch
                    }
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
