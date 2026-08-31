package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KadeColorScheme = lightColorScheme(
    primary = BrandTealPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandMintSurface,
    onPrimaryContainer = BrandTealDark,
    secondary = BrandEmeraldAccent,
    onSecondary = Color.White,
    secondaryContainer = BrandMintSurface,
    onSecondaryContainer = BrandTealDark,
    error = StatusRed,
    onError = Color.White,
    errorContainer = StatusRedBg,
    onErrorContainer = StatusRed,
    background = LightBackground,
    onBackground = TextPrimary,
    surface = LightSurface,
    onSurface = TextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder
)

/**
 * Arro-POS theme. Deliberately light-only: shopkeepers use the app in bright
 * daylight and under shop lighting, and a single scheme keeps every screen
 * predictable.
 */
@Composable
fun KadePosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KadeColorScheme,
        typography = Typography,
        content = content
    )
}

@Deprecated("Renamed to KadePosTheme", ReplaceWith("KadePosTheme(content)"))
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) = KadePosTheme(content)
