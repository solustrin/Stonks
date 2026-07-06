package com.trin.stonks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ----------------------------------------------------------------
//  LIGHT SCHEME – white cards, black text, subtle grey accents
// ----------------------------------------------------------------
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF212121),             // dark charcoal for icons / highlights
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0E0),    // light grey container (no pink!)
    onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF616161),           // mid grey for secondary elements
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0E0),
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF757575),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF1F1F1),      // subtle variant for cards/sheets
    onSurfaceVariant = Color(0xFF4A4A4A),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
    error = Color(0xFFD32F2F),               // match your expense red
    onError = Color.White
)

// ----------------------------------------------------------------
//  DARK SCHEME – dark surfaces, white text
// ----------------------------------------------------------------
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFAFAFA),             // light icons / highlights
    onPrimary = Color(0xFF121212),
    primaryContainer = Color(0xFF424242),
    onPrimaryContainer = Color(0xFFE0E0E0),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF121212),
    secondaryContainer = Color(0xFF424242),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color(0xFF121212),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF616161),
    outlineVariant = Color(0xFF424242),
    error = Color(0xFFEF5350),
    onError = Color(0xFF121212)
)

@Composable
fun StonksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}