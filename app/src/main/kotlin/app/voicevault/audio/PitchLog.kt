package app.voicevault.audio

import java.io.BufferedWriter
import java.io.File

/**
 * Traccia del pitch su file, accanto all'audio.
 *
 * Non va in Room: sono decine di migliaia di righe per sessione e servono solo
 * per ridisegnare il grafico. Il database tiene i riassunti.
 */
class PitchLog(file: File) {

    private val writer: BufferedWriter = file.bufferedWriter().apply {
        write("t_ms,exercise,hz,confidence,rms_db,voiced,tilt_db,h1h2_db\n")
    }

    private var exerciseId: String = ""

    fun setExercise(id: String) { exerciseId = id }

    fun append(frame: AnalysisFrame) {
        val p = frame.pitch
        val w = frame.weight
        runCatching {
            writer.write(
                buildString {
                    append(frame.tMs).append(',')
                    append(exerciseId).append(',')
                    append(fmt(p.hz)).append(',')
                    append(fmt(p.confidence)).append(',')
                    append(fmt(p.rmsDb)).append(',')
                    append(if (p.voiced) 1 else 0).append(',')
                    append(if (w.valid) fmt(w.tiltDb) else "").append(',')
                    append(if (w.valid) fmt(w.h1h2Db) else "")
                    append('\n')
                }
            )
        }
    }

    fun close() {
        runCatching { writer.flush() }
        runCatching { writer.close() }
    }

    private fun fmt(v: Float): String =
        if (!v.isFinite()) "" else String.format(java.util.Locale.US, "%.2f", v)
}
