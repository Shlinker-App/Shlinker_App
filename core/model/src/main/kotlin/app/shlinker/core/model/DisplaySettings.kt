package app.shlinker.core.model

enum class NavigationMenuStyle { DRAWER, OVERFLOW }

data class DisplaySettings(
    val showServerOverview: Boolean = true,
    val menuStyle: NavigationMenuStyle = NavigationMenuStyle.DRAWER,
)
