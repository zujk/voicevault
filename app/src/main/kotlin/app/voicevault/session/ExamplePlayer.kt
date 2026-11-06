package app.voicevault.session

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import app.voicevault.audio.AudioDevices

/**
 * Riproduce gli esempi audio della lezione.
 *
 * Sostituiscono le istruzioni dette dalla voce sintetica: un esempio vero, con
 * la dimostrazione vocale dentro, insegna quello che una descrizione a parole
 * non riesce a dire.
 *
 * Attributi e dispositivo di uscita sono dichiarati espliciti come per il drone,
 * invece di affidarsi a `MediaPlayer.create` e al routing di default: con le
 * cuffie USB collegate l'esempio deve finire nello stesso orecchio del drone.
 */
class ExamplePlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    val isPlaying: Boolean
        get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    /**
     * Avvia l'esempio e ritorna subito: il conto alla rovescia dell'esercizio
     * non si ferma ad aspettarlo. [onFinished] scatta quando l'esempio e'
     * finito, ed e' li' che si riaccende il drone.
     */
    fun play(resId: Int, onFinished: () -> Unit) {
        stop()
        // Prima la via esplicita; se la risorsa non si apre come descrittore si
        // ricade su MediaPlayer.create, che almeno suona.
        val p = runCatching { explicitPlayer(resId) }.getOrNull()
            ?: runCatching { MediaPlayer.create(context, resId) }.getOrNull()
        if (p == null) {
            onFinished()
            return
        }
        // Il rilascio non si fa dentro il listener: ci pensa stop().
        p.setOnCompletionListener { onFinished() }
        player = p
        runCatching { p.start() }.onFailure {
            stop()
            onFinished()
        }
    }

    private fun explicitPlayer(resId: Int): MediaPlayer {
        val afd = context.resources.openRawResourceFd(resId)
            ?: error("la risorsa $resId non si apre come descrittore")
        val p = MediaPlayer()
        p.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        p.prepare()
        // Chiuso solo dopo prepare: prima il descrittore serve ancora.
        runCatching { afd.close() }
        val am = context.getSystemService(AudioManager::class.java)
        AudioDevices.usbOutput(am)?.let { p.setPreferredDevice(it) }
        return p
    }

    fun stop() {
        val p = player ?: return
        player = null
        runCatching { if (p.isPlaying) p.stop() }
        runCatching { p.release() }
    }
}
