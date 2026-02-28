package dev.krinry.jarvis.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val JarvisDarkScheme = darkColorScheme(
    primary = JarvisPrimary,
    onPrimary = Color.White,
    secondary = JarvisSecondary,
    onSecondary = Color.Black,
    tertiary = JarvisAccent,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = JarvisError,
    onError = Color.White
)

private val JarvisLightScheme = lightColorScheme(
    primary = JarvisPrimary,
    onPrimary = Color.White,
    secondary = JarvisSecondary,
    onSecondary = Color.Black,
    tertiary = JarvisAccent,
    background = Color(0xFFF5F5FA),
    onBackground = Color(0xFF1A1A2E),
    surface = Color.White,
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFE8E8F0),
    onSurfaceVariant = Color(0xFF6B6B8B),
    error = JarvisError,
    onError = Color.White
)

@Composable
fun JarvisTheme(
    darkTheme: Boolean = true, // Always dark by default for Jarvis
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JarvisDarkScheme else JarvisLightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}