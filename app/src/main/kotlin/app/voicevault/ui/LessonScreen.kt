package app.voicevault.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.voicevault.data.ClipKind
import app.voicevault.data.Library
import app.voicevault.session.ExamplePlayer
import app.voicevault.ui.theme.VV

/**
 * La lezione, spacchettata in sezioni ascoltabili.
 *
 * Si guarda da fermi: e' materiale di studio, non roba da sessione. Le clip che
 * gli esercizi usano come esempio vengono da qui, citate per identificativo.
 */
@Composable
fun LessonScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember { ExamplePlayer(context) }
    var playing by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VV.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        TextAction("← Indietro", onBack)
        Spacer(Modifier.height(6.dp))

        Library.lessons.forEach { lesson ->
            Text("LEZIONE", style = MaterialTheme.typography.labelLarge, color = VV.TextDim)
            Spacer(Modifier.height(4.dp))
            Text(lesson.title, style = MaterialTheme.typography.titleLarge, color = VV.Text)
            Text(
                text = "${lesson.author} · ${lesson.source}",
                style = MaterialTheme.typography.bodyMedium,
                color = VV.TextDim,
            )
            Spacer(Modifier.height(14.dp))

            ClipKind.entries.forEach { kind ->
                val clips = lesson.clips.filter { it.kind == kind }
                if (clips.isEmpty()) return@forEach

                Text(
                    text = kind.label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = VV.Target,
                )
                Spacer(Modifier.height(6.dp))

                clips.forEach { clip ->
                    val isPlaying = playing == clip.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isPlaying) VV.SurfaceHigh else VV.Surface)
                            .clickable {
                                if (isPlaying) {
                                    player.stop()
                                    playing = null
                                } else {
                                    playing = clip.id
                                    player.play(clip.res) { playing = null }
                                }
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    clip.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = VV.Text,
                                )
                                Text(
                                    text = "da ${clip.fromLabel} · ${clip.durationSec / 60}:" +
                                        "%02d".format(clip.durationSec % 60),
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
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
