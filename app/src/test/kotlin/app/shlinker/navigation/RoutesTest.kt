package app.shlinker.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test fun redirectRulesRouteRestoresPinnedIdentityWithoutCredentials() {
        for (domain in listOf(null, "custom.example")) {
            val route = RedirectRulesRoute("profile-1", "same-code", domain)
            assertEquals(route, Json.decodeFromString<RedirectRulesRoute>(Json.encodeToString(route)))
        }
    }

    @Test fun statisticsRouteRestoresPinnedIdentityWithoutCredentials() {
        for (domain in listOf(null, "custom.example")) {
            val route = VisitStatisticsRoute("profile-1", "same-code", domain)
            assertEquals(route, Json.decodeFromString<VisitStatisticsRoute>(Json.encodeToString(route)))
        }
    }

    @Test fun detailsRouteRestoresProfileShortCodeAndOptionalDomain() {
        for (domain in listOf(null, "custom.example")) {
            val route = ShortUrlDetailsRoute("profile-1", "same-code", domain)
            assertEquals(route, Json.decodeFromString<ShortUrlDetailsRoute>(Json.encodeToString(route)))
        }
    }
}
