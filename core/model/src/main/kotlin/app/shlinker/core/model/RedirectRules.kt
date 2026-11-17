package app.shlinker.core.model

import java.util.Locale

enum class RedirectConditionType(val apiValue: String, val hasKey: Boolean = false, val hasValue: Boolean = true) {
    DEVICE("device"),
    LANGUAGE("language"),
    QUERY_PARAM("query-param", hasKey = true),
    ANY_VALUE_QUERY_PARAM("any-value-query-param", hasKey = true, hasValue = false),
    VALUELESS_QUERY_PARAM("valueless-query-param", hasKey = true, hasValue = false),
    IP_ADDRESS("ip-address"),
    COUNTRY("geolocation-country-code"),
    CITY("geolocation-city-name"),
    BEFORE("before-date"),
    AFTER("after-date"),
    BROWSER("browser"),
}

val redirectDevices = listOf("android", "ios", "mobile", "windows", "macos", "linux", "chromeos", "desktop")
val redirectBrowsers = listOf("chrome", "firefox", "edge", "safari", "opera", "android_browser")

data class RedirectCondition(val type: String, val matchKey: String?, val matchValue: String?) {
    val knownType: RedirectConditionType? get() = RedirectConditionType.entries.firstOrNull { it.apiValue == type }
    val isValid: Boolean
        get() {
            val kind = knownType ?: return false
            if (kind.hasKey && matchKey.isNullOrBlank()) return false
            if (!kind.hasValue) return true
            val value = matchValue?.takeIf { it.isNotBlank() } ?: return false
            return when (kind) {
                RedirectConditionType.DEVICE -> value in redirectDevices
                RedirectConditionType.BROWSER -> value in redirectBrowsers
                RedirectConditionType.COUNTRY -> value in Locale.getISOCountries().toSet()
                RedirectConditionType.BEFORE, RedirectConditionType.AFTER -> parseShlinkDate(value) != null
                else -> true
            }
        }
}

data class RedirectRule(val longUrl: String, val conditions: List<RedirectCondition>) {
    val isValid: Boolean get() = isValidDestinationUrl(longUrl) && conditions.all { it.isValid }
}

data class RedirectRules(val defaultLongUrl: String, val rules: List<RedirectRule>) {
    val hasUnknownConditions: Boolean get() = rules.any { rule -> rule.conditions.any { it.knownType == null } }
}
