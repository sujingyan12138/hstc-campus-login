package com.hstc.quicklogin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F2FF),
    onPrimaryContainer = Color(0xFF0A2E57),
    secondary = Color(0xFF5E6B7A),
    tertiary = Color(0xFF2E7D62),
    background = Color(0xFFF5F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EAEE),
    outlineVariant = Color(0xFFD9DCE3),
    onSurface = Color(0xFF111318),
    onSurfaceVariant = Color(0xFF5B6472)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AB8FF),
    onPrimary = Color(0xFF001D35),
    primaryContainer = Color(0xFF173A5E),
    onPrimaryContainer = Color(0xFFD7E9FF),
    secondary = Color(0xFFB9C3D1),
    tertiary = Color(0xFF8BD8B7),
    background = Color(0xFF101113),
    surface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFF252930),
    outlineVariant = Color(0xFF363C46),
    onSurface = Color(0xFFF2F3F5),
    onSurfaceVariant = Color(0xFFC1C7D0)
)

@Composable
fun HstcQuickLoginTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
