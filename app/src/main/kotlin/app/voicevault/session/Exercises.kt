package app.voicevault.session

import app.voicevault.R

/**
 * Gli esercizi, come dati.
 *
 * Derivati da "Voice Feminization for ABSOLUTE BEGINNERS" (TransVoiceLessons):
 * il punto non e' alzare l'altezza, e' imparare a **ridurre il peso** mentre si
 * sale. Il pitch tracker visivo del video qui diventa il drone, perche' in guida
 * non si puo' guardare niente.
 *
 * Le istruzioni arrivano dagli **esempi audio ritagliati dalla lezione**, non
 * da una voce sintetica che le descrive. La voce sintetica resta solo dove un
 * esempio non c'e'.
 */
data class Exercise(
    val id: String,
    /** Due o tre parole: va letto a un metro di distanza con la coda dell'occhio. */
    val title: String,
    val durationSec: Int,
    /** Esempio audio dalla lezione. Mentre suona il drone tace. */
    val exampleRes: Int? = null,
    /** Detto dalla voce sintetica, solo dove manca un esempio audio. */
    val spoken: String? = null,
    /** Moltiplicatore sulla nota target. null = drone spento. */
    val droneRatio: Float? = 1f,
    /** Se true, i frame di questo esercizio entrano nelle metriche della sessione. */
    val measured: Boolean = false,
)

data class Program(
    val id: String,
    val title: String,
    val exercises: List<Exercise>,
    val isCalibration: Boolean = false,
) {
    val totalSec: Int get() = exercises.sumOf { it.durationSec }
}

object Exercises {

    private val warmup = Exercise(
        id = "warmup",
        title = "Riscaldamento",
        durationSec = 150,
        spoken = "Riscaldamento. Respira con calma e fai un hum rilassato, " +
            "senza cercare nessuna nota. Senti il ronzio dove ti viene naturale.",
        droneRatio = 1f,
    )

    private val humSlide = Exercise(
        id = "hum_slide",
        title = "Hum e scivolata",
        durationSec = 180,
        exampleRes = R.raw.esempio_hum_slide,
        droneRatio = 1f,
    )

    private val heavyHeavyLight = Exercise(
        id = "heavy_heavy_light",
        title = "Pesante pesante leggero",
        durationSec = 180,
        exampleRes = R.raw.esempio_heavy_heavy_light,
        droneRatio = 1.5f,
    )

    private val carryover = Exercise(
        id = "carryover",
        title = "Nel parlato",
        durationSec = 150,
        exampleRes = R.raw.esempio_carryover,
        droneRatio = 1f,
        measured = true,
    )

    // --- Calibrazione: le prime sessioni servono a misurare te, non ad allenarti. ---

    private val calRange = Exercise(
        id = "cal_range",
        title = "Dove arrivi",
        durationSec = 150,
        exampleRes = R.raw.esempio_esplorazione,
        droneRatio = null,
        measured = true,
    )

    private val calSpeech = Exercise(
        id = "cal_speech",
        title = "La tua voce",
        durationSec = 120,
        spoken = "Adesso parla normale, come parli tutti i giorni. " +
            "Racconta com'e' andata la giornata, o cosa devi fare oggi. " +
            "Mi serve la tua voce di partenza.",
        droneRatio = null,
        measured = true,
    )

    val calibration = Program(
        id = "cal",
        title = "Calibrazione",
        exercises = listOf(warmup.copy(durationSec = 90), calRange, calSpeech),
        isCalibration = true,
    )

    /** Il programma della Fase 1: esercizi 0, 1, 2 e 5 del documento. */
    val programA = Program(
        id = "A",
        title = "Programma A",
        exercises = listOf(warmup, humSlide, heavyHeavyLight, carryover),
    )

    /** Quante sessioni di calibrazione servono prima di passare all'allenamento. */
    const val CALIBRATION_SESSIONS = 2
}
