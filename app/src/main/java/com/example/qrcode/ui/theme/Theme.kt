package com.example.qrcode.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    error = Color(0xFFEF5350)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    secondary = Color(0xFF00897B),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFD32F2F)
)

@Composable
fun QRCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Black.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
