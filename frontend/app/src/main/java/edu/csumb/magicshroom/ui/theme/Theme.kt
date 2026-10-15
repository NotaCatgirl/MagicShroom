package edu.csumb.magicshroom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandPurple,
    secondary = BrandPurpleDark,
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPurple,
    secondary = BrandPurpleDark,
    background = AppBackground,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = PrimaryText,
    onSurface = PrimaryText,
    onSurfaceVariant = SecondaryText,
    outline = BorderLavender,
)

@Composable
fun MagicShroomTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
