package at.awit.shlinker.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedirectRulesTest {
    @Test fun allDocumentedTypesHaveAppropriateKeyValueValidation() {
        for (type in RedirectConditionType.entries) {
            val value = when (type) {
                RedirectConditionType.DEVICE -> "android"
                RedirectConditionType.BROWSER -> "firefox"
                RedirectConditionType.COUNTRY -> "AT"
                RedirectConditionType.BEFORE, RedirectConditionType.AFTER -> "2026-11-01T10:00:00+01:00"
                else -> if (type.hasValue) "example" else null
            }
            val condition = RedirectCondition(type.apiValue, if (type.hasKey) "campaign" else null, value)
            assertTrue(type.name, condition.isValid)
            if (type.hasKey) assertFalse(condition.copy(matchKey = "").isValid)
            if (type.hasValue) assertFalse(condition.copy(matchValue = "").isValid)
        }
    }

    @Test fun invalidDatesCountryDeviceBrowserAndDestinationAreNotAccepted() {
        for ((type, value) in listOf(
            "device" to "nonsense",
            "browser" to "nonsense",
            "geolocation-country-code" to "at",
            "geolocation-country-code" to "XX",
            "before-date" to "2026-11-01",
            "after-date" to "not-a-date",
        )) {
            assertFalse(RedirectCondition(type, null, value).isValid)
        }
        assertFalse(RedirectRule("https://user:pass@example.com", emptyList()).isValid)
        assertTrue(RedirectRule("https://example.com", emptyList()).isValid)
        assertTrue(RedirectRules("https://example.com", emptyList()).rules.isEmpty())
    }

    @Test fun unknownTypesArePreservedForReadOnlyDisplayNotSilentlyDropped() {
        val unknown = RedirectCondition("future-type", "key", "value")
        val rules = RedirectRules("https://example.com", listOf(RedirectRule("https://other.example", listOf(unknown))))
        assertTrue(rules.hasUnknownConditions)
        assertFalse(rules.rules.single().isValid)
        assertTrue(rules.rules.single().conditions.single() == unknown)
    }
}
