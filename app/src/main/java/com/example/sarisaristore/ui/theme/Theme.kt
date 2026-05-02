package com.example.sarisaristore.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.sarisaristore.data.local.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6A4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8F3E5),
    secondary = Color(0xFFB66B1C),
    secondaryContainer = Color(0xFFFFE1C2),
    tertiary = Color(0xFF006B78),
    background = Color(0xFFF7F5EF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8E3D7),
    outline = Color(0xFF7A7E76),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CE3BC),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF004D35),
    secondary = Color(0xFFFFC88E),
    secondaryContainer = Color(0xFF6A3F00),
    tertiary = Color(0xFF77D5E4),
    background = Color(0xFF101613),
    surface = Color(0xFF18201C),
    surfaceVariant = Color(0xFF23302A),
    outline = Color(0xFF8A958E),
    error = Color(0xFFF2B8B5),
)

@Composable
fun SariSariTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content,
    )
}
