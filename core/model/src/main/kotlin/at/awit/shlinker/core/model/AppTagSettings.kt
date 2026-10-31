package at.awit.shlinker.core.model

data class AppTagSettings(val enabled: Boolean = true, val name: String = "shlinker") {
    val isValid: Boolean
        get() = name.isNotBlank() && name.none { it == ',' || it == '\n' || it == '\r' }
}
