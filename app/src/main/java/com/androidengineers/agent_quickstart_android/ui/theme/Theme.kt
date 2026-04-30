package com.androidengineers.agent_quickstart_android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Signal,
    onPrimary = Snow,
    primaryContainer = HarborMuted,
    onPrimaryContainer = Snow,
    secondary = Aqua,
    onSecondary = Harbor,
    secondaryContainer = Color(0xFF143E43),
    onSecondaryContainer = Snow,
    tertiary = Sand,
    onTertiary = Harbor,
    tertiaryContainer = Color(0xFF4A3413),
    onTertiaryContainer = Snow,
    background = Harbor,
    onBackground = Snow,
    surface = HarborSoft,
    onSurface = Snow,
    surfaceVariant = HarborMuted,
    onSurfaceVariant = Mist,
    surfaceContainer = HarborSoft,
    surfaceContainerHigh = HarborMuted,
    surfaceContainerHighest = Color(0xFF24364E),
    outline = Color(0xFF39506A),
    outlineVariant = Color(0xFF2A3D56),
    error = Coral,
    onError = Snow,
)

private val LightColorScheme = lightColorScheme(
    primary = Signal,
    onPrimary = Color.White,
    primaryContainer = SignalContainer,
    onPrimaryContainer = Ink,
    secondary = Aqua,
    onSecondary = Color.White,
    secondaryContainer = AquaContainer,
    onSecondaryContainer = Ink,
    tertiary = Sand,
    onTertiary = Harbor,
    tertiaryContainer = Color(0xFFFFE8B8),
    onTertiaryContainer = Harbor,
    background = Cloud,
    onBackground = Ink,
    surface = Snow,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = SlateDark,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF1F5FA),
    surfaceContainerHighest = Color(0xFFE8EEF6),
    outline = Frost,
    outlineVariant = Color(0xFFCBD7E5),
    error = Coral,
    onError = Color.White,
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
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
