package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val VoltLightColorScheme = lightColorScheme(
    primary = DeepCobalt,
    onPrimary = CrispWhite,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = DeepCobalt,
    secondary = ElectricCyan,
    onSecondary = CrispWhite,
    secondaryContainer = SoftSteelSurface,
    onSecondaryContainer = InkPrimary,
    tertiary = HighVoltageAmber,
    onTertiary = CrispWhite,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = ColorTokens.AmberOnContainerLight,
    background = AlabasterCanvas,
    onBackground = InkPrimary,
    surface = CrispWhite,
    onSurface = InkPrimary,
    surfaceVariant = SoftSteelSurface,
    onSurfaceVariant = InkSecondary,
    outline = OutlineLight,
    outlineVariant = SoftSteelSurface,
    error = AlertCoral,
    onError = CrispWhite,
    errorContainer = CoralContainerLight,
    onErrorContainer = AlertCoral
)

private val VoltDarkColorScheme = darkColorScheme(
    primary = LuminousCyan,
    onPrimary = MidnightSlate,
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = LuminousCyan,
    secondary = BrightAmber,
    onSecondary = MidnightSlate,
    secondaryContainer = ControlRoomElevated,
    onSecondaryContainer = FrostPrimary,
    tertiary = BrightEmerald,
    onTertiary = MidnightSlate,
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = BrightEmerald,
    background = MidnightSlate,
    onBackground = FrostPrimary,
    surface = ControlRoomSurface,
    onSurface = FrostPrimary,
    surfaceVariant = ControlRoomElevated,
    onSurfaceVariant = FrostSecondary,
    outline = SteelBorderDark,
    outlineVariant = ControlRoomElevated,
    error = SoftCoral,
    onError = MidnightSlate,
    errorContainer = CoralContainerDark,
    onErrorContainer = SoftCoral
)

private object ColorTokens {
    val AmberOnContainerLight = androidx.compose.ui.graphics.Color(0xFF78350F)
}

val VoltShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun VoltLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VoltDarkColorScheme else VoltLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VoltTypography,
        shapes = VoltShapes,
        content = content
    )
}
