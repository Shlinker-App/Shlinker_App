package app.shlinker.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * @param darkTheme Enables dark mode; defaults to the system setting.
 * @param dynamicColor Uses Material You system colors on Android 12 and later; falls back to the AWiT palette.
 */
@Composable
fun ShlinkerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && supportsDynamicColor() ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> ShlinkerDarkColors
        else -> ShlinkerLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShlinkerTypography,
        content = content,
    )
}

private fun supportsDynamicColor(): Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
