package app.voicevault.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * Il drone di riferimento: un tono tenue e continuo sulla nota target.
 *
 * Serve a dare l'altezza a orecchio, senza guardare lo schermo. Quando sei vicina
 * ma non precisa senti il battimento; all'unisono il battimento scompare.
 *
 * Due scelte da non cambiare per sbaglio:
 *  - **mono duplicato sui due canali**: si indossa un auricolare solo, e se il drone
 *    finisse su un canale solo potrebbe finire nell'orecchio che non c'e';
 *  - **non e' un seno puro**: con due armoniche deboli si sente molto meglio sopra il
 *    rumore dell'auto a volume basso, e il battimento resta percepibile.
 *
 * Non serve bassa latenza (e' solo riproduzione), quindi AudioTrack basta e non
 * c'e' nessun motivo di tirare dentro NDK e Oboe. Servirebbe per il sidetone,
 * che e' rinviato.
 */
class DroneEngine(
    private val audioManager: AudioManager,
    private val sampleRate: Int = 48_000,
) {
    private var track: AudioTrack? = null
    private var worker: Thread? = null
    @Volatile private var running = false

    @Volatile private var targetHz = 196f
    @Volatile private var targetGain = 0f

    private var phase = 0.0
    private var currentHz = 196f
    private var currentGain = 0f

    /** Hz della nota di riferimento. Il cambio e' graduale, non a scatti. */
    fun setFrequency(hz: Float) { targetHz = hz.coerceIn(60f, 800f) }

    /** 0..1. A 0 il drone tace ma il motore resta acceso, senza click di stop/start. */
    fun setGain(gain: Float) { targetGain = gain.coerceIn(0f, 1f) }

    fun start() {
        if (running) return
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(CHUNK_FRAMES * 2 * 4 * 2)

        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        AudioDevices.usbOutput(audioManager)?.let { t.setPreferredDevice(it) }

        track = t
        running = true
        t.play()

        worker = thread(name = "vv-drone", priority = Thread.MAX_PRIORITY) {
            val buf = FloatArray(CHUNK_FRAMES * 2)
            try {
                while (running) {
                    render(buf)
                    t.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                }
            } catch (_: Throwable) {
                // Il drone che smette non deve portarsi dietro il processo.
            } finally {
                // Stesso principio della cattura: rilascia chi ha usato.
                runCatching { t.stop() }
                runCatching { t.release() }
                track = null
            }
        }
    }

    fun stop() {
        running = false
        val w = worker
        worker = null
        runCatching { w?.join(2_000) }
        phase = 0.0
        currentGain = 0f
        targetGain = 0f
    }

    private fun render(out: FloatArray) {
        val frames = out.size / 2
        for (i in 0 until frames) {
            // Glide e fade esponenziali: nessun salto udibile di tono o volume.
            currentHz += (targetHz - currentHz) * HZ_SMOOTH
            currentGain += (targetGain - currentGain) * GAIN_SMOOTH

            phase += 2.0 * PI * currentHz / sampleRate
            if (phase > 2.0 * PI) phase -= 2.0 * PI

            val s = (
                sin(phase) +
                    0.28 * sin(2.0 * phase) +
                    0.10 * sin(3.0 * phase)
                ) * 0.55 * currentGain

            val v = s.toFloat()
            out[i * 2] = v
            out[i * 2 + 1] = v // mono duplicato: vedi il commento in testa alla classe
        }
    }

    private companion object {
        const val CHUNK_FRAMES = 480 // 10 ms a 48 kHz
        const val HZ_SMOOTH = 0.0008f
        const val GAIN_SMOOTH = 0.0005f
    }
}
