package app.voicevault.audio

import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Stima indiretta del "peso vocale".
 *
 * Non esiste un misuratore di peso vocale: questi sono proxy acustici che
 * correlano con la percezione di voce densa/ronzante.
 *
 *  - [tiltDb]  inclinazione spettrale: energia sotto 1 kHz contro 1-5 kHz.
 *              Piu' alto = piu' energia nei bassi = voce piu' pesante.
 *  - [h1h2Db]  ampiezza della prima armonica contro la seconda. Piu' alto
 *              tende a indicare fonazione piu' "breathy"/leggera.
 *
 * I valori assoluti non significano niente da soli: la normalizzazione sulla
 * singola voce e sul singolo microfono arriva con la sonificazione, in Fase 2.
 * Per ora si misurano, si registrano e si mostrano grezzi in diagnostica.
 */
data class WeightResult(
    val tiltDb: Float,
    val h1h2Db: Float,
    val valid: Boolean,
) {
    companion object {
        val INVALID = WeightResult(0f, 0f, false)
    }
}

class WeightProxy(
    private val sampleRate: Int,
    private val fftSize: Int = 2048,
) {
    private val fft = Fft(fftSize)
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)
    private val mag = FloatArray(fftSize / 2)

    fun analyze(buffer: FloatArray, pitch: PitchResult): WeightResult {
        if (!pitch.voiced || buffer.size < fftSize) return WeightResult.INVALID

        for (i in 0 until fftSize) {
            re[i] = buffer[i] * fft.window[i]
            im[i] = 0f
        }
        fft.transform(re, im)
        for (i in mag.indices) {
            mag[i] = Math.sqrt((re[i] * re[i] + im[i] * im[i]).toDouble()).toFloat()
        }

        val binHz = sampleRate.toFloat() / fftSize
        var low = 0.0
        var high = 0.0
        for (i in mag.indices) {
            val hz = i * binHz
            val e = (mag[i] * mag[i]).toDouble()
            when {
                hz < 80f -> {} // scartato: sotto la voce c'e' solo il rumore dell'auto
                hz < 1000f -> low += e
                hz < 5000f -> high += e
            }
        }
        val tiltDb = if (high <= 0.0 || low <= 0.0) 0f else (10.0 * log10(low / high)).toFloat()

        val h1 = harmonicMagnitude(pitch.hz, binHz)
        val h2 = harmonicMagnitude(pitch.hz * 2f, binHz)
        val h1h2Db = if (h1 <= 0f || h2 <= 0f) 0f else 20f * log10(h1 / h2)

        return WeightResult(tiltDb, h1h2Db, valid = true)
    }

    /** Picco nei dintorni dell'armonica, per tollerare l'errore di quantizzazione dei bin. */
    private fun harmonicMagnitude(hz: Float, binHz: Float): Float {
        val center = (hz / binHz).roundToInt()
        if (center <= 0 || center >= mag.size) return 0f
        var peak = 0f
        for (i in (center - 2)..(center + 2)) {
            if (i in mag.indices && mag[i] > peak) peak = mag[i]
        }
        return peak
    }
}
