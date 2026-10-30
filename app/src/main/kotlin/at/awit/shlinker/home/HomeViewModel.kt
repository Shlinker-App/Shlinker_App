package at.awit.shlinker.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.model.ServerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(val servers: List<ServerProfile> = emptyList(), val activeId: String? = null)

/** Vorläufige Startseite: zeigt gespeicherte Server. Wird durch Dashboard ersetzt. */
@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: ServerProfileRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(repository.profiles, repository.activeProfile) { all, active ->
        HomeUiState(all, active?.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setActive(id: String) {
        viewModelScope.launch { repository.setActive(id) }
    }
}
