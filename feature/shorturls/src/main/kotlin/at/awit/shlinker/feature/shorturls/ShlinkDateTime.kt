package at.awit.shlinker.feature.shorturls

import at.awit.shlinker.core.model.parseShlinkDate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

internal fun selectedShlinkDate(
    dateMillis: Long,
    hour: Int,
    minute: Int,
    zone: ZoneId,
    original: String,
    preserveTimePrecision: Boolean = false,
): String? {
    // Material's calendar encodes the selected calendar date at UTC midnight, not local midnight.
    val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val previous = parseShlinkDate(original)
    val previousLocal = previous?.atZoneSameInstant(zone)
    val time = if (preserveTimePrecision && previousLocal != null) {
        previousLocal.toLocalTime()
    } else {
        java.time.LocalTime.of(hour, minute)
    }
    val local = LocalDateTime.of(date, time)
    val offsets = zone.rules.getValidOffsets(local)
    if (offsets.isEmpty()) return null
    if (previousLocal?.toLocalDate() == date && previousLocal.hour == hour && previousLocal.minute == minute) {
        return original
    }
    val offset = previousLocal?.offset?.takeIf { it in offsets } ?: offsets.first()
    return local.atOffset(offset).toString()
}
