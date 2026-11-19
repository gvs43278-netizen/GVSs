package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldDarkPrimary,
    onPrimary = Color(0xFF00382C),
    primaryContainer = EmeraldDarkPrimaryContainer,
    onPrimaryContainer = OnEmeraldDarkPrimaryContainer,
    secondary = TealDarkSecondary,
    onSecondary = Color(0xFF003730),
    secondaryContainer = TealDarkSecondaryContainer,
    onSecondaryContainer = OnTealDarkSecondaryContainer,
    tertiary = GoldDarkTertiary,
    onTertiary = Color(0xFF3E2200),
    tertiaryContainer = GoldDarkTertiaryContainer,
    onTertiaryContainer = OnGoldDarkTertiaryContainer,
    background = DarkBackground,
    onBackground = Color(0xFFE6EFEA),
    surface = DarkSurface,
    onSurface = Color(0xFFE6EFEA),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB1C4BD),
    outline = DarkOutline,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = OnEmeraldPrimaryContainer,
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = OnTealSecondaryContainer,
    tertiary = GoldTertiary,
    onTertiary = Color.White,
    tertiaryContainer = GoldTertiaryContainer,
    onTertiaryContainer = OnGoldTertiaryContainer,
    background = LightBackground,
    onBackground = Color(0xFF10201C),
    surface = LightSurface,
    onSurface = Color(0xFF10201C),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF465C55),
    outline = LightOutline,
    error = LossCrimson,
    onError = Color.White,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun BusinessManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
