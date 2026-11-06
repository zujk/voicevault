package app.voicevault.session

import android.content.Context
import android.media.MediaPlayer

/**
 * Riproduce gli esempi audio della lezione.
 *
 * Sostituiscono le istruzioni dette dalla voce sintetica: un esempio vero, con
 * la dimostrazione vocale dentro, insegna quello che una descrizione a parole
 * non riesce a dire.
 */
class ExamplePlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    /**
     * Avvia l'esempio e ritorna subito: il conto alla rovescia dell'esercizio
     * non si ferma ad aspettarlo. [onFinished] scatta quando l'esempio e'
     * finito, ed e' li' che si riaccende il drone.
     */
    fun play(resId: Int, onFinished: () -> Unit) {
        stop()
        val p = runCatching { MediaPlayer.create(context, resId) }.getOrNull()
        if (p == null) {
            onFinished()
            return
        }
        // Il rilascio non si fa qui dentro: ci pensa stop(), a fine esercizio.
        p.setOnCompletionListener { onFinished() }
        player = p
        runCatching { p.start() }.onFailure { onFinished() }
    }

    fun stop() {
        val p = player ?: return
        player = null
        runCatching { if (p.isPlaying) p.stop() }
        runCatching { p.release() }
    }
}
