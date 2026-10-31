package at.awit.shlinker.feature.shorturls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.awit.shlinker.core.model.VisitTimelineBucket
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun VisitTimelineChart(buckets: List<VisitTimelineBucket>, zone: ZoneId) {
    var selectedIndex by rememberSaveable(buckets) { mutableIntStateOf(buckets.lastIndex) }
    val primary = MaterialTheme.colorScheme.primary
    val bots = MaterialTheme.colorScheme.tertiary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val maximum = buckets.maxOf { it.counts.total }
    val format = DateTimeFormatter.ofPattern("yyyy / MM / dd HH:mm:ss z", Locale.ENGLISH).withZone(zone)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.stats_timeline_legend), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(16.dp).background(primary))
            Text(stringResource(R.string.details_non_bot_visits), style = MaterialTheme.typography.labelMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(16.dp).background(bots))
            Text(stringResource(R.string.stats_bots), style = MaterialTheme.typography.labelMedium)
        }
        Text(stringResource(R.string.stats_timeline_scale, maximum), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            buckets.forEachIndexed { index, bucket ->
                val description = stringResource(
                    R.string.stats_timeline_bar,
                    index + 1,
                    bucket.counts.nonBots,
                    bucket.counts.bots,
                )
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier.fillMaxWidth().height(160.dp)
                            .semantics {
                                contentDescription = description
                                selected = selectedIndex == index
                            }
                            .clickable(role = Role.Button, onClickLabel = description) { selectedIndex = index },
                    ) {
                        Canvas(Modifier.matchParentSize().padding(horizontal = 1.dp)) {
                            val nonBotHeight = if (maximum == 0L) {
                                0f
                            } else {
                                (bucket.counts.nonBots.toDouble() / maximum * size.height).toFloat()
                            }
                            val botHeight = if (maximum == 0L) {
                                0f
                            } else {
                                (bucket.counts.bots.toDouble() / maximum * size.height).toFloat()
                            }
                            drawRect(primary, Offset(0f, size.height - nonBotHeight), Size(size.width, nonBotHeight))
                            drawRect(
                                bots,
                                Offset(0f, size.height - nonBotHeight - botHeight),
                                Size(size.width, botHeight),
                            )
                            drawLine(
                                if (selectedIndex == index) primary else baseline,
                                Offset(0f, size.height - 1.dp.toPx()),
                                Offset(size.width, size.height - 1.dp.toPx()),
                                strokeWidth = if (selectedIndex == index) 3.dp.toPx() else 1.dp.toPx(),
                            )
                        }
                    }
                    Text((index + 1).toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        val shortFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.ENGLISH).withZone(zone)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                buckets.first().since?.let(shortFormat::format) ?: stringResource(R.string.stats_timeline_beginning),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                shortFormat.format(buckets.last().until),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        Text(stringResource(R.string.stats_timeline_order), style = MaterialTheme.typography.bodySmall)
        val bucket = buckets[selectedIndex]
        Text(
            stringResource(
                R.string.stats_timeline_range,
                selectedIndex + 1,
                if (bucket.sinceExclusive) {
                    stringResource(R.string.stats_timeline_after)
                } else {
                    stringResource(R.string.stats_timeline_from)
                },
                bucket.since?.let(format::format) ?: stringResource(R.string.stats_beginning),
                format.format(bucket.until),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(
                R.string.stats_timeline_values,
                bucket.counts.total,
                bucket.counts.nonBots,
                bucket.counts.bots,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
