package at.awit.linkwerk.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * App-Theme.
 *
 * @param darkTheme Dunkelmodus (Standard: Systemeinstellung).
 * @param dynamicColor Material You / Systemfarben verwenden (ab Android 12). Fällt sonst auf das AWiT-Schema zurück.
 */
@Composable
fun LinkwerkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && supportsDynamicColor() ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> LinkwerkDarkColors
        else -> LinkwerkLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LinkwerkTypography,
        content = content,
    )
}

private fun supportsDynamicColor(): Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
