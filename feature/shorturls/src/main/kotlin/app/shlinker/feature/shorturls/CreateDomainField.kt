package app.shlinker.feature.shorturls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.shlinker.core.data.ServerNotFoundException
import app.shlinker.core.data.ShortUrlApiKeyException
import app.shlinker.core.network.ConnectionError
import app.shlinker.core.network.InvalidServerDomainsException
import app.shlinker.core.network.ShortUrlRequestException

@Composable
internal fun CreateDomainField(
    state: CreateShortUrlState,
    enabled: Boolean,
    onChange: (CreateShortUrlState) -> Unit,
    onRetry: () -> Unit,
) {
    val domains = state.domains.orEmpty()
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.create_domain_label), style = MaterialTheme.typography.labelLarge)
        if (state.domainsLoading) {
            CircularProgressIndicator()
            Text(stringResource(R.string.create_domains_loading))
        } else {
            state.domainError?.let { error ->
                val message = when {
                    error is ServerNotFoundException -> R.string.create_error_server_missing
                    error is ShortUrlApiKeyException ||
                        (error is ShortUrlRequestException && error.error == ConnectionError.InvalidApiKey) ->
                        R.string.create_domains_auth
                    error is InvalidServerDomainsException -> R.string.create_domains_invalid
                    else -> R.string.create_domains_failed
                }
                Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, enabled = enabled) {
                    Text(stringResource(R.string.create_domains_retry))
                }
            }
            if (state.domains != null && domains.isEmpty()) Text(stringResource(R.string.create_domains_empty))
            if (domains.size == 1 && !state.manualDomain) {
                Text(domainLabel(domains.single().domain, domains.single().isDefault))
            } else if (domains.isNotEmpty()) {
                TextButton(onClick = { expanded = true }, enabled = enabled) {
                    Text(
                        stringResource(
                            R.string.create_domain_selected,
                            if (state.manualDomain) {
                                stringResource(R.string.create_domain_other)
                            } else {
                                domains.firstOrNull { it.domain == state.domain }?.let {
                                    domainLabel(it.domain, it.isDefault)
                                } ?: stringResource(R.string.create_domain_choose)
                            },
                        ),
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    domains.forEach { domain ->
                        DropdownMenuItem(
                            text = { Text(domainLabel(domain.domain, domain.isDefault)) },
                            onClick = {
                                onChange(state.copy(domain = domain.domain, manualDomain = false))
                                expanded = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.create_domain_other)) },
                        onClick = {
                            onChange(state.copy(domain = "", manualDomain = true))
                            expanded = false
                        },
                    )
                }
            }
            if (!state.manualDomain && domains.size <= 1) {
                TextButton(
                    onClick = { onChange(state.copy(domain = "", manualDomain = true)) },
                    enabled = enabled,
                ) { Text(stringResource(R.string.create_domain_other)) }
            }
        }
        if (state.manualDomain) {
            OutlinedTextField(
                state.domain, { onChange(state.copy(domain = it)) },
                label = { Text(stringResource(R.string.create_domain_manual)) },
                supportingText = { Text(stringResource(R.string.create_domain_hint)) },
                enabled = enabled && !state.domainsLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                isError = state.error == CreateError.DOMAIN,
            )
            if (domains.size == 1) {
                TextButton(
                    onClick = { onChange(state.copy(domain = domains.single().domain, manualDomain = false)) },
                    enabled = enabled && !state.domainsLoading,
                ) { Text(stringResource(R.string.create_domain_use, domains.single().domain)) }
            }
        }
        Text(
            stringResource(R.string.create_domains_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun domainLabel(domain: String, isDefault: Boolean): String =
    if (isDefault) stringResource(R.string.create_domain_default, domain) else domain
