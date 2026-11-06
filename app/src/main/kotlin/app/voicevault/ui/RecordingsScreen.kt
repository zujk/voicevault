package app.voicevault.ui

import android.media.MediaPlayer
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.voicevault.VoiceVaultApp
import app.voicevault.data.RecordingStore
import app.voicevault.data.TrainingSession
import app.voicevault.ui.theme.VV

/**
 * L'elenco delle registrazioni, con riascolto.
 *
 * Esiste perche' le registrazioni c'erano ma non si vedevano: finivano in una
 * cartella privata e l'app non le mostrava da nessuna parte.
 */
@Composable
fun RecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VoiceVaultApp
    val sessions by app.db.sessions().recent(100).collectAsState(emptyList())

    var playing by remember { mutableStateOf<Long?>(null) }
    val player = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
    }

    fun play(session: TrainingSession) {
        val path = session.audioPath ?: return
        runCatching {
            player.reset()
            player.setDataSource(context, Uri.parse(path))
            player.prepare()
            player.start()
            playing = session.id
            player.setOnCompletionListener { playing = null }
        }.onFailure { playing = null }
    }

    fun stop() {
        runCatching { player.stop() }
        playing = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VV.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        TextAction("← Indietro", onBack)
        Spacer(Modifier.height(6.dp))
        Text("REGISTRAZIONI", style = MaterialTheme.typography.labelLarge, color = VV.TextDim)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Le trovi anche nel gestore file del telefono, in " +
                "${RecordingStore.folderLabel()}, e dal PC via cavo.",
            style = MaterialTheme.typography.bodyMedium,
            color = VV.TextDim,
        )
        Spacer(Modifier.height(14.dp))

        if (sessions.isEmpty()) {
            Text(
                "Ancora nessuna registrazione.",
                style = MaterialTheme.typography.bodyLarge,
                color = VV.TextDim,
            )
        }

        sessions.forEach { session ->
            val isPlaying = playing == session.id
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isPlaying) VV.SurfaceHigh else VV.Surface)
                    .clickable { if (isPlaying) stop() else play(session) }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = session.dayKey,
                            style = MaterialTheme.typography.bodyLarge,
                            color = VV.Text,
                        )
                        Text(
                            text = buildString {
                                append("${session.durationMs / 60_000} min")
                                session.medianF0?.let { append(" · mediana ${it.toInt()} Hz") }
                                if (session.audioPath == null) append(" · audio mancante")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = VV.TextDim,
                        )
                    }
                    Text(
                        text = if (isPlaying) "■" else "▶",
                        style = MaterialTheme.typography.titleLarge,
                        color = VV.Light,
                    )
                }
            }
        }
    }
}
