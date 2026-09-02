package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SleekPrimaryDark,
    onPrimary = SleekOnPrimaryDark,
    primaryContainer = SleekPrimaryContainerDark,
    onPrimaryContainer = SleekOnPrimaryContainerDark,
    secondary = SleekSecondaryDark,
    onSecondary = SleekOnSecondaryDark,
    secondaryContainer = SleekSecondaryContainerDark,
    onSecondaryContainer = SleekOnSecondaryContainerDark,
    tertiary = SleekTertiaryDark,
    background = SleekBackgroundDark,
    surface = SleekSurfaceDark,
    surfaceVariant = SleekSurfaceVariantDark,
    outline = SleekOutlineDark,
    outlineVariant = SleekOutlineVariantDark,
    onBackground = SleekTextPrimaryDark,
    onSurface = SleekTextPrimaryDark,
    onSurfaceVariant = SleekTextSecondaryDark,
    error = DangerColor
)

private val LightColorScheme = lightColorScheme(
    primary = SleekPrimaryLight,
    onPrimary = SleekOnPrimaryLight,
    primaryContainer = SleekPrimaryContainerLight,
    onPrimaryContainer = SleekOnPrimaryContainerLight,
    secondary = SleekSecondaryLight,
    onSecondary = SleekOnSecondaryLight,
    secondaryContainer = SleekSecondaryContainerLight,
    onSecondaryContainer = SleekOnSecondaryContainerLight,
    tertiary = SleekTertiaryLight,
    background = SleekBackgroundLight,
    surface = SleekSurfaceLight,
    surfaceVariant = SleekSurfaceVariantLight,
    outline = SleekOutlineLight,
    outlineVariant = SleekOutlineVariantLight,
    onBackground = SleekTextPrimaryLight,
    onSurface = SleekTextPrimaryLight,
    onSurfaceVariant = SleekTextSecondaryLight,
    error = DangerColor
)

@Composable
fun FindAnythingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our brand palette by default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Retain alias for any existing preview references
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = FindAnythingTheme(darkTheme, dynamicColor, content)
