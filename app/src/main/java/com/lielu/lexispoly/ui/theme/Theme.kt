package com.lielu.lexispoly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette neutre (bleu ardoise), sans reprendre l'identité visuelle de LexisNexis. Les couleurs vivent ici et nulle part ailleurs : un écran ne code
// pas de couleur en dur. `BackgroundLight`/`BackgroundDark` doivent suivre `window_background` dans
// res/values*/colors.xml.
private val BrandLight = Color(0xFF1F3A5F)
private val BrandDark = Color(0xFFA9C7F0)
private val BackgroundLight = Color(0xFFF8F9FC)
private val BackgroundDark = Color(0xFF12161C)

private val LightColors =
    lightColorScheme(
        primary = BrandLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD5E3F7),
        onPrimaryContainer = Color(0xFF0A1F36),
        background = BackgroundLight,
        onBackground = Color(0xFF191C20),
        surface = BackgroundLight,
        onSurface = Color(0xFF191C20),
        surfaceVariant = Color(0xFFE0E3EA),
        onSurfaceVariant = Color(0xFF44474E),
        outline = Color(0xFF74777F),
    )

private val DarkColors =
    darkColorScheme(
        primary = BrandDark,
        onPrimary = Color(0xFF0A2A4D),
        primaryContainer = Color(0xFF2C4D75),
        onPrimaryContainer = Color(0xFFD5E3F7),
        background = BackgroundDark,
        onBackground = Color(0xFFE2E2E6),
        surface = BackgroundDark,
        onSurface = Color(0xFFE2E2E6),
        surfaceVariant = Color(0xFF44474E),
        onSurfaceVariant = Color(0xFFC4C6CF),
        outline = Color(0xFF8E9099),
    )

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
