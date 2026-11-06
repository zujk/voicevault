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
import app.voicevault.R
import app.voicevault.session.ExamplePlayer
import app.voicevault.ui.theme.VV

private data class ExampleClip(
    val res: Int,
    val title: String,
    val source: String,
)

private val clips = listOf(
    ExampleClip(R.raw.esempio_hum_slide, "Hum e scivolata", "lezione 6:08 → 7:27 · 78 s"),
    ExampleClip(R.raw.esempio_heavy_heavy_light, "Pesante pesante leggero", "lezione 7:55 → 9:14 · 80 s"),
    ExampleClip(R.raw.esempio_carryover, "Nel parlato", "lezione 13:02 → 13:49 · 47 s"),
)

/**
 * Gli esempi audio, ascoltabili da fermi.
 *
 * Esiste perche' durante una sessione gli esempi non si possono controllare: il
 * tap serve a passare all'esercizio successivo e quindi interrompe l'esempio in
 * corso. Qui si sentono interi, per verificare che i tagli cadano al punto
 * giusto senza fare dodici minuti di sessione.
 */
@Composable
fun ExamplesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember { ExamplePlayer(context) }
    var playing by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) {
        onDispose { player.stop() }
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
        Text("ESEMPI DELLA LEZIONE", style = MaterialTheme.typography.labelLarge, color = VV.TextDim)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Sono gli stessi che partono all'inizio di ogni esercizio. " +
                "Durante la sessione un tap passa all'esercizio dopo, quindi " +
                "interrompe l'esempio: per sentirli interi, qui.",
            style = MaterialTheme.typography.bodyMedium,
            color = VV.TextDim,
        )
        Spacer(Modifier.height(14.dp))

        clips.forEach { clip ->
            val isPlaying = playing == clip.res
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
                            playing = clip.res
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
                        Text(clip.title, style = MaterialTheme.typography.bodyLarge, color = VV.Text)
                        Text(clip.source, style = MaterialTheme.typography.bodyMedium, color = VV.TextDim)
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
