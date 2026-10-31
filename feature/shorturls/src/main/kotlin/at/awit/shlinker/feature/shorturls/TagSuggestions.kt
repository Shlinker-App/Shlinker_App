package at.awit.shlinker.feature.shorturls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal fun matchingTagSuggestions(available: List<String>, selected: List<String>, input: String): List<String> =
    available.filter { it !in selected && it.contains(input.trim(), ignoreCase = true) }.take(8)

@Composable
internal fun TagSuggestions(
    state: TagSuggestionsState,
    tags: List<String>,
    input: String,
    enabled: Boolean,
    onRetry: () -> Unit,
    onAdd: (String) -> Unit,
) {
    Column {
        Text(stringResource(R.string.tags_suggestions), style = MaterialTheme.typography.labelLarge)
        when {
            state.loading -> Text(stringResource(R.string.tags_loading))
            state.error != null -> {
                Text(stringResource(R.string.tags_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, enabled = enabled) { Text(stringResource(R.string.tags_retry)) }
            }
            else -> {
                val suggestions = matchingTagSuggestions(state.tags, tags, input)
                if (state.tags.isEmpty()) {
                    Text(stringResource(R.string.tags_empty))
                } else if (suggestions.isEmpty()) {
                    Text(stringResource(R.string.tags_no_matches))
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { tag ->
                            val label = stringResource(R.string.tags_add, tag)
                            AssistChip(
                                onClick = { onAdd(tag) },
                                label = { Text(tag) },
                                enabled = enabled,
                                modifier = Modifier.semantics { contentDescription = label },
                            )
                        }
                    }
                    Text(stringResource(R.string.tags_filter_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
