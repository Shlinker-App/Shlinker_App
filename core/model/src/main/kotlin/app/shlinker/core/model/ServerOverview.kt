package app.shlinker.core.model

/** Server-wide counts as visible to the API key. Clicks exclude bots and orphan visits. */
data class ServerOverview(val shlinks: Long, val humanClicks: Long, val tags: Int)
