package at.awit.shlinker.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.awit.shlinker.R
import at.awit.shlinker.core.model.AppTagSettings

@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.saving) {}
    SettingsScreen(state, viewModel::change, viewModel::save, viewModel::load, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsState,
    onChange: (AppTagSettings) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
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
            if (state.loading) {
                CircularProgressIndicator()
            } else if (state.loadFailed) {
                Text(stringResource(R.string.settings_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.settings_retry)) }
            } else {
                Text(stringResource(R.string.settings_tag_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.settings_tag_intro))
                val enabled = !state.saving
                val label = stringResource(R.string.settings_tag_enabled)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f))
                    Switch(
                        checked = state.appTag.enabled,
                        onCheckedChange = { onChange(state.appTag.copy(enabled = it)) },
                        enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
                OutlinedTextField(
                    value = state.appTag.name,
                    onValueChange = { onChange(state.appTag.copy(name = it)) },
                    label = { Text(stringResource(R.string.settings_tag_name)) },
                    supportingText = { Text(stringResource(R.string.settings_tag_hint)) },
                    singleLine = true,
                    enabled = enabled,
                    isError = state.invalidName,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.invalidName) {
                    Text(stringResource(R.string.settings_tag_invalid), color = MaterialTheme.colorScheme.error)
                }
                if (state.saveFailed) {
                    Text(stringResource(R.string.settings_save_error), color = MaterialTheme.colorScheme.error)
                }
                if (state.saved) Text(stringResource(R.string.settings_saved))
                if (state.saving) CircularProgressIndicator()
                Button(onClick = onSave, enabled = enabled) { Text(stringResource(R.string.home_save)) }
            }
        }
    }
}
