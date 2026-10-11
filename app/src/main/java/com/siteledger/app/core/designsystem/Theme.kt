package com.siteledger.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Blueprint blue for structure, safety amber for warnings, teal for healthy states.
private val Blue900 = Color(0xFF0E3350)
private val Blue700 = Color(0xFF1D5A8E)
private val Blue100 = Color(0xFFD3E4F5)
private val Amber800 = Color(0xFF8A4B08)
private val Amber100 = Color(0xFFFDEBD0)
private val Teal800 = Color(0xFF0B5D57)
private val Teal100 = Color(0xFFD3F0EC)
private val Red800 = Color(0xFF8C1D18)
private val Red100 = Color(0xFFF9DEDC)
private val Sand50 = Color(0xFFFAF7F2)
private val Ink900 = Color(0xFF1B1C1E)
private val Sand200 = Color(0xFFEDE6DA)

private val LightColors = lightColorScheme(
    primary = Blue700,
    onPrimary = Color.White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue900,
    secondary = Amber800,
    onSecondary = Color.White,
    secondaryContainer = Amber100,
    onSecondaryContainer = Color(0xFF3F2500),
    tertiary = Teal800,
    onTertiary = Color.White,
    tertiaryContainer = Teal100,
    onTertiaryContainer = Color(0xFF03302C),
    error = Red800,
    onError = Color.White,
    errorContainer = Red100,
    onErrorContainer = Color(0xFF410E0B),
    background = Sand50,
    onBackground = Ink900,
    surface = Color.White,
    onSurface = Ink900,
    surfaceVariant = Sand200,
    onSurfaceVariant = Color(0xFF4C463C),
    outline = Color(0xFF7C766B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC6EF),
    onPrimary = Blue900,
    primaryContainer = Color(0xFF14466E),
    onPrimaryContainer = Color(0xFFD3E4F5),
    secondary = Color(0xFFF0BC80),
    onSecondary = Color(0xFF472A00),
    secondaryContainer = Color(0xFF653E00),
    onSecondaryContainer = Amber100,
    tertiary = Color(0xFF9CD9D1),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF155049),
    onTertiaryContainer = Teal100,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Red100,
    background = Color(0xFF121417),
    onBackground = Color(0xFFE3E1DC),
    surface = Color(0xFF1B1E22),
    onSurface = Color(0xFFE3E1DC),
    surfaceVariant = Color(0xFF44474C),
    onSurfaceVariant = Color(0xFFC4C6CC),
)

@Composable
fun SiteLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
