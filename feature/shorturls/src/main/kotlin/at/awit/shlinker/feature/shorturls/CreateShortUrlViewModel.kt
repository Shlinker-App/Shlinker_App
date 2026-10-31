package at.awit.shlinker.feature.shorturls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.awit.shlinker.core.data.AppTagSettingsRepository
import at.awit.shlinker.core.data.ServerDomainsRepository
import at.awit.shlinker.core.data.ServerNotFoundException
import at.awit.shlinker.core.data.ServerProfileRepository
import at.awit.shlinker.core.data.ShortUrlApiKeyException
import at.awit.shlinker.core.data.ShortUrlCreator
import at.awit.shlinker.core.model.AppTagSettings
import at.awit.shlinker.core.model.CreateShortUrl
import at.awit.shlinker.core.model.ServerDomain
import at.awit.shlinker.core.model.ShlinkDefaults
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.model.isValidDestinationUrl
import at.awit.shlinker.core.model.isValidShlinkDomain
import at.awit.shlinker.core.model.parseTagInput
import at.awit.shlinker.core.network.CreateShortUrlException
import at.awit.shlinker.core.network.CreateShortUrlFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class CreateShortUrlState(
    val serverName: String? = null,
    val longUrl: String = "",
    val title: String = "",
    val slug: String = "",
    val domain: String = "",
    val domains: List<ServerDomain>? = null,
    val domainsLoading: Boolean = false,
    val domainError: Exception? = null,
    val manualDomain: Boolean = false,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val appTag: AppTagSettings = AppTagSettings(),
    val defaults: ShlinkDefaults = ShlinkDefaults(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: CreateError? = null,
    val created: ShortUrl? = null,
) {
    val domainReady: Boolean
        get() = !domainsLoading &&
            domain.isNotBlank() &&
            (manualDomain || domains?.any { it.domain == domain } == true)
}

enum class CreateError { URL, DOMAIN, LOAD, MISSING_SERVER, MISSING_KEY, INVALID, CONFLICT, AUTH, SERVER, UNCERTAIN }

@HiltViewModel
class CreateShortUrlViewModel @Inject constructor(
    private val profiles: ServerProfileRepository,
    private val creator: ShortUrlCreator,
    private val appTags: AppTagSettingsRepository,
    private val domainsRepository: ServerDomainsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CreateShortUrlState())
    val state = _state.asStateFlow()
    private var serverId: String? = null
    private var loadJob: Job? = null
    private var domainsJob: Job? = null

    fun load(id: String) {
        if (_state.value.busy) return
        if (serverId == id && !_state.value.loading && _state.value.serverName != null) return
        loadJob?.cancel()
        domainsJob?.cancel()
        val sameServer = serverId == id
        serverId = id
        _state.value = if (sameServer) {
            _state.value.copy(loading = true, error = null, domainsLoading = false)
        } else {
            CreateShortUrlState()
        }
        loadDomains()
        loadJob = viewModelScope.launch {
            try {
                val server = profiles.profiles.first().firstOrNull { it.id == id }
                val preferences = appTags.preferences.first()
                _state.value = _state.value.copy(
                    loading = false,
                    serverName = server?.name,
                    appTag = preferences.appTag,
                    defaults = preferences.shlinkDefaults,
                    error = if (server == null) CreateError.MISSING_SERVER else null,
                )
            } catch (_: IOException) {
                _state.value = _state.value.copy(loading = false, error = CreateError.LOAD)
            }
        }
    }

    fun loadDomains() {
        val id = serverId ?: return
        val s = _state.value
        if (s.busy || s.created != null || s.domainsLoading) return
        _state.value = s.copy(domainsLoading = true, domainError = null)
        domainsJob = viewModelScope.launch {
            try {
                val domains = domainsRepository.get(id)
                val current = _state.value
                val selected = when {
                    current.manualDomain -> current.domain
                    domains.any { it.domain == current.domain } -> current.domain
                    domains.size == 1 -> domains.single().domain
                    else -> domains.firstOrNull { it.isDefault }?.domain.orEmpty()
                }
                _state.value = current.copy(
                    domains = domains,
                    domainsLoading = false,
                    domainError = null,
                    domain = selected,
                )
            } catch (e: ServerNotFoundException) {
                _state.value = _state.value.copy(domainsLoading = false, domains = null, domainError = e)
            } catch (e: IOException) {
                _state.value = _state.value.copy(domainsLoading = false, domains = null, domainError = e)
            }
        }
    }

    fun change(value: CreateShortUrlState) {
        if (!_state.value.busy && _state.value.created == null) {
            _state.value = value.copy(error = _state.value.error?.takeIf { it == CreateError.UNCERTAIN })
        }
    }

    fun submit() {
        val s = _state.value
        val id = serverId ?: return
        if (s.busy || s.loading || s.serverName == null || s.created != null || s.error == CreateError.UNCERTAIN) return
        val url = s.longUrl.trim()
        if (!isValidDestinationUrl(url)) {
            _state.value = s.copy(error = CreateError.URL)
            return
        }
        val domain = s.domain.trim()
        if (!s.domainReady || !isValidShlinkDomain(domain)) {
            _state.value = s.copy(error = CreateError.DOMAIN)
            return
        }
        _state.value = s.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val created = creator.create(
                    id,
                    CreateShortUrl(
                        url,
                        s.title.trim().ifEmpty { null },
                        s.slug.trim().ifEmpty { null },
                        domain.takeUnless {
                            !s.manualDomain &&
                                s.domains?.any { it.domain == domain && it.isDefault } == true
                        },
                        (s.tags + parseTagInput(s.tagInput)).distinct(),
                    ),
                )
                _state.value = _state.value.copy(busy = false, created = created)
            } catch (e: CreateShortUrlException) {
                val error = when (e.failure) {
                    CreateShortUrlFailure.INVALID_INPUT -> CreateError.INVALID
                    CreateShortUrlFailure.SLUG_CONFLICT -> CreateError.CONFLICT
                    CreateShortUrlFailure.UNAUTHORIZED -> CreateError.AUTH
                    CreateShortUrlFailure.SERVER -> CreateError.SERVER
                    CreateShortUrlFailure.UNCERTAIN -> CreateError.UNCERTAIN
                }
                _state.value = _state.value.copy(busy = false, error = error)
            } catch (_: ServerNotFoundException) {
                _state.value = _state.value.copy(busy = false, error = CreateError.MISSING_SERVER)
            } catch (_: ShortUrlApiKeyException) {
                _state.value = _state.value.copy(busy = false, error = CreateError.MISSING_KEY)
            } catch (_: IOException) {
                _state.value = _state.value.copy(busy = false, error = CreateError.LOAD)
            }
        }
    }
}
