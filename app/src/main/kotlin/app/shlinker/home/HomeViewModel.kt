package app.shlinker.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shlinker.core.data.ServerNameConflictException
import app.shlinker.core.data.ServerNotFoundException
import app.shlinker.core.data.ServerOrderChangedException
import app.shlinker.core.data.ServerProfileRepository
import app.shlinker.core.model.ServerProfile
import app.shlinker.core.model.ServerSort
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val servers: List<ServerProfile> = emptyList(),
    val activeId: String? = null,
    val sort: ServerSort = ServerSort.MANUAL,
)

enum class ServerActionError { NAME_CONFLICT, EMPTY_NAME, STORAGE, NOT_FOUND, ORDER_CHANGED }

data class ServerActionState(
    val server: ServerProfile? = null,
    val deleting: Boolean = false,
    val name: String = "",
    val busy: Boolean = false,
    val error: ServerActionError? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: ServerProfileRepository) : ViewModel() {
    private val _action = MutableStateFlow(ServerActionState())
    val action = _action.asStateFlow()
    val state: StateFlow<HomeUiState> = combine(repository.profiles, repository.activeProfile, repository.sort) {
            all,
            active,
            sort,
        ->
        HomeUiState(all, active?.id, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setActive(id: String) {
        mutate { repository.setActive(id) }
    }

    fun setSort(sort: ServerSort) {
        mutate { repository.setSort(sort) }
    }

    fun reorder(ids: List<String>) {
        mutate { repository.reorder(ids) }
    }

    fun rename(server: ServerProfile) {
        if (!_action.value.busy) _action.value = ServerActionState(server = server, name = server.name)
    }

    fun delete(server: ServerProfile) {
        if (!_action.value.busy) _action.value = ServerActionState(server = server, deleting = true)
    }

    fun changeName(value: String) {
        if (!_action.value.busy) _action.value = _action.value.copy(name = value, error = null)
    }

    fun dismiss() {
        if (!_action.value.busy) _action.value = ServerActionState()
    }

    fun confirm() {
        val current = _action.value
        val server = current.server ?: return
        if (current.busy) return
        if (!current.deleting && current.name.isBlank()) {
            _action.value = current.copy(error = ServerActionError.EMPTY_NAME)
            return
        }
        mutate {
            if (current.deleting) repository.delete(server.id) else repository.update(server.id, name = current.name)
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        if (_action.value.busy) return
        _action.value = _action.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                block()
                _action.value = ServerActionState()
            } catch (_: ServerNameConflictException) {
                _action.value = _action.value.copy(busy = false, error = ServerActionError.NAME_CONFLICT)
            } catch (_: ServerNotFoundException) {
                _action.value = _action.value.copy(busy = false, error = ServerActionError.NOT_FOUND)
            } catch (_: IOException) {
                _action.value = _action.value.copy(busy = false, error = ServerActionError.STORAGE)
            } catch (_: ServerOrderChangedException) {
                _action.value = _action.value.copy(busy = false, error = ServerActionError.ORDER_CHANGED)
            }
        }
    }
}
