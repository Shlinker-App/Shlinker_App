package at.awit.shlinker.feature.shorturls

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.awit.shlinker.core.data.RedirectRulesConflictException
import at.awit.shlinker.core.data.ServerNotFoundException
import at.awit.shlinker.core.data.ShortUrlApiKeyException
import at.awit.shlinker.core.model.RedirectCondition
import at.awit.shlinker.core.model.RedirectConditionType
import at.awit.shlinker.core.model.RedirectRule
import at.awit.shlinker.core.model.isValidDestinationUrl
import at.awit.shlinker.core.model.redirectBrowsers
import at.awit.shlinker.core.model.redirectDevices
import at.awit.shlinker.core.network.RedirectRulesException
import at.awit.shlinker.core.network.RedirectRulesFailure

@Composable
fun RedirectRulesRoute(
    serverId: String,
    shortCode: String,
    domain: String?,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    viewModel: RedirectRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(serverId, shortCode, domain) { viewModel.load(serverId, shortCode, domain) }
    LaunchedEffect(state.changeRevision) { if (state.changeRevision > 0) onChanged() }
    RedirectRulesScreen(
        state, shortCode, domain, onBack,
        { viewModel.load(serverId, shortCode, domain, refresh = true) },
        viewModel::edit, viewModel::change, viewModel::cancel,
        viewModel::requestSave, viewModel::dismissSave, viewModel::save,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RedirectRulesScreen(
    state: RedirectRulesState,
    shortCode: String,
    domain: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onChange: (List<RedirectRule>) -> Unit,
    onCancel: () -> Unit,
    onRequestSave: () -> Unit,
    onDismissSave: () -> Unit,
    onSave: () -> Unit,
) {
    var discard by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Int?>(null) }
    val editing = state.draft != null
    val locked = state.saving || state.needsReload
    val cancel = { if (state.dirty) discard = true else onCancel() }
    val back = {
        if (!state.saving) {
            if (editing && !state.needsReload) cancel() else onBack()
        }
    }
    BackHandler(enabled = editing || state.saving) { back() }
    if (discard) {
        RulesDialog(
            R.string.edit_discard_title,
            stringResource(R.string.edit_discard_hint),
            R.string.edit_discard,
            {
                discard = false
                onCancel()
            },
            { discard = false },
        )
    }
    removing?.let { index ->
        RulesDialog(
            R.string.rules_remove,
            stringResource(R.string.rules_remove_hint, index + 1),
            R.string.rules_remove,
            {
                state.draft?.let { rules -> onChange(rules.filterIndexed { i, _ -> i != index }) }
                removing = null
            },
            { removing = null },
        )
    }
    if (state.confirmSave) {
        RulesDialog(
            R.string.rules_save_title,
            stringResource(
                R.string.rules_save_warning,
                state.serverName.orEmpty(),
                shortCode,
                state.draft.orEmpty().size,
            ),
            R.string.edit_save,
            onSave,
            onDismissSave,
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_rules_action)) },
                navigationIcon = {
                    IconButton(onClick = back, enabled = !state.saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.stats_back))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.loading && !editing && !state.saving) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.short_urls_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(
                    R.string.short_urls_server,
                    state.serverName ?: stringResource(R.string.stats_server_loading),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.stats_code, shortCode))
            Text(stringResource(R.string.stats_domain, domain ?: stringResource(R.string.details_default_domain)))
            Text(stringResource(R.string.rules_explanation))
            if (state.loading || state.saving) CircularProgressIndicator()
            if (state.saved) {
                val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                Surface(
                    color = if (dark) Color(0xFF163922) else Color(0xFFD5E8D4),
                    contentColor = if (dark) Color(0xFF9CE5A9) else Color(0xFF125322),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Text(stringResource(R.string.rules_saved))
                    }
                }
            }
            state.error?.let { error ->
                Text(rulesError(error), color = MaterialTheme.colorScheme.error)
                if (!editing || state.needsReload) {
                    TextButton(onClick = onRefresh, enabled = !state.loading && !state.saving) {
                        Text(
                            stringResource(if (state.needsReload) R.string.rules_reload else R.string.short_urls_retry),
                        )
                    }
                }
            }
            state.original?.let { original ->
                Text(stringResource(R.string.rules_default), style = MaterialTheme.typography.titleMedium)
                Text(original.defaultLongUrl)
                if (original.hasUnknownConditions) {
                    Text(stringResource(R.string.rules_unknown), color = MaterialTheme.colorScheme.error)
                }
                if (editing) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.rules_editing),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                val rules = state.draft ?: original.rules
                if (rules.isEmpty()) Text(stringResource(R.string.rules_empty))
                rules.forEachIndexed { index, rule ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.rules_number, index + 1),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (editing) {
                                fun update(value: RedirectRule) = onChange(
                                    rules.toMutableList().also {
                                        it[index] =
                                            value
                                    },
                                )
                                OutlinedTextField(
                                    rule.longUrl,
                                    { update(rule.copy(longUrl = it)) },
                                    Modifier.fillMaxWidth(),
                                    enabled = !locked,
                                    label = { Text(stringResource(R.string.create_url)) },
                                    isError = state.validation && !isValidDestinationUrl(rule.longUrl),
                                    singleLine = true,
                                )
                                if (rule.conditions.isEmpty()) Text(stringResource(R.string.rules_unconditional))
                                rule.conditions.forEachIndexed { cIndex, condition ->
                                    ConditionEditor(
                                        condition,
                                        cIndex,
                                        !locked,
                                        state.validation,
                                        { value ->
                                            update(
                                                rule.copy(
                                                    conditions = rule.conditions.toMutableList().also {
                                                        it[cIndex] =
                                                            value
                                                    },
                                                ),
                                            )
                                        },
                                        {
                                            update(
                                                rule.copy(
                                                    conditions = rule.conditions.filterIndexed { i, _ ->
                                                        i !=
                                                            cIndex
                                                    },
                                                ),
                                            )
                                        },
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        update(
                                            rule.copy(
                                                conditions =
                                                rule.conditions + RedirectCondition("device", null, "android"),
                                            ),
                                        )
                                    },
                                    enabled = !locked,
                                ) { Text(stringResource(R.string.rules_add_condition)) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for ((delta, label) in listOf(-1 to R.string.rules_up, 1 to R.string.rules_down)) {
                                        TextButton(
                                            onClick = {
                                                onChange(
                                                    rules.toMutableList().also {
                                                        val other = index + delta
                                                        it[index] = it[other]
                                                        it[other] = rule
                                                    },
                                                )
                                            },
                                            enabled = !locked && index + delta in rules.indices,
                                        ) { Text(stringResource(label)) }
                                    }
                                    TextButton(onClick = { removing = index }, enabled = !locked) {
                                        Text(
                                            stringResource(R.string.rules_remove),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            } else {
                                Text(rule.longUrl)
                                if (rule.conditions.isEmpty()) Text(stringResource(R.string.rules_unconditional))
                                rule.conditions.forEach { condition ->
                                    Text(
                                        listOfNotNull(
                                            condition.knownType?.let { stringResource(conditionLabel(it)) }
                                                ?: condition.type,
                                            condition.matchKey?.let { stringResource(R.string.rules_key_value, it) },
                                            condition.matchValue?.let {
                                                stringResource(R.string.rules_match_value, it)
                                            },
                                        ).joinToString(" · "),
                                    )
                                }
                            }
                        }
                    }
                }
                if (editing) {
                    if (state.validation) {
                        Text(
                            stringResource(R.string.rules_validation),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(
                        onClick = {
                            onChange(
                                rules + RedirectRule("", listOf(RedirectCondition("device", null, "android"))),
                            )
                        },
                        enabled = !locked,
                    ) { Text(stringResource(R.string.rules_add)) }
                    Button(onClick = onRequestSave, enabled = !locked && state.dirty) {
                        Text(stringResource(R.string.edit_save))
                    }
                    TextButton(onClick = cancel, enabled = !locked) { Text(stringResource(R.string.edit_cancel)) }
                } else {
                    Button(onClick = onEdit, enabled = !locked && !original.hasUnknownConditions) {
                        Text(stringResource(R.string.rules_edit))
                    }
                }
            }
        }
    }
}

@Composable
private fun RulesDialog(title: Int, text: String, confirm: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.55f }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(text, Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_picker_cancel)) } },
    )
}

@Composable
private fun ConditionEditor(
    condition: RedirectCondition,
    index: Int,
    enabled: Boolean,
    validation: Boolean,
    onChange: (RedirectCondition) -> Unit,
    onRemove: () -> Unit,
) {
    val kind = condition.knownType ?: return
    var types by remember { mutableStateOf(false) }
    Text(stringResource(R.string.rules_condition_number, index + 1), style = MaterialTheme.typography.labelLarge)
    Column {
        TextButton(onClick = { types = true }, enabled = enabled) {
            Text(stringResource(R.string.rules_condition_type, stringResource(conditionLabel(kind))))
        }
        DropdownMenu(expanded = types, onDismissRequest = { types = false }) {
            RedirectConditionType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(conditionLabel(type))) },
                    onClick = {
                        val defaultValue = when (type) {
                            RedirectConditionType.DEVICE -> "android"
                            RedirectConditionType.BROWSER -> "chrome"
                            else -> if (type.hasValue) "" else null
                        }
                        onChange(RedirectCondition(type.apiValue, if (type.hasKey) "" else null, defaultValue))
                        types = false
                    },
                )
            }
        }
    }
    Text(stringResource(conditionHint(kind)), style = MaterialTheme.typography.bodySmall)
    if (kind.hasKey) {
        OutlinedTextField(
            condition.matchKey.orEmpty(),
            { onChange(condition.copy(matchKey = it)) },
            Modifier.fillMaxWidth(),
            enabled = enabled,
            label = { Text(stringResource(R.string.rules_key)) },
            isError = validation && condition.matchKey.isNullOrBlank(),
            singleLine = true,
        )
    }
    when (kind) {
        RedirectConditionType.DEVICE, RedirectConditionType.BROWSER -> {
            var choices by remember { mutableStateOf(false) }
            Column {
                TextButton(onClick = { choices = true }, enabled = enabled) {
                    Text(stringResource(R.string.rules_match_value, condition.matchValue.orEmpty()))
                }
                DropdownMenu(expanded = choices, onDismissRequest = { choices = false }) {
                    (if (kind == RedirectConditionType.DEVICE) redirectDevices else redirectBrowsers).forEach { value ->
                        DropdownMenuItem(
                            text = { Text(value) },
                            onClick = {
                                onChange(condition.copy(matchValue = value))
                                choices = false
                            },
                        )
                    }
                }
            }
        }
        RedirectConditionType.BEFORE, RedirectConditionType.AFTER -> ShlinkDateTimeField(
            conditionLabel(kind),
            condition.matchValue.orEmpty(),
            enabled,
            validation && !condition.isValid,
        ) { onChange(condition.copy(matchValue = it)) }
        else -> if (kind.hasValue) {
            OutlinedTextField(
                condition.matchValue.orEmpty(),
                { onChange(condition.copy(matchValue = it)) },
                Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(stringResource(R.string.rules_value)) },
                isError = validation && !condition.isValid,
                singleLine = true,
            )
        }
    }
    TextButton(onClick = onRemove, enabled = enabled) { Text(stringResource(R.string.rules_remove_condition)) }
}

private fun conditionLabel(type: RedirectConditionType): Int = when (type) {
    RedirectConditionType.DEVICE -> R.string.rules_device
    RedirectConditionType.LANGUAGE -> R.string.rules_language
    RedirectConditionType.QUERY_PARAM -> R.string.rules_query
    RedirectConditionType.ANY_VALUE_QUERY_PARAM -> R.string.rules_query_any
    RedirectConditionType.VALUELESS_QUERY_PARAM -> R.string.rules_query_empty
    RedirectConditionType.IP_ADDRESS -> R.string.rules_ip
    RedirectConditionType.COUNTRY -> R.string.rules_country
    RedirectConditionType.CITY -> R.string.rules_city
    RedirectConditionType.BEFORE -> R.string.rules_before
    RedirectConditionType.AFTER -> R.string.rules_after
    RedirectConditionType.BROWSER -> R.string.rules_browser
}

private fun conditionHint(type: RedirectConditionType): Int = when (type) {
    RedirectConditionType.DEVICE -> R.string.rules_device_hint
    RedirectConditionType.LANGUAGE -> R.string.rules_language_hint
    RedirectConditionType.QUERY_PARAM -> R.string.rules_query_hint
    RedirectConditionType.ANY_VALUE_QUERY_PARAM -> R.string.rules_query_any_hint
    RedirectConditionType.VALUELESS_QUERY_PARAM -> R.string.rules_query_empty_hint
    RedirectConditionType.IP_ADDRESS -> R.string.rules_ip_hint
    RedirectConditionType.COUNTRY -> R.string.rules_country_hint
    RedirectConditionType.CITY -> R.string.rules_city_hint
    RedirectConditionType.BEFORE, RedirectConditionType.AFTER -> R.string.rules_date_hint
    RedirectConditionType.BROWSER -> R.string.rules_browser_hint
}

@Composable
private fun rulesError(error: Exception): String = stringResource(
    when (error) {
        is RedirectRulesConflictException -> R.string.rules_conflict
        is ShortUrlApiKeyException -> R.string.rules_key_missing
        is ServerNotFoundException -> R.string.rules_profile_missing
        is RedirectRulesException -> when (error.failure) {
            RedirectRulesFailure.INVALID_INPUT -> R.string.rules_invalid_server
            RedirectRulesFailure.UNAUTHORIZED -> R.string.rules_unauthorized
            RedirectRulesFailure.NOT_FOUND -> R.string.rules_not_found
            RedirectRulesFailure.UNSUPPORTED -> R.string.rules_unsupported
            RedirectRulesFailure.INVALID_RESPONSE -> R.string.rules_invalid_response
            RedirectRulesFailure.UNCERTAIN -> R.string.rules_uncertain
            RedirectRulesFailure.SERVER -> R.string.rules_server
        }
        else -> R.string.rules_connection
    },
)
