package at.awit.shlinker.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test fun detailsRouteRestoresProfileShortCodeAndOptionalDomain() {
        for (domain in listOf(null, "custom.example")) {
            val route = ShortUrlDetailsRoute("profile-1", "same-code", domain)
            assertEquals(route, Json.decodeFromString<ShortUrlDetailsRoute>(Json.encodeToString(route)))
        }
    }
}
