package app.shlinker.core.model

data class ShlinkDefaults(val forwardQuery: Boolean = false, val crawlable: Boolean? = null)

data class AppPreferences(
    val appTag: AppTagSettings,
    val shlinkDefaults: ShlinkDefaults,
    val display: DisplaySettings = DisplaySettings(),
)
