package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ArroColorScheme = lightColorScheme(
    primary = BrandGoldPrimary,
    onPrimary = BrandOnGold,
    primaryContainer = BrandGoldSurface,
    onPrimaryContainer = BrandGoldDark,
    secondary = BrandGoldAccent,
    onSecondary = BrandOnGold,
    secondaryContainer = BrandGoldSurface,
    onSecondaryContainer = BrandGoldDark,
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
fun ArroPosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArroColorScheme,
        typography = Typography,
        content = content
    )
}

@Deprecated("Renamed to ArroPosTheme", ReplaceWith("ArroPosTheme(content)"))
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) = ArroPosTheme(content)
