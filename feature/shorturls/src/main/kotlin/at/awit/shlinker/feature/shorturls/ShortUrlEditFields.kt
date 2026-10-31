package at.awit.shlinker.feature.shorturls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ShortUrlEditFields(
    form: ShortUrlEditForm,
    enabled: Boolean,
    validation: EditValidation?,
    onChange: (ShortUrlEditForm) -> Unit,
) {
    Text(stringResource(R.string.edit_intro), style = MaterialTheme.typography.bodyMedium)
    EditField(R.string.create_url, form.longUrl, enabled, validation == EditValidation.URL, KeyboardType.Uri) {
        onChange(form.copy(longUrl = it))
    }
    EditField(R.string.create_label_title, form.title, enabled) { onChange(form.copy(title = it)) }
    TagInput(form.tags, form.tagInput, enabled) { tags, input -> onChange(form.copy(tags = tags, tagInput = input)) }
    Text(stringResource(R.string.details_limits), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.edit_dates_hint))
    ShlinkDateTimeField(
        R.string.details_valid_since,
        form.validSince,
        enabled,
        validation in listOf(EditValidation.SINCE, EditValidation.SINCE_RANGE),
    ) {
        onChange(form.copy(validSince = it))
    }
    ShlinkDateTimeField(
        R.string.details_valid_until,
        form.validUntil,
        enabled,
        validation in listOf(EditValidation.UNTIL, EditValidation.UNTIL_RANGE),
    ) {
        onChange(form.copy(validUntil = it))
    }
    Text(stringResource(R.string.edit_limit_hint))
    EditField(
        R.string.details_max_visits,
        form.maxVisits,
        enabled,
        validation == EditValidation.LIMIT,
        KeyboardType.Number,
    ) { onChange(form.copy(maxVisits = it)) }
    Text(stringResource(R.string.details_behavior), style = MaterialTheme.typography.titleMedium)
    EditBoolean(R.string.details_crawlable, form.crawlable, enabled) { onChange(form.copy(crawlable = it)) }
    Text(stringResource(R.string.edit_crawlable_hint))
    EditBoolean(R.string.details_forward_query, form.forwardQuery, enabled) { onChange(form.copy(forwardQuery = it)) }
    Text(stringResource(R.string.edit_forward_hint))
}

@Composable
private fun EditField(
    label: Int,
    value: String,
    enabled: Boolean,
    error: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(stringResource(label)) },
        enabled = enabled,
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EditBoolean(label: Int, value: Boolean?, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        if (value == null) Text(stringResource(R.string.edit_unknown_boolean))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false, true).forEach { option ->
                FilterChip(
                    selected = value == option,
                    onClick = { onChange(option) },
                    enabled = enabled,
                    label = { Text(stringResource(if (option) R.string.details_yes else R.string.details_no)) },
                )
            }
        }
    }
}
