package at.awit.shlinker.feature.shorturls

import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.model.ShortUrlDetails
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShlinkDateTimeTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private val original = ShortUrlDetails(
        ShortUrl("abc", "https://s.example/abc", "https://example.org", null, emptyList()),
        validSince = "2026-11-01T10:00:00Z",
        validUntil = "2026-12-01T10:00:00Z",
    )
    private val form = ShortUrlEditForm.from(original)

    private fun date(value: String) = LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun calendarDatesAreNotShiftedByDeviceOffsetAndIncludeCorrectDstOffset() {
        assertEquals("2026-07-01T09:30+02:00", selectedShlinkDate(date("2026-07-01"), 9, 30, zone, ""))
        assertEquals("2026-12-01T09:30+01:00", selectedShlinkDate(date("2026-12-01"), 9, 30, zone, ""))
        assertEquals(
            "2026-07-01T00:15-07:00",
            selectedShlinkDate(date("2026-07-01"), 0, 15, ZoneId.of("America/Los_Angeles"), ""),
        )
    }

    @Test fun selectingSameDateAndMinutePreservesOriginalOffsetSecondsAndExactServerText() {
        val original = "2026-07-01T07:30:45.123Z"
        assertEquals(original, selectedShlinkDate(date("2026-07-01"), 9, 30, zone, original))
    }

    @Test fun dstGapIsRejectedAndOverlapKeepsOriginalOffsetWhenAvailable() {
        assertNull(selectedShlinkDate(date("2026-03-29"), 2, 30, zone, ""))
        assertEquals(
            "2026-10-25T02:45+01:00",
            selectedShlinkDate(date("2026-10-25"), 2, 45, zone, "2026-10-25T02:30:00+01:00"),
        )
        assertEquals("2026-10-25T02:30+02:00", selectedShlinkDate(date("2026-10-25"), 2, 30, zone, ""))
    }

    @Test fun onlyBrokenOrAdjustedBoundaryIsFlaggedAndRangeUsesInstantNotClockTime() {
        assertEquals(EditValidation.SINCE, form.copy(validSince = "incomplete").dateValidation(original))
        assertEquals(EditValidation.UNTIL, form.copy(validUntil = "incomplete").dateValidation(original))
        assertEquals(
            EditValidation.SINCE_RANGE,
            form.copy(validSince = "2026-12-02T10:00:00Z").dateValidation(original),
        )
        assertEquals(
            EditValidation.UNTIL_RANGE,
            form.copy(validUntil = "2026-10-31T10:00:00Z").dateValidation(original),
        )
        assertNull(form.copy(validSince = "", validUntil = "").dateValidation(original))
        assertNull(
            form.copy(
                validSince = "2026-10-25T02:30:00+02:00",
                validUntil = "2026-10-25T02:15:00+01:00",
            ).dateValidation(original),
        )
    }

    @Test fun dirtyCheckIncludesAllFieldsPendingInputAndReversionWithoutMistakingMissingTitleForEdit() {
        assertFalse(form.hasChanges(original))
        val changes = listOf(
            form.copy(longUrl = "https://other.example"),
            form.copy(title = "Title"),
            form.copy(tags = listOf("one")),
            form.copy(tagInput = "unfinished"),
            form.copy(validSince = "invalid"),
            form.copy(validUntil = ""),
            form.copy(maxVisits = "invalid"),
            form.copy(crawlable = true),
            form.copy(forwardQuery = false),
        )
        changes.forEach { assertTrue(it.hasChanges(original)) }
        assertFalse(form.copy(title = "Draft").copy(title = "").hasChanges(original))
    }
}
