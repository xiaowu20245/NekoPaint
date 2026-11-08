package io.github.xiaowu20245.nekopaint.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = NekoPrimaryLight,
    onPrimary = NekoOnPrimaryLight,
    primaryContainer = NekoPrimaryContainerLight,
    onPrimaryContainer = NekoOnPrimaryContainerLight,
    secondary = NekoSecondaryLight,
    onSecondary = NekoOnSecondaryLight,
    secondaryContainer = NekoSecondaryContainerLight,
    onSecondaryContainer = NekoOnSecondaryContainerLight,
    tertiary = NekoTertiaryLight,
    onTertiary = NekoOnTertiaryLight,
    tertiaryContainer = NekoTertiaryContainerLight,
    onTertiaryContainer = NekoOnTertiaryContainerLight,
    background = NekoBackgroundLight,
    onBackground = NekoOnBackgroundLight,
    surface = NekoSurfaceLight,
    onSurface = NekoOnSurfaceLight,
    surfaceVariant = NekoSurfaceVariantLight,
    onSurfaceVariant = NekoOnSurfaceVariantLight,
    surfaceContainerLowest = NekoSurfaceContainerLowestLight,
    surfaceContainerLow = NekoSurfaceContainerLowLight,
    surfaceContainer = NekoSurfaceContainerLight,
    surfaceContainerHigh = NekoSurfaceContainerHighLight,
    surfaceContainerHighest = NekoSurfaceContainerHighestLight,
    outline = NekoOutlineLight,
    outlineVariant = NekoOutlineVariantLight,
    error = NekoDangerLight,
    onError = NekoOnDangerLight,
    errorContainer = NekoDangerContainerLight,
    onErrorContainer = NekoOnDangerContainerLight,
)

private val DarkColors = darkColorScheme(
    primary = NekoPrimaryDark,
    onPrimary = NekoOnPrimaryDark,
    primaryContainer = NekoPrimaryContainerDark,
    onPrimaryContainer = NekoOnPrimaryContainerDark,
    secondary = NekoSecondaryDark,
    onSecondary = NekoOnSecondaryDark,
    secondaryContainer = NekoSecondaryContainerDark,
    onSecondaryContainer = NekoOnSecondaryContainerDark,
    tertiary = NekoTertiaryDark,
    onTertiary = NekoOnTertiaryDark,
    tertiaryContainer = NekoTertiaryContainerDark,
    onTertiaryContainer = NekoOnTertiaryContainerDark,
    background = NekoBackgroundDark,
    onBackground = NekoOnBackgroundDark,
    surface = NekoSurfaceDark,
    onSurface = NekoOnSurfaceDark,
    surfaceVariant = NekoSurfaceVariantDark,
    onSurfaceVariant = NekoOnSurfaceVariantDark,
    surfaceContainerLowest = NekoSurfaceContainerLowestDark,
    surfaceContainerLow = NekoSurfaceContainerLowDark,
    surfaceContainer = NekoSurfaceContainerDark,
    surfaceContainerHigh = NekoSurfaceContainerHighDark,
    surfaceContainerHighest = NekoSurfaceContainerHighestDark,
    outline = NekoOutlineDark,
    outlineVariant = NekoOutlineVariantDark,
    error = NekoDangerDark,
    onError = NekoOnDangerDark,
    errorContainer = NekoDangerContainerDark,
    onErrorContainer = NekoOnDangerContainerDark,
)

@Immutable
data class NekoSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

private val LightSemanticColors = NekoSemanticColors(
    success = NekoSuccessLight,
    onSuccess = NekoOnSuccessLight,
    successContainer = NekoSuccessContainerLight,
    onSuccessContainer = NekoOnSuccessContainerLight,
    warning = NekoWarningLight,
    onWarning = NekoOnWarningLight,
    warningContainer = NekoWarningContainerLight,
    onWarningContainer = NekoOnWarningContainerLight,
    danger = NekoDangerLight,
    onDanger = NekoOnDangerLight,
    dangerContainer = NekoDangerContainerLight,
    onDangerContainer = NekoOnDangerContainerLight,
    info = NekoInfoLight,
    onInfo = NekoOnInfoLight,
    infoContainer = NekoInfoContainerLight,
    onInfoContainer = NekoOnInfoContainerLight,
)

private val DarkSemanticColors = NekoSemanticColors(
    success = NekoSuccessDark,
    onSuccess = NekoOnSuccessDark,
    successContainer = NekoSuccessContainerDark,
    onSuccessContainer = NekoOnSuccessContainerDark,
    warning = NekoWarningDark,
    onWarning = NekoOnWarningDark,
    warningContainer = NekoWarningContainerDark,
    onWarningContainer = NekoOnWarningContainerDark,
    danger = NekoDangerDark,
    onDanger = NekoOnDangerDark,
    dangerContainer = NekoDangerContainerDark,
    onDangerContainer = NekoOnDangerContainerDark,
    info = NekoInfoDark,
    onInfo = NekoOnInfoDark,
    infoContainer = NekoInfoContainerDark,
    onInfoContainer = NekoOnInfoContainerDark,
)

val LocalNekoColors = staticCompositionLocalOf { LightSemanticColors }

object NekoTheme {
    val colors: NekoSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNekoColors.current
}

@Composable
fun NekoPaintTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalNekoColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NekoTypography,
            content = content,
        )
    }
}
