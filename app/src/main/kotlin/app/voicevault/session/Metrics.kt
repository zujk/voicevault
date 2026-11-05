package app.voicevault.session

import app.voicevault.audio.AnalysisFrame
import kotlin.math.pow

/**
 * Riassunto di un pezzo di sessione.
 *
 * Poche grandezze e tutte dichiarabili: se la qualita' del segnale e' bassa,
 * [signalQuality] lo dice e l'app lo mostra invece di far finta di niente.
 */
data class MetricSummary(
    val medianF0: Float,
    val p10F0: Float,
    val p90F0: Float,
    val maxF0: Float,
    val pctInTarget: Float,
    val tiltAvg: Float?,
    val signalQuality: Float,
    val voicedFrames: Int,
)

class MetricAccumulator {

    private val f0 = ArrayList<Float>(4096)
    private var tiltSum = 0.0
    private var tiltCount = 0
    private var framesWithSound = 0
    private var framesVoiced = 0

    fun add(frame: AnalysisFrame) {
        val p = frame.pitch
        if (p.rmsDb > SOUND_FLOOR_DB) framesWithSound++
        if (!p.voiced) return
        framesVoiced++
        f0.add(p.hz)
        if (frame.weight.valid) {
            tiltSum += frame.weight.tiltDb
            tiltCount++
        }
    }

    fun summary(targetHz: Float): MetricSummary? {
        if (f0.size < MIN_FRAMES) return null
        val sorted = f0.toFloatArray().also { it.sort() }

        val lo = targetHz * SEMITONE.pow(-TARGET_BAND_SEMITONES)
        val hi = targetHz * SEMITONE.pow(TARGET_BAND_SEMITONES)
        val inBand = sorted.count { it in lo..hi }

        return MetricSummary(
            medianF0 = percentile(sorted, 0.5f),
            p10F0 = percentile(sorted, 0.1f),
            p90F0 = percentile(sorted, 0.9f),
            maxF0 = sorted.last(),
            pctInTarget = inBand.toFloat() / sorted.size,
            tiltAvg = if (tiltCount > 0) (tiltSum / tiltCount).toFloat() else null,
            signalQuality = if (framesWithSound == 0) 0f
            else (framesVoiced.toFloat() / framesWithSound).coerceIn(0f, 1f),
            voicedFrames = framesVoiced,
        )
    }

    private fun percentile(sorted: FloatArray, q: Float): Float {
        if (sorted.isEmpty()) return 0f
        val idx = ((sorted.size - 1) * q).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    private companion object {
        const val SOUND_FLOOR_DB = -42f
        const val MIN_FRAMES = 20
        /** Mezzo tono in rapporto di frequenza. */
        val SEMITONE = 2f.pow(1f / 12f)
        const val TARGET_BAND_SEMITONES = 1.5f
    }
}

/** Filtro mediano a 5: taglia i salti d'ottava del pitch detector senza inseguirli. */
class MedianFilter5 {
    private val buf = FloatArray(5)
    private var filled = 0
    private var index = 0

    fun push(value: Float): Float {
        buf[index] = value
        index = (index + 1) % buf.size
        if (filled < buf.size) filled++
        val slice = buf.copyOf(filled)
        slice.sort()
        return slice[filled / 2]
    }

    fun reset() { filled = 0; index = 0 }
}
