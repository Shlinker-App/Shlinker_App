package at.awit.shlinker.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object AddServerRoute : NavKey

@Serializable
data object ShortUrlsRoute : NavKey

@Serializable
data class EditServerRoute(val id: String) : NavKey

@Serializable
data class CreateShortUrlRoute(val serverId: String) : NavKey

@Serializable
data class ShortUrlDetailsRoute(val serverId: String, val shortCode: String, val domain: String?) : NavKey
