package app.voicevault.data

import kotlin.math.abs

/**
 * Gli insight: regole deterministiche, niente modelli.
 *
 * Massimo due per volta, e nessuna frase che non poggi su un numero misurato.
 * Se i dati non bastano, non si dice niente.
 */
object Insights {

    fun forSessions(sessions: List<TrainingSession>): List<String> {
        val done = sessions.filter { it.completed && !it.isCalibration }
        val out = mutableListOf<String>()

        trendSpeechPitch(done)?.let(out::add)
        signalWarning(done)?.let(out::add)
        newBest(done)?.let(out::add)
        gapNudge(sessions)?.let(out::add)

        return out.take(2)
    }

    /** Confronto fra le ultime 3 sessioni e le 3 precedenti: serve un minimo di storia. */
    private fun trendSpeechPitch(sessions: List<TrainingSession>): String? {
        val withF0 = sessions.mapNotNull { s -> s.medianF0?.let { s to it } }
        if (withF0.size < 6) return null
        val recent = withF0.take(3).map { it.second }.average()
        val before = withF0.drop(3).take(3).map { it.second }.average()
        val delta = recent - before
        if (abs(delta) < 4.0) return null
        return if (delta > 0) {
            "Il pitch mediano e' salito da ${before.toInt()} a ${recent.toInt()} Hz nelle ultime sessioni."
        } else {
            "Il pitch mediano e' sceso da ${before.toInt()} a ${recent.toInt()} Hz: " +
                "capita, spesso vuol dire stanchezza. Niente di grave."
        }
    }

    private fun signalWarning(sessions: List<TrainingSession>): String? {
        val last = sessions.firstOrNull() ?: return null
        val quality = last.signalQuality ?: return null
        if (quality >= 0.45f) return null
        return "Nell'ultima sessione il segnale era sporco (${(quality * 100).toInt()}%): " +
            "i numeri di quella sessione valgono poco. Controlla il microfono delle cuffie."
    }

    private fun newBest(sessions: List<TrainingSession>): String? {
        if (sessions.size < 3) return null
        val last = sessions.first().p90F0 ?: return null
        val previousBest = sessions.drop(1).mapNotNull { it.p90F0 }.maxOrNull() ?: return null
        if (last <= previousBest + 3f) return null
        return "Nuovo massimo comodo: ${last.toInt()} Hz, meglio dei ${previousBest.toInt()} Hz di prima."
    }

    private fun gapNudge(sessions: List<TrainingSession>): String? {
        val last = sessions.firstOrNull { it.completed } ?: return null
        val days = (System.currentTimeMillis() - last.startedAt) / 86_400_000L
        if (days < 3) return null
        return "Sono passati $days giorni dall'ultima sessione. Si riparte dal riscaldamento, con calma."
    }
}
