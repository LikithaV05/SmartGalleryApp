package com.smartgalleryapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SmartGalleryColors = darkColorScheme(
    primary = Moss500,
    onPrimary = White,
    secondary = Cream100,
    onSecondary = Slate900,
    tertiary = Indigo500,
    background = Forest900,
    onBackground = White,
    surface = ColorPalette.surface,
    onSurface = White,
    error = Red500,
)

private object ColorPalette {
    val surface = androidx.compose.ui.graphics.Color(0xFF203C32)
}

@Composable
fun SmartGalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmartGalleryColors,
        typography = SmartTypography,
        content = content,
    )
}
