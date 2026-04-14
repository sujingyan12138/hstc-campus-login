package com.hstc.quicklogin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF9B2F2B),
    secondary = Color(0xFF2B5E88),
    tertiary = Color(0xFF6B7D2B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE39E97),
    secondary = Color(0xFF9FC7E8),
    tertiary = Color(0xFFC7D88C)
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
