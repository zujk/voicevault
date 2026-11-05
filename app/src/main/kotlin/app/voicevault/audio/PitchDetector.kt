package app.voicevault.audio

import kotlin.math.log10
import kotlin.math.sqrt

/** Esito dell'analisi di una finestra. */
data class PitchResult(
    val hz: Float,
    /** 0..1 — quanto e' periodico il segnale. Sotto la soglia il valore di hz va ignorato. */
    val confidence: Float,
    val rmsDb: Float,
    val voiced: Boolean,
) {
    companion object {
        val SILENT = PitchResult(0f, 0f, -120f, false)
    }
}

/**
 * Pitch detection con l'algoritmo YIN (de Cheveigne & Kawahara, 2002).
 *
 * Scelta progettuale: ogni finestra restituisce anche una confidenza, e i frame
 * sotto soglia vengono scartati invece di produrre un numero inventato. In auto
 * capitera' (finestrino aperto, autostrada) e l'app deve dirlo, non mascherarlo.
 */
class PitchDetector(
    private val sampleRate: Int,
    val windowSize: Int = 2048,
    minHz: Float = 70f,
    maxHz: Float = 600f,
    private val threshold: Float = 0.15f,
    /** Sotto questo livello non si analizza nemmeno: e' silenzio o solo rumore. */
    private val minRmsDb: Float = -48f,
    /** Confidenza minima per considerare il frame affidabile. */
    private val minConfidence: Float = 0.55f,
) {
    private val tauMin = (sampleRate / maxHz).toInt().coerceAtLeast(2)
    private val tauMax = (sampleRate / minHz).toInt().coerceAtMost(windowSize / 2 - 1)

    private val diff = FloatArray(tauMax + 1)
    private val cmnd = FloatArray(tauMax + 1)

    fun detect(buffer: FloatArray): PitchResult {
        require(buffer.size >= windowSize)

        var sumSquares = 0.0
        for (i in 0 until windowSize) sumSquares += (buffer[i] * buffer[i]).toDouble()
        val rms = sqrt(sumSquares / windowSize).toFloat()
        val rmsDb = if (rms <= 1e-7f) -120f else 20f * log10(rms)
        if (rmsDb < minRmsDb) return PitchResult.SILENT.copy(rmsDb = rmsDb)

        val half = windowSize / 2

        // Funzione di differenza.
        for (tau in tauMin..tauMax) {
            var sum = 0.0
            for (i in 0 until half) {
                val d = buffer[i] - buffer[i + tau]
                sum += (d * d).toDouble()
            }
            diff[tau] = sum.toFloat()
        }

        // Normalizzazione cumulativa media.
        cmnd[0] = 1f
        var running = 0.0
        for (tau in tauMin..tauMax) {
            running += diff[tau].toDouble()
            cmnd[tau] = if (running <= 0.0) 1f else (diff[tau] * tau / running).toFloat()
        }

        // Primo minimo locale sotto soglia; in mancanza, il minimo globale.
        var bestTau = -1
        var tau = tauMin
        while (tau <= tauMax) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                bestTau = tau
                break
            }
            tau++
        }
        if (bestTau < 0) {
            var min = Float.MAX_VALUE
            for (t in tauMin..tauMax) {
                if (cmnd[t] < min) { min = cmnd[t]; bestTau = t }
            }
        }
        if (bestTau <= 0) return PitchResult.SILENT.copy(rmsDb = rmsDb)

        val refined = parabolicRefine(bestTau)
        val hz = sampleRate / refined
        val confidence = (1f - cmnd[bestTau]).coerceIn(0f, 1f)
        val voiced = confidence >= minConfidence && hz.isFinite() &&
            hz >= sampleRate / tauMax.toFloat() && hz <= sampleRate / tauMin.toFloat()

        return PitchResult(hz, confidence, rmsDb, voiced)
    }

    /** Interpolazione parabolica: senza, la risoluzione sui toni alti e' grossolana. */
    private fun parabolicRefine(tau: Int): Float {
        if (tau <= tauMin || tau >= tauMax) return tau.toFloat()
        val a = cmnd[tau - 1]
        val b = cmnd[tau]
        val c = cmnd[tau + 1]
        val denom = 2f * (2f * b - a - c)
        if (denom == 0f) return tau.toFloat()
        val shift = (c - a) / denom
        return tau + shift.coerceIn(-1f, 1f)
    }
}
