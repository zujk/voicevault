package app.voicevault.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

data class AnalysisFrame(
    val tMs: Long,
    val pitch: PitchResult,
    val weight: WeightResult,
)

data class CaptureInfo(
    val sourceName: String,
    val inputDevice: String,
    val sampleRate: Int,
)

/**
 * Cattura dal microfono e analisi in tempo reale.
 *
 * Sorgente audio: `UNPROCESSED` quando l'hardware la dichiara, altrimenti
 * `VOICE_RECOGNITION`. Mai `VOICE_COMMUNICATION`: AGC, noise suppression ed echo
 * cancel alterano ampiezza e spettro, cioe' proprio le grandezze su cui si misura
 * il peso vocale. Quel che fa il DAC delle cuffie dentro al suo chip non e'
 * controllabile da qui: si misura dalla schermata di diagnostica.
 */
class AudioCapture(
    private val audioManager: AudioManager,
    private val sampleRate: Int = 44_100,
    private val onFrame: (AnalysisFrame) -> Unit,
) {
    private val windowSize = 2048
    private val hop = 1024

    private val detector = PitchDetector(sampleRate, windowSize)
    private val weightProxy = WeightProxy(sampleRate, windowSize)

    private var record: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var running = false

    var info: CaptureInfo? = null
        private set

    /** Dove finiscono i campioni grezzi, se c'e' una registrazione in corso. */
    var fileWriter: AacFileWriter? = null
    var pitchLog: PitchLog? = null

    @SuppressLint("MissingPermission") // il chiamante (SessionService) verifica RECORD_AUDIO
    fun start(): Boolean {
        if (running) return true

        val unprocessedSupported =
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessedSupported) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }

        val minBytes = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBytes <= 0) return false

        val r = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBytes * 2, hop * 2 * 8))
            .build()

        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return false
        }

        // Il microfono inline delle cuffie e' il punto di forza di tutto il setup:
        // lo si chiede esplicitamente invece di sperare nel routing automatico.
        val preferred = AudioDevices.usbInput(audioManager) ?: AudioDevices.builtinInput(audioManager)
        preferred?.let { r.setPreferredDevice(it) }

        info = CaptureInfo(
            sourceName = if (unprocessedSupported) "UNPROCESSED" else "VOICE_RECOGNITION",
            inputDevice = AudioDevices.describe(preferred),
            sampleRate = sampleRate,
        )

        record = r
        running = true
        r.startRecording()

        worker = thread(name = "vv-capture", priority = Thread.MAX_PRIORITY) { loop(r) }
        return true
    }

    private fun loop(r: AudioRecord) {
        val raw = ShortArray(hop)
        val window = FloatArray(windowSize)
        var samplesRead = 0L

        while (running) {
            val n = r.read(raw, 0, hop)
            if (n <= 0) continue

            fileWriter?.write(raw, n)

            // Finestra scorrevole: si scarta la parte piu' vecchia e si accoda la nuova.
            System.arraycopy(window, n, window, 0, windowSize - n)
            for (i in 0 until n) {
                window[windowSize - n + i] = raw[i] / 32768f
            }
            samplesRead += n

            if (samplesRead < windowSize) continue

            val tMs = samplesRead * 1000L / sampleRate
            val pitch = detector.detect(window)
            val weight = weightProxy.analyze(window, pitch)
            val frame = AnalysisFrame(tMs, pitch, weight)

            pitchLog?.append(frame)
            onFrame(frame)
        }
    }

    fun stop() {
        running = false
        worker?.join(500)
        worker = null
        record?.runCatching { stop() }
        record?.release()
        record = null
    }
}
