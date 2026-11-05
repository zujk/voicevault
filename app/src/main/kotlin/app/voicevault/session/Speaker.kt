package app.voicevault.session

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * La voce guida. E' il canale principale dell'app: lo schermo e' solo una spia.
 */
class Speaker(context: Context) {

    @Volatile private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            tts?.apply {
                language = Locale.ITALIAN
                setSpeechRate(0.95f)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            ready = true
        }
    }

    /** In coda: i prompt non si sovrappongono mai. */
    fun say(text: String) {
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    /** Interrompe quello che sta dicendo: serve quando salti un esercizio. */
    fun interruptWith(text: String) {
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun stopSpeaking() { tts?.stop() }

    fun release() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }
}
