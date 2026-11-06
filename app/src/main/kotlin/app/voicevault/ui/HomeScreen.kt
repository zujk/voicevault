package app.voicevault.ui

import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.voicevault.VoiceVaultApp
import app.voicevault.audio.AudioDevices
import app.voicevault.data.TrainingSession
import app.voicevault.session.Exercises
import app.voicevault.session.SessionService
import app.voicevault.ui.theme.VV

/**
 * Schermata di partenza: un bersaglio enorme e tre informazioni.
 *
 * Non c'e' niente da configurare prima di allenarsi: il programma e il target
 * li decide l'app.
 */
@Composable
fun HomeScreen(
    micGranted: Boolean,
    finishedSessionId: Long?,
    onRequestPermission: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as VoiceVaultApp
    val settings by app.settings.state.collectAsState()
    val audioManager = context.getSystemService(AudioManager::class.java)
    val headsetConnected = AudioDevices.usbInput(audioManager) != null

    val last by produceState<TrainingSession?>(initialValue = null, finishedSessionId) {
        value = app.db.sessions().recentOnce(1).firstOrNull()
    }

    val calibrating = !settings.calibrated
    val program = if (calibrating) Exercises.calibration else Exercises.programA

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VV.Background)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextAction("Calendario", onOpenCalendar)
            TextAction("Registrazioni", onOpenRecordings)
            TextAction("Diagnostica", onOpenDiagnostics)
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(VV.Surface)
                .clickable {
                    if (!micGranted) onRequestPermission() else SessionService.start(context)
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (micGranted) "INIZIA" else "CONSENTI IL MICROFONO",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (micGranted) VV.Light else VV.Heavy,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "${program.title} · ${program.totalSec / 60} minuti",
                    style = MaterialTheme.typography.titleLarge,
                    color = VV.Text,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (calibrating) {
                        "Le prime sessioni misurano la tua voce"
                    } else {
                        "Drone sul target: ${settings.targetHz.toInt()} Hz"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = VV.TextDim,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (headsetConnected) "Cuffie collegate" else "Cuffie non collegate",
                style = MaterialTheme.typography.bodyMedium,
                color = if (headsetConnected) VV.Light else VV.Heavy,
            )
            last?.let { s ->
                Text(
                    text = "Ultima: ${s.durationMs / 60_000} min" +
                        (s.medianF0?.let { " · mediana ${it.toInt()} Hz" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VV.TextDim,
                )
            }
        }
    }
}

@Composable
internal fun TextAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = VV.Light,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
