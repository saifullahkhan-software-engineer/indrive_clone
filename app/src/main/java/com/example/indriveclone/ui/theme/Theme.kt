package com.example.indriveclone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** inDrive-ish palette: near-black surfaces with a lime accent. */
private val Lime = Color(0xFFB9E937)
private val LimeDark = Color(0xFF7BA31F)
private val InkBlack = Color(0xFF151716)
private val InkSurface = Color(0xFFFFFFFF)
private val InkSurfaceVariant = Color(0xFFEFF1EC)
private val OnInk = Color(0xFF191B1A)
private val Muted = Color(0xFF5C6357)

private val LightColors = lightColorScheme(
    primary = Lime,
    onPrimary = InkBlack,
    primaryContainer = Lime,
    onPrimaryContainer = InkBlack,
    secondary = InkBlack,
    onSecondary = Color.White,
    secondaryContainer = InkSurfaceVariant,
    onSecondaryContainer = InkBlack,
    tertiary = LimeDark,
    onTertiary = Color.White,
    background = Color(0xFFF7F8F5),
    onBackground = OnInk,
    surface = InkSurface,
    onSurface = OnInk,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = Muted,
    outline = Color(0xFFBFC5B8),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = InkBlack,
    primaryContainer = LimeDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFD7DBD0),
    onSecondary = InkBlack,
    secondaryContainer = Color(0xFF2C302B),
    onSecondaryContainer = Color(0xFFE3E7DC),
    tertiary = Lime,
    background = Color(0xFF121412),
    onBackground = Color(0xFFE3E6E0),
    surface = Color(0xFF191C19),
    onSurface = Color(0xFFE3E6E0),
    surfaceVariant = Color(0xFF2A2E29),
    onSurfaceVariant = Color(0xFFC3C8BC),
    outline = Color(0xFF8D9387),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun InDriveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

/** Small label style used for the "Route via OSRM" chip and metadata rows. */
val LabelTextStyle
    @Composable get() = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.3.sp)
