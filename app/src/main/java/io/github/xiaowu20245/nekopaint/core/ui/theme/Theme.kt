package io.github.xiaowu20245.nekopaint.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = NekoBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = NekoBlueContainer,
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001453),
    secondary = NekoPink,
    secondaryContainer = NekoPinkContainer,
    background = NekoBackground,
    surfaceVariant = NekoSurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = NekoBlueDark,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00297A),
    primaryContainer = NekoBlueContainerDark,
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFDDE2FF),
    secondary = NekoPink,
    secondaryContainer = NekoPinkContainerDark,
    background = NekoBackgroundDark,
    surfaceVariant = NekoSurfaceVariantDark,
)

@Composable
fun NekoPaintTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NekoTypography,
        content = content,
    )
}
