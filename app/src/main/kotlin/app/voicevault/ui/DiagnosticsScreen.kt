package app.voicevault.ui

import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.voicevault.audio.AudioCapture
import app.voicevault.ui.theme.VV
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/**
 * Diagnostica: qui si misurano i due rischi residui del progetto, invece di
 * scrivere un'app usa-e-getta per scoprirli (vedi docs/DESIGN.md, Fase 1).
 *
 *  1. le cuffie economiche fanno AGC o noise suppression dentro il loro chip?
 *  2. quanto regge il pitch tracking in auto, in marcia?
 *
 * Il sospetto di AGC si legge dalla dinamica: se fra un sussurro e una voce
 * piena ci sono meno di 12 dB, qualcosa sta comprimendo. E' un'euristica,
 * dichiarata come tale.
 */
private data class DiagState(
    val device: String = "-",
    val source: String = "-",
    val hz: Float = 0f,
    val confidence: Float = 0f,
    val rmsDb: Float = -120f,
    val minRmsDb: Float = 0f,
    val maxRmsDb: Float = -120f,
    val tiltDb: Float? = null,
    val framesWithSound: Int = 0,
    val framesVoiced: Int = 0,
)

@Composable
fun DiagnosticsScreen(
    micGranted: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val audioManager = context.getSystemService(AudioManager::class.java)
    val flow = remember { MutableStateFlow(DiagState()) }
    val state by flow.collectAsState()

    DisposableEffect(micGranted) {
        var capture: AudioCapture? = null
        if (micGranted) {
            val c = AudioCapture(audioManager) { frame ->
                val p = frame.pitch
                val prev = flow.value
                val hasSound = p.rmsDb > -42f
                flow.value = prev.copy(
                    hz = if (p.voiced) p.hz else prev.hz,
                    confidence = p.confidence,
                    rmsDb = p.rmsDb,
                    minRmsDb = if (hasSound) minOf(prev.minRmsDb, p.rmsDb) else prev.minRmsDb,
                    maxRmsDb = maxOf(prev.maxRmsDb, p.rmsDb),
                    tiltDb = frame.weight.tiltDb.takeIf { frame.weight.valid } ?: prev.tiltDb,
                    framesWithSound = prev.framesWithSound + if (hasSound) 1 else 0,
                    framesVoiced = prev.framesVoiced + if (p.voiced) 1 else 0,
                )
            }
            if (c.start()) {
                capture = c
                c.info?.let { info ->
                    flow.value = flow.value.copy(device = info.inputDevice, source = info.sourceName)
                }
            }
        }
        onDispose { capture?.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VV.Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TextAction("← Indietro", onBack)
        Spacer(Modifier.height(10.dp))
        Text("DIAGNOSTICA", style = MaterialTheme.typography.labelLarge, color = VV.TextDim)
        Spacer(Modifier.height(16.dp))

        if (!micGranted) {
            TextAction("Consenti il microfono", onRequestPermission)
            return@Column
        }

        val dynamicDb = state.maxRmsDb - state.minRmsDb
        val voicedRatio = if (state.framesWithSound == 0) 0f
        else state.framesVoiced.toFloat() / state.framesWithSound

        Field("Ingresso", state.device)
        Field("Sorgente audio", state.source)
        Field("Pitch", if (state.hz > 0f) "${state.hz.toInt()} Hz" else "-")
        Field("Confidenza", pct(state.confidence))
        Field("Livello", "${state.rmsDb.toInt()} dB")
        Field("Inclinazione spettrale", state.tiltDb?.let { fmt(it) + " dB" } ?: "-")
        Field("Frame affidabili", pct(voicedRatio))

        Spacer(Modifier.height(18.dp))
        Text("DINAMICA", style = MaterialTheme.typography.labelLarge, color = VV.TextDim)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Parla piano, poi a voce piena. Qui sotto deve comparire " +
                "una differenza larga: se resta sotto i 12 dB, le cuffie stanno " +
                "comprimendo e le metriche di peso vanno calibrate su quel comportamento.",
            style = MaterialTheme.typography.bodyMedium,
            color = VV.TextDim,
        )
        Spacer(Modifier.height(10.dp))
        Field(
            "Escursione misurata",
            if (state.framesWithSound < 50) "in attesa di voce…" else "${dynamicDb.toInt()} dB",
        )
        if (state.framesWithSound >= 50) {
            Text(
                text = when {
                    dynamicDb < 12f -> "Sospetta compressione in ingresso."
                    dynamicDb < 20f -> "Dinamica stretta ma accettabile."
                    else -> "Dinamica ampia: nessun segno di compressione."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (dynamicDb < 12f) VV.Heavy else VV.Light,
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = VV.TextDim)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = VV.Text)
    }
}

private fun pct(v: Float) = "${(v * 100).toInt()}%"
private fun fmt(v: Float) = String.format(Locale.ITALY, "%.1f", v)
