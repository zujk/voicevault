package app.voicevault.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.voicevault.VoiceVaultApp
import app.voicevault.session.SessionService
import app.voicevault.session.SessionUiState
import app.voicevault.ui.theme.VV
import kotlin.math.ln

/**
 * La schermata che sta sul supporto allo specchietto.
 *
 * Tre informazioni e tre bersagli, tutti enormi. Niente da leggere, niente da
 * ricordare: il drone suona sulla nota, i due pulsanti la spostano di un
 * semitono, il terzo chiude la sessione.
 */
@Composable
fun SessionScreen(state: SessionUiState) {
    val context = LocalContext.current
    val activity = context as? Activity
    val keepScreenOn = (context.applicationContext as VoiceVaultApp).settings.current.keepScreenOn

    DisposableEffect(keepScreenOn) {
        // Landscape forzato: il telefono e' sul supporto allo specchietto.
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation =
                previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(VV.Background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTime(state.elapsedSec),
                style = MaterialTheme.typography.displayLarge,
                color = VV.Text,
            )
            Text(
                text = "drone ${state.targetHz.toInt()} Hz" +
                    if (state.voiced && state.currentHz > 0f) {
                        "   ·   tu ${state.currentHz.toInt()} Hz"
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.titleLarge,
                color = VV.TextDim,
            )

            Spacer(Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                BigButton("−", VV.Text, Modifier.weight(1f)) { SessionService.targetDown(context) }
                Spacer(Modifier.width(10.dp))
                BigButton("+", VV.Text, Modifier.weight(1f)) { SessionService.targetUp(context) }
                Spacer(Modifier.width(10.dp))
                BigButton("FINE", VV.Heavy, Modifier.weight(1.4f)) { SessionService.stop(context) }
            }
        }

        Spacer(Modifier.width(16.dp))

        PitchColumn(
            state = state,
            modifier = Modifier
                .width(86.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(VV.Surface),
        )
    }
}

@Composable
private fun BigButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(VV.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.displayMedium,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * La colonna del pitch: scala logaritmica, banda del target, e un blocco di
 * colore che sale e scende. Il colore viene dal peso: ambra = pesante,
 * verde-azzurro = leggera. Va letta con la visione periferica.
 */
@Composable
private fun PitchColumn(state: SessionUiState, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxHeight()) {
        val h = size.height
        val w = size.width

        fun yOf(hz: Float): Float {
            val t = (ln(hz.toDouble()) - ln(MIN_HZ.toDouble())) /
                (ln(MAX_HZ.toDouble()) - ln(MIN_HZ.toDouble()))
            return (h * (1.0 - t.coerceIn(0.0, 1.0))).toFloat()
        }

        // Banda del target: +/- un tono e mezzo, come nelle metriche.
        val bandTop = yOf(state.targetHz * 1.0905f)
        val bandBottom = yOf(state.targetHz / 1.0905f)
        drawRect(
            color = VV.Target.copy(alpha = 0.35f),
            topLeft = Offset(0f, bandTop),
            size = Size(w, bandBottom - bandTop),
        )

        if (state.voiced && state.currentHz > 0f) {
            val y = yOf(state.currentHz)
            val weight = normalizedWeight(state.tiltDb)
            val blockHeight = 26f
            drawRect(
                color = lerp(VV.Light, VV.Heavy, weight),
                topLeft = Offset(0f, (y - blockHeight / 2).coerceIn(0f, h - blockHeight)),
                size = Size(w, blockHeight),
            )
        }
    }
}

/**
 * Normalizzazione provvisoria del tilt in 0..1.
 *
 * Volutamente grezza: serve solo a far muovere un colore, e non viene salvata
 * da nessuna parte ne' mostrata come numero.
 */
private fun normalizedWeight(tiltDb: Float?): Float {
    if (tiltDb == null) return 0.5f
    return ((tiltDb - 6f) / 18f).coerceIn(0f, 1f)
}

private fun formatTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

private const val MIN_HZ = 90f
private const val MAX_HZ = 400f
