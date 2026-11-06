package app.voicevault.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer

/**
 * Riproduce le clip della lezione, nella schermata Lezione.
 *
 * Non viene usato durante una sessione: la sessione e' solo drone e
 * registrazione, e il materiale di studio si ascolta da fermi.
 *
 * Attributi e dispositivo di uscita sono dichiarati espliciti, invece di
 * affidarsi a `MediaPlayer.create` e al routing di default: con le cuffie USB
 * collegate la clip deve finire nello stesso orecchio del drone.
 */
class ClipPlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    val isPlaying: Boolean
        get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    /** Avvia la clip e ritorna subito; [onFinished] scatta a fine riproduzione. */
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
