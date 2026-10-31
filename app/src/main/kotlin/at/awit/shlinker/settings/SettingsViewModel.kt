package at.awit.shlinker.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.awit.shlinker.core.data.AppTagSettingsRepository
import at.awit.shlinker.core.model.AppTagSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsState(
    val appTag: AppTagSettings = AppTagSettings(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val loadFailed: Boolean = false,
    val saveFailed: Boolean = false,
    val invalidName: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: AppTagSettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (_state.value.saving) return
        _state.value = _state.value.copy(loading = true, loadFailed = false)
        viewModelScope.launch {
            try {
                _state.value = SettingsState(appTag = repository.settings.first(), loading = false)
            } catch (_: IOException) {
                _state.value = _state.value.copy(loading = false, loadFailed = true)
            }
        }
    }

    fun change(value: AppTagSettings) {
        if (_state.value.loading || _state.value.saving || _state.value.loadFailed) return
        _state.value = _state.value.copy(appTag = value, invalidName = false, saveFailed = false, saved = false)
    }

    fun save() {
        val state = _state.value
        if (state.loading || state.saving || state.loadFailed) return
        if (!state.appTag.isValid) {
            _state.value = state.copy(invalidName = true, saved = false)
            return
        }
        _state.value = state.copy(saving = true, saveFailed = false, saved = false)
        viewModelScope.launch {
            try {
                repository.save(state.appTag)
                _state.value = _state.value.copy(
                    appTag = state.appTag.copy(name = state.appTag.name.trim()),
                    saving = false,
                    saved = true,
                )
            } catch (_: IOException) {
                _state.value = _state.value.copy(saving = false, saveFailed = true)
            }
        }
    }
}
