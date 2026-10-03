package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ArushiPrimary,
    onPrimary = Color.White,
    primaryContainer = ArushiSurfaceVariant,
    onPrimaryContainer = ArushiPrimaryLight,
    secondary = ArushiSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = Color(0xFFA5F3FC),
    tertiary = ArushiTertiary,
    background = ArushiDeepBg,
    onBackground = ArushiTextPrimary,
    surface = ArushiSurface,
    onSurface = ArushiTextPrimary,
    surfaceVariant = ArushiSurfaceVariant,
    onSurfaceVariant = ArushiTextSecondary,
    error = ArushiError,
    onError = Color.White
)

private val LightColorScheme = DarkColorScheme // Default to dark aesthetic for voice assistant

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
