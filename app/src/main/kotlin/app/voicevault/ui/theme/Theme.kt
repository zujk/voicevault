package app.voicevault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Un solo tema, scuro, sempre. Nessun bianco: lo schermo sta nel campo visivo
 * mentre si guida di notte, e il nero su AMOLED non illumina l'abitacolo.
 * Il tema di sistema non viene nemmeno consultato, di proposito.
 */
object VV {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0E0E12)
    val SurfaceHigh = Color(0xFF17171D)
    val Text = Color(0xFFE8E8EA)
    val TextDim = Color(0xFF8A8A93)
    /** Voce leggera: il colore verso cui si lavora. */
    val Light = Color(0xFF7FD4C1)
    /** Voce pesante. */
    val Heavy = Color(0xFFE0B46A)
    val Target = Color(0xFF4A4A55)
    val Danger = Color(0xFFD08A8A)
}

private val scheme = darkColorScheme(
    primary = VV.Light,
    onPrimary = Color.Black,
    secondary = VV.Heavy,
    background = VV.Background,
    onBackground = VV.Text,
    surface = VV.Surface,
    onSurface = VV.Text,
    surfaceVariant = VV.SurfaceHigh,
    onSurfaceVariant = VV.TextDim,
    error = VV.Danger,
)

/**
 * Testi grandi, ma non enormi: devono restare leggibili con la coda dell'occhio
 * a un metro **e** stare dentro uno schermo in landscape senza sfondare.
 */
private val typography = Typography(
    displayLarge = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Light),
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

@Composable
fun VoiceVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
