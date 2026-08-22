package com.rdndds.forcefastboot.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Dark-only brand scheme. Dynamic color intentionally removed: wallpaper-based
 * palettes fought the hardcoded terminal colors, and this is a flashing tool -
 * the visual identity must be deterministic on every device.
 */
private val AppDarkColors =
    darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80,
        background = Color(0xFF131217), // matches drawable/ic_launcher_background
        surface = Color(0xFF131217),
        surfaceVariant = Color(0xFF232028),
        error = Color(0xFFEF5350),
    )

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
)

@Composable
fun ForceFastbootTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = AppDarkColors, typography = Typography, shapes = AppShapes, content = content)
}
