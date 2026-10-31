package at.awit.shlinker.feature.shorturls

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import at.awit.shlinker.core.model.parseShlinkDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShlinkDateTimeField(
    label: Int,
    value: String,
    enabled: Boolean,
    error: Boolean,
    onChange: (String) -> Unit,
) {
    val name = stringResource(label)
    val zone = ZoneId.systemDefault()
    val parsed = parseShlinkDate(value)?.atZoneSameInstant(zone)
    var stage by rememberSaveable { mutableStateOf(0) }
    val invalidDate = stringResource(R.string.edit_error_dates)
    Column {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { if (error) error(invalidDate) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val dateLabel = stringResource(R.string.edit_choose_date, name)
            TextButton(
                onClick = { stage = 1 },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = dateLabel },
            ) {
                Text(
                    parsed?.format(DateTimeFormatter.ofPattern("yyyy / MM / dd", Locale.ENGLISH))
                        ?: stringResource(R.string.edit_date_placeholder),
                )
            }
            Text("-", modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
            val timeLabel = stringResource(R.string.edit_choose_time, name)
            TextButton(
                onClick = { stage = 2 },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = timeLabel },
            ) {
                Text(
                    parsed?.format(DateTimeFormatter.ofPattern("HH:mm z", Locale.ENGLISH))
                        ?: stringResource(R.string.edit_time_placeholder),
                )
            }
            if (value.isNotEmpty()) {
                TextButton(onClick = { onChange("") }, enabled = enabled) {
                    Text(stringResource(R.string.edit_clear_date, name))
                }
            }
        }
        Text(stringResource(R.string.edit_date_zone, zone.id), style = MaterialTheme.typography.bodySmall)
        if (value.isNotEmpty() && parsed == null) {
            Text(value, color = MaterialTheme.colorScheme.error)
        }
    }
    if (stage == 1) {
        val initial = parsed ?: ZonedDateTime.now(zone)
        var invalidTime by rememberSaveable { mutableStateOf(false) }
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { stage = 0 },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = selectedShlinkDate(
                            requireNotNull(picker.selectedDateMillis),
                            parsed?.hour ?: 0,
                            parsed?.minute ?: 0,
                            zone,
                            value,
                            preserveTimePrecision = true,
                        )
                        if (selected == null) {
                            invalidTime = true
                        } else {
                            onChange(selected)
                            stage = 0
                        }
                    },
                    enabled = picker.selectedDateMillis != null,
                ) { Text(stringResource(R.string.edit_picker_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { stage = 0 }) { Text(stringResource(R.string.edit_picker_cancel)) }
            },
        ) {
            Column(
                Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.7f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(name)
                DatePicker(state = picker)
                if (invalidTime) {
                    Text(stringResource(R.string.edit_date_gap), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (stage == 2) {
        val initial = parsed ?: ZonedDateTime.now(zone)
        val time = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        var invalidTime by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { stage = 0 },
            modifier = Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth(),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text(name) },
            text = {
                Column(
                    Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.6f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(stringResource(R.string.edit_date_zone, zone.id))
                    TimePicker(state = time)
                    if (invalidTime) {
                        Text(stringResource(R.string.edit_date_gap), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dateMillis = initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    val selected = selectedShlinkDate(dateMillis, time.hour, time.minute, zone, value)
                    if (selected == null) {
                        invalidTime = true
                    } else {
                        onChange(selected)
                        stage = 0
                    }
                }) { Text(stringResource(R.string.edit_picker_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { stage = 0 }) { Text(stringResource(R.string.edit_picker_cancel)) }
            },
        )
    }
}
