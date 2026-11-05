package app.voicevault.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import app.voicevault.VoiceVaultApp
import app.voicevault.session.SessionService
import app.voicevault.session.SessionUiState
import app.voicevault.ui.theme.VV
import kotlin.math.ln

/**
 * La schermata che sta sul supporto allo specchietto.
 *
 * Regole: nessun bersaglio piccolo, nessun testo da leggere, nessuna animazione
 * rapida. Tap in qualunque punto = avanti, pressione lunga = stop. Tutto il
 * resto arriva dalla voce guida e dal drone.
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
            previousOrientation?.let { activity.requestedOrientation = it }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VV.Background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { SessionService.next(context) },
                    onLongPress = { SessionService.stop(context) },
                )
            }
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.isCalibration) "CALIBRAZIONE" else state.programTitle.uppercase(),
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = VV.TextDim,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.exerciseTitle,
                    style = androidx.compose.material3.MaterialTheme.typography.displayMedium,
                    color = VV.Text,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = formatTime(state.exerciseRemainingSec),
                    style = androidx.compose.material3.MaterialTheme.typography.displayLarge,
                    color = VV.TextDim,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "esercizio ${state.exerciseIndex + 1} di ${state.exerciseCount}" +
                        " · sessione ${formatTime(state.sessionRemainingSec)}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = VV.TextDim,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "tap = avanti   ·   tieni premuto = stop",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = VV.Target,
                )
            }

            Spacer(Modifier.width(24.dp))

            PitchColumn(
                state = state,
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(VV.Surface),
            )
        }
    }
}

/**
 * La colonna del pitch: scala logaritmica, banda del target, e un blocco di
 * colore che sale e scende. Il colore viene dal peso: ambra = pesante,
 * verde-azzurro = leggera. Va letta con la visione periferica, non guardata.
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
            size = androidx.compose.ui.geometry.Size(w, bandBottom - bandTop),
        )

        if (state.voiced && state.currentHz > 0f) {
            val y = yOf(state.currentHz)
            val weight = normalizedWeight(state.tiltDb)
            val color = lerp(VV.Light, VV.Heavy, weight)
            val blockHeight = 26f
            drawRect(
                color = color,
                topLeft = Offset(0f, (y - blockHeight / 2).coerceIn(0f, h - blockHeight)),
                size = androidx.compose.ui.geometry.Size(w, blockHeight),
            )
        }
    }
}

/**
 * Normalizzazione provvisoria del tilt in 0..1.
 *
 * Volutamente grezza: la normalizzazione vera arriva con la calibrazione del
 * peso in Fase 2. Qui serve solo a far muovere un colore, e non viene salvata
 * da nessuna parte ne' mostrata come numero.
 */
private fun normalizedWeight(tiltDb: Float?): Float {
    if (tiltDb == null) return 0.5f
    return ((tiltDb - 6f) / 18f).coerceIn(0f, 1f)
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private const val MIN_HZ = 90f
private const val MAX_HZ = 400f
