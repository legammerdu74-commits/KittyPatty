package be.chatpate

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF9A4E12),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC4),
    onPrimaryContainer = Color(0xFF331300),
    secondary = Color(0xFF765845),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC4),
    onSecondaryContainer = Color(0xFF2C160A),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF211A15),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF211A15),
    surfaceVariant = Color(0xFFF4DED2),
    onSurfaceVariant = Color(0xFF52443C),
    surfaceContainer = Color(0xFFFBEBE0),
    surfaceContainerHigh = Color(0xFFF8E9DE),
    surfaceContainerHighest = Color(0xFFF3E2D6),
    outline = Color(0xFF85746B),
    outlineVariant = Color(0xFFD8C2B8),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB787),
    onPrimary = Color(0xFF512400),
    primaryContainer = Color(0xFF743809),
    onPrimaryContainer = Color(0xFFFFDCC4),
    secondary = Color(0xFFE6BEA8),
    onSecondary = Color(0xFF432B1B),
    secondaryContainer = Color(0xFF5C4130),
    onSecondaryContainer = Color(0xFFFFDCC4),
    background = Color(0xFF1A120C),
    onBackground = Color(0xFFEFE0D7),
    surface = Color(0xFF1A120C),
    onSurface = Color(0xFFEFE0D7),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B8),
    surfaceContainer = Color(0xFF261A13),
    surfaceContainerHigh = Color(0xFF2E2118),
    surfaceContainerHighest = Color(0xFF3A2C23),
    outline = Color(0xFF9F8D83),
    outlineVariant = Color(0xFF52443C),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun ChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}

/** Couleur "repas donné" dans le calendrier. */
val DoneColor = Color(0xFF5E8B5A)

fun pateColor(key: String): Color = when (key) {
    Pate.BOEUF.key -> Color(0xFFB4523A)
    else -> Color(0xFFE0A030)
}

/** Couleurs proposées pour les chats. */
private val CatPalette = listOf(
    Color(0xFFD9803F), // orange
    Color(0xFF5B83B7), // bleu
    Color(0xFF6E9E68), // vert
    Color(0xFFA871A6), // mauve
    Color(0xFFC9973A), // miel
    Color(0xFF8A7566), // taupe
)

val CatColorCount: Int get() = CatPalette.size

fun catColor(index: Int): Color = CatPalette[((index % CatPalette.size) + CatPalette.size) % CatPalette.size]
