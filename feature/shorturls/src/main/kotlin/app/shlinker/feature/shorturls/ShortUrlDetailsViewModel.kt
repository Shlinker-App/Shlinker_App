package app.shlinker.feature.shorturls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shlinker.core.data.ServerNotFoundException
import app.shlinker.core.data.ServerProfileRepository
import app.shlinker.core.data.ShortUrlDeleter
import app.shlinker.core.data.ShortUrlDetailsRepository
import app.shlinker.core.data.ShortUrlUpdater
import app.shlinker.core.model.ShortUrlDetails
import app.shlinker.core.model.isValidDestinationUrl
import app.shlinker.core.network.DeleteShortUrlException
import app.shlinker.core.network.DeleteShortUrlFailure
import app.shlinker.core.network.UpdateShortUrlException
import app.shlinker.core.network.UpdateShortUrlFailure
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
    val form: ShortUrlEditForm? = null,
    val saving: Boolean = false,
    val validation: EditValidation? = null,
    val saved: Boolean = false,
    val needsReload: Boolean = false,
    val changeRevision: Int = 0,
    val confirmingDelete: Boolean = false,
    val deleted: Boolean = false,
)

enum class EditValidation { URL, SINCE, UNTIL, SINCE_RANGE, UNTIL_RANGE, LIMIT }

@HiltViewModel
class ShortUrlDetailsViewModel @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val repository: ShortUrlDetailsRepository,
    private val updater: ShortUrlUpdater,
    private val deleter: ShortUrlDeleter,
) : ViewModel() {
    private val _state = MutableStateFlow(ShortUrlDetailsState())
    val state = _state.asStateFlow()
    private var identity: Triple<String, String, String?>? = null
    private var job: Job? = null

    fun load(serverId: String, shortCode: String, domain: String?, refresh: Boolean = false) {
        val requested = Triple(serverId, shortCode, domain)
        if (_state.value.saving) return
        if (!refresh && identity == requested) return
        job?.cancel()
        identity = requested
        _state.value = ShortUrlDetailsState(changeRevision = _state.value.changeRevision)
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

    fun edit() {
        val s = _state.value
        if (s.loading || s.saving || s.needsReload || s.confirmingDelete || s.deleted) return
        val details = s.details ?: return
        _state.value =
            s.copy(form = ShortUrlEditForm.from(details), error = null, validation = null, saved = false)
    }

    fun change(form: ShortUrlEditForm) {
        if (_state.value.form == null || _state.value.saving || _state.value.needsReload) return
        _state.value = _state.value.copy(form = form, error = null, validation = null)
    }

    fun cancelEdit() {
        if (_state.value.saving || _state.value.needsReload) return
        _state.value = _state.value.copy(form = null, validation = null, error = null)
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.needsReload) return
        val form = s.form ?: return
        val original = s.details ?: return
        val id = identity?.first ?: return
        val input = form.toInput(original)
        val maxVisits = input.maxVisits
        val validation = when {
            !isValidDestinationUrl(input.longUrl) -> EditValidation.URL
            form.maxVisits.isNotBlank() && (maxVisits == null || maxVisits < 1) ->
                EditValidation.LIMIT
            else -> form.dateValidation(original)
        }
        if (validation != null) {
            _state.value = s.copy(validation = validation)
            return
        }
        _state.value = s.copy(saving = true, error = null, validation = null)
        job = viewModelScope.launch {
            try {
                val details = updater.update(id, input)
                _state.value = _state.value.copy(
                    details = details,
                    form = null,
                    saving = false,
                    saved = true,
                    changeRevision = s.changeRevision + 1,
                )
            } catch (e: UpdateShortUrlException) {
                val uncertain =
                    e.failure in listOf(UpdateShortUrlFailure.UNCERTAIN, UpdateShortUrlFailure.SERVER)
                _state.value = _state.value.copy(
                    saving = false,
                    error = e,
                    needsReload = uncertain,
                    changeRevision = s.changeRevision + if (uncertain) 1 else 0,
                )
            } catch (e: ServerNotFoundException) {
                _state.value = _state.value.copy(saving = false, error = e)
            } catch (e: IOException) {
                _state.value = _state.value.copy(saving = false, error = e)
            }
        }
    }

    fun requestDelete() {
        val s = _state.value
        if (s.loading || s.saving || s.form != null || s.details == null || s.needsReload || s.deleted) return
        _state.value = s.copy(confirmingDelete = true, error = null, saved = false)
    }

    fun cancelDelete() {
        if (_state.value.saving) return
        _state.value = _state.value.copy(confirmingDelete = false)
    }

    fun delete() {
        val s = _state.value
        if (!s.confirmingDelete || s.saving || s.needsReload || s.deleted) return
        val requested = identity ?: return
        _state.value = s.copy(saving = true, confirmingDelete = false, error = null, saved = false)
        job = viewModelScope.launch {
            try {
                deleter.delete(requested.first, requested.second, requested.third)
                _state.value = _state.value.copy(
                    saving = false,
                    details = null,
                    deleted = true,
                    changeRevision = s.changeRevision + 1,
                )
            } catch (e: DeleteShortUrlException) {
                val mustReload =
                    e.failure in listOf(DeleteShortUrlFailure.UNCERTAIN, DeleteShortUrlFailure.NOT_FOUND)
                _state.value = _state.value.copy(
                    saving = false,
                    error = e,
                    needsReload = mustReload,
                    changeRevision = s.changeRevision + if (mustReload) 1 else 0,
                )
            } catch (e: ServerNotFoundException) {
                _state.value = _state.value.copy(saving = false, error = e)
            } catch (e: IOException) {
                _state.value = _state.value.copy(saving = false, error = e)
            }
        }
    }
}
