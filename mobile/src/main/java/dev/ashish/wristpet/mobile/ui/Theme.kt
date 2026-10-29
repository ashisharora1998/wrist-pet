package dev.ashish.wristpet.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Mint = Color(0xFF7FE0B0)
val MintDark = Color(0xFF3E8F6A)
val Cream = Color(0xFFF2EBD9)
val Ink = Color(0xFF1A1C1E)
val Lavender = Color(0xFFB9A6F2)
val LavenderDark = Color(0xFF7A68C4)
val Coral = Color(0xFFFF8A7A)

private val Dark = darkColorScheme(
    primary = Mint,
    onPrimary = Ink,
    secondary = Lavender,
    onSecondary = Ink,
    tertiary = Coral,
    background = Color(0xFF111214),
    surface = Color(0xFF111214),
    surfaceContainer = Ink,
    onBackground = Cream,
    onSurface = Cream,
)

private val Light = lightColorScheme(
    primary = MintDark,
    onPrimary = Color.White,
    secondary = LavenderDark,
    onSecondary = Color.White,
    tertiary = Coral,
    background = Cream,
    surface = Cream,
    onBackground = Ink,
    onSurface = Ink,
)

@Composable
fun WristPetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
