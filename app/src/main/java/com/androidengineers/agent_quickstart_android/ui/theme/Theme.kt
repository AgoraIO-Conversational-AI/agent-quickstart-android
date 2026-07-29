package com.androidengineers.agent_quickstart_android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

object BetterSaidSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 16.dp
    val Lg = 24.dp
    val Xl = 40.dp
    val ContainerMargin = 20.dp
    val Gutter = 16.dp
}

object BetterSaidShapes {
    val Sm = 4.dp
    val Md = 8.dp
    val Lg = 12.dp
    val Xl = 16.dp
    val InkStroke = 1.5.dp
}

private val BetterSaidMaterialShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(BetterSaidShapes.Sm),
    small = androidx.compose.foundation.shape.RoundedCornerShape(BetterSaidShapes.Md),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(BetterSaidShapes.Md),
    large = androidx.compose.foundation.shape.RoundedCornerShape(BetterSaidShapes.Lg),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(BetterSaidShapes.Xl),
)

private val DarkColorScheme = darkColorScheme(
    primary = BetterSaidSageFixed,
    onPrimary = BetterSaidDarkBackground,
    primaryContainer = BetterSaidSage,
    onPrimaryContainer = Color.White,
    secondary = BetterSaidSky,
    onSecondary = BetterSaidDarkBackground,
    secondaryContainer = BetterSaidDarkSurfaceHigh,
    onSecondaryContainer = BetterSaidDarkInk,
    tertiary = BetterSaidCoral,
    onTertiary = BetterSaidDarkBackground,
    tertiaryContainer = BetterSaidCoralDeep,
    onTertiaryContainer = BetterSaidCoralSoft,
    background = BetterSaidDarkBackground,
    onBackground = BetterSaidDarkInk,
    surface = BetterSaidDarkSurface,
    onSurface = BetterSaidDarkInk,
    surfaceVariant = BetterSaidDarkSurfaceHigh,
    onSurfaceVariant = BetterSaidDarkInkSoft,
    surfaceContainer = BetterSaidDarkSurface,
    surfaceContainerHigh = BetterSaidDarkSurfaceHigh,
    surfaceContainerHighest = Color(0xFF343630),
    outline = BetterSaidDarkOutline,
    outlineVariant = BetterSaidDarkOutline,
    error = BetterSaidErrorSoft,
    onError = BetterSaidDarkBackground,
)

private val LightColorScheme = lightColorScheme(
    primary = BetterSaidInk,
    onPrimary = Color.White,
    primaryContainer = BetterSaidInk,
    onPrimaryContainer = Color.White,
    secondary = BetterSaidSage,
    onSecondary = Color.White,
    secondaryContainer = BetterSaidSageSoft,
    onSecondaryContainer = BetterSaidSage,
    tertiary = BetterSaidCoral,
    onTertiary = Color.White,
    tertiaryContainer = BetterSaidCoralSoft,
    onTertiaryContainer = BetterSaidCoralDeep,
    background = BetterSaidPaper,
    onBackground = BetterSaidInk,
    surface = BetterSaidWhite,
    onSurface = BetterSaidInk,
    surfaceVariant = BetterSaidPaperHighest,
    onSurfaceVariant = BetterSaidInkSoft,
    surfaceContainerLowest = BetterSaidWhite,
    surfaceContainerLow = BetterSaidPaperLow,
    surfaceContainer = BetterSaidPaperMid,
    surfaceContainerHigh = BetterSaidPaperHigh,
    surfaceContainerHighest = BetterSaidPaperHighest,
    outline = BetterSaidOutline,
    outlineVariant = BetterSaidOutlineSoft,
    error = BetterSaidError,
    onError = Color.White,
    errorContainer = BetterSaidErrorSoft,
    onErrorContainer = Color(0xFF93000A),
)

@Composable
fun AgentquickstartandroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = BetterSaidMaterialShapes,
        content = content,
    )
}
