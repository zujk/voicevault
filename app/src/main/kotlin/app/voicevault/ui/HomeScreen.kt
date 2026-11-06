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
import app.voicevault.session.SessionService
import app.voicevault.ui.theme.VV

/**
 * Schermata di partenza: un bersaglio enorme e poco altro.
 *
 * Non c'e' niente da configurare prima di allenarsi. La nota del drone si
 * sposta anche da qui, per non doverlo fare in marcia.
 */
@Composable
fun HomeScreen(
    micGranted: Boolean,
    finishedSessionId: Long?,
    onRequestPermission: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenLesson: () -> Unit,
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
            TextAction("Lezione", onOpenLesson)
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
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "drone ${settings.targetHz.toInt()} Hz · registra · cronometro",
                    style = MaterialTheme.typography.bodyLarge,
                    color = VV.TextDim,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // La nota si regola anche da qui: meglio sistemarla da fermi che in marcia.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Nota del drone",
                style = MaterialTheme.typography.bodyMedium,
                color = VV.TextDim,
            )
            Row {
                TextAction("− semitono") { app.settings.nudgeTarget(-1) }
                TextAction("+ semitono") { app.settings.nudgeTarget(1) }
            }
        }

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
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
