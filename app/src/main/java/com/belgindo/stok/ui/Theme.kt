package com.belgindo.stok.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palet senada dengan aplikasi desktop PT. Belgindo Raya.
val Navy = Color(0xFF123B63)
val NavyLight = Color(0xFF1D5689)
val SkyBlue = Color(0xFFDBEAFE)
val Amber = Color(0xFFFFC44D)
val GoodGreen = Color(0xFF1B7A3D)
val BadRed = Color(0xFFB3261E)

private val LightScheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = SkyBlue,
    onPrimaryContainer = Navy,
    secondary = NavyLight,
    onSecondary = Color.White,
    secondaryContainer = SkyBlue,
    onSecondaryContainer = Navy,
    background = Color(0xFFF6F8FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFEAEFF5),
    onSurfaceVariant = Color(0xFF44546A),
    error = BadRed
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9FC4E8),
    onPrimary = Color(0xFF0A2A47),
    primaryContainer = Navy,
    onPrimaryContainer = SkyBlue,
    secondary = Color(0xFFB4CBE2),
    onSecondary = Color(0xFF0A2A47),
    background = Color(0xFF101820),
    surface = Color(0xFF16212C),
    surfaceVariant = Color(0xFF223140),
    onSurfaceVariant = Color(0xFFB7C4D2)
)

@Composable
fun StokBRTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content
    )
}
