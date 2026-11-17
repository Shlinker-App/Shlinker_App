package app.shlinker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shlinker.core.data.ServerProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface StartState {
    data object Loading : StartState

    data object NoServer : StartState

    data object Ready : StartState
}

@HiltViewModel
class MainViewModel @Inject constructor(repository: ServerProfileRepository) : ViewModel() {
    /** Nur für die Startentscheidung – danach übernimmt die Navigation. */
    val startState: StateFlow<StartState> = repository.profiles
        .map { if (it.isEmpty()) StartState.NoServer else StartState.Ready }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StartState.Loading)
}
