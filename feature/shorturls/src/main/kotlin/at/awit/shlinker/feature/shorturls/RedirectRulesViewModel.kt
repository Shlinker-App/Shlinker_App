package at.awit.shlinker.feature.shorturls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.awit.shlinker.core.data.RedirectRulesConflictException
import at.awit.shlinker.core.data.RedirectRulesRepository
import at.awit.shlinker.core.data.ServerNotFoundException
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.model.RedirectRule
import at.awit.shlinker.core.model.RedirectRules
import at.awit.shlinker.core.network.RedirectRulesException
import at.awit.shlinker.core.network.RedirectRulesFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class RedirectRulesState(
    val serverName: String? = null,
    val loading: Boolean = true,
    val original: RedirectRules? = null,
    val draft: List<RedirectRule>? = null,
    val saving: Boolean = false,
    val confirmSave: Boolean = false,
    val validation: Boolean = false,
    val needsReload: Boolean = false,
    val error: Exception? = null,
    val saved: Boolean = false,
    val changeRevision: Int = 0,
) {
    val dirty: Boolean get() = draft != null && draft != original?.rules
}

@HiltViewModel
class RedirectRulesViewModel @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val repository: RedirectRulesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RedirectRulesState())
    val state = _state.asStateFlow()
    private var identity: Triple<String, String, String?>? = null
    private var job: Job? = null

    fun load(serverId: String, shortCode: String, domain: String?, refresh: Boolean = false) {
        val requested = Triple(serverId, shortCode, domain)
        if (_state.value.saving || (!refresh && requested == identity)) return
        job?.cancel()
        val name = _state.value.serverName.takeIf { identity?.first == serverId }
        identity = requested
        _state.value = RedirectRulesState(serverName = name, changeRevision = _state.value.changeRevision)
        job = viewModelScope.launch {
            try {
                val server = profiles.profiles.first().firstOrNull { it.id == serverId }
                    ?: throw ServerNotFoundException(serverId)
                _state.value = _state.value.copy(serverName = server.name)
                val rules = repository.get(serverId, shortCode, domain)
                _state.value = _state.value.copy(loading = false, original = rules)
            } catch (e: ServerNotFoundException) {
                _state.value = _state.value.copy(loading = false, error = e)
            } catch (e: IOException) {
                _state.value = _state.value.copy(loading = false, error = e)
            }
        }
    }

    fun edit() {
        val s = _state.value
        if (s.loading || s.saving || s.needsReload || s.original?.hasUnknownConditions != false) return
        _state.value = s.copy(draft = s.original.rules, validation = false, error = null, saved = false)
    }

    fun change(rules: List<RedirectRule>) {
        val s = _state.value
        if (s.draft == null || s.saving || s.needsReload || s.confirmSave) return
        _state.value = s.copy(draft = rules, validation = false, error = null)
    }

    fun cancel() {
        if (_state.value.saving || _state.value.needsReload) return
        _state.value = _state.value.copy(draft = null, validation = false, error = null, confirmSave = false)
    }

    fun requestSave() {
        val s = _state.value
        val rules = s.draft ?: return
        if (s.saving || s.needsReload || !s.dirty) return
        _state.value = s.copy(validation = rules.any { !it.isValid }, confirmSave = rules.all { it.isValid })
    }

    fun dismissSave() {
        if (!_state.value.saving) _state.value = _state.value.copy(confirmSave = false)
    }

    fun save() {
        val s = _state.value
        val id = identity ?: return
        val original = s.original ?: return
        val rules = s.draft ?: return
        if (!s.confirmSave || s.saving || s.needsReload || !s.dirty || rules.any { !it.isValid }) return
        _state.value = s.copy(saving = true, confirmSave = false, error = null)
        job = viewModelScope.launch {
            try {
                val result = repository.set(id.first, id.second, id.third, original, rules)
                _state.value = _state.value.copy(
                    original = result,
                    draft = null,
                    saving = false,
                    changeRevision = s.changeRevision + 1,
                    saved = true,
                )
            } catch (e: ServerNotFoundException) {
                failed(e)
            } catch (e: IOException) {
                failed(e)
            }
        }
    }

    private fun failed(error: Exception) {
        val uncertain = error is RedirectRulesException && error.failure == RedirectRulesFailure.UNCERTAIN
        _state.value = _state.value.copy(
            saving = false,
            error = error,
            needsReload = uncertain || error is RedirectRulesConflictException,
            changeRevision = _state.value.changeRevision + if (uncertain) 1 else 0,
        )
    }
}
