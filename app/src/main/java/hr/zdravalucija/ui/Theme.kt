package hr.zdravalucija.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import hr.zdravalucija.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF496B46), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5EFD9), onPrimaryContainer = Color(0xFF233D22),
    secondary = Color(0xFFAC603C), secondaryContainer = Color(0xFFFFE3D3),
    background = Color(0xFFFAF9F4), surface = Color(0xFFFAF9F4),
    surfaceVariant = Color(0xFFF0EFE6), onSurface = Color(0xFF282E25),
    onSurfaceVariant = Color(0xFF65705F), outlineVariant = Color(0xFFE0E4D9),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB2CEA3), primaryContainer = Color(0xFF304A2D),
    onPrimaryContainer = Color(0xFFE5EFD9), secondary = Color(0xFFE9B398),
    background = Color(0xFF171C16), surface = Color(0xFF171C16),
    surfaceVariant = Color(0xFF262E23), onSurface = Color(0xFFE5E9DE),
    onSurfaceVariant = Color(0xFFB3BDAB), outlineVariant = Color(0xFF3F4A39),
)
@Composable
fun LucijaTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
