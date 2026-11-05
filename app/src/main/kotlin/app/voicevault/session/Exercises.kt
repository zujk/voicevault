package app.voicevault.session

/**
 * Gli esercizi, come dati.
 *
 * Derivati da "Voice Feminization for ABSOLUTE BEGINNERS" (TransVoiceLessons):
 * il punto non e' alzare l'altezza, e' imparare a **ridurre il peso** mentre si sale.
 * Il pitch tracker visivo del video qui diventa il drone, perche' in guida non si
 * puo' guardare niente.
 */
data class Exercise(
    val id: String,
    /** Due o tre parole: va letto a un metro di distanza con la coda dell'occhio. */
    val title: String,
    val durationSec: Int,
    /** Detto all'inizio dell'esercizio. */
    val intro: String,
    /** Secondo di offset -> frase detta. Pochi, brevi: una voce che commenta sempre stufa. */
    val cues: List<Cue> = emptyList(),
    /** Moltiplicatore sulla nota target. null = drone spento. */
    val droneRatio: Float? = 1f,
    /** Se true, i frame di questo esercizio entrano nelle metriche della sessione. */
    val measured: Boolean = false,
) {
    data class Cue(val atSec: Int, val text: String)
}

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
        intro = "Riscaldamento. Respira con calma e fai un hum rilassato, " +
            "senza cercare nessuna nota. Senti il ronzio dove ti viene naturale.",
        cues = listOf(
            Exercise.Cue(60, "Rilassa la mascella e le spalle. Niente sforzo."),
            Exercise.Cue(110, "Ora un hum un po' piu' lungo, sempre comodo."),
        ),
        droneRatio = 1f,
    )

    private val humSlide = Exercise(
        id = "hum_slide",
        title = "Hum e scivolata",
        durationSec = 180,
        intro = "Hum e scivolata. Parti da un hum basso e pesante, senti bene il ronzio. " +
            "Poi scivola verso l'alto togliendo peso, come se la voce diventasse piu' sottile. " +
            "Non serve arrivare in alto: serve arrivare leggera.",
        cues = listOf(
            Exercise.Cue(45, "Riparti dal basso, bello pesante. Poi su, piu' leggera."),
            Exercise.Cue(95, "Se senti tensione in gola, sei andata troppo su. Scendi un po'."),
            Exercise.Cue(140, "Ultime scivolate. Cerca il suono pulito, non quello alto."),
        ),
        droneRatio = 1f,
    )

    private val heavyHeavyLight = Exercise(
        id = "heavy_heavy_light",
        title = "Pesante pesante leggero",
        durationSec = 180,
        intro = "Pesante, pesante, leggero. Due suoni bassi e pesanti che salgono, " +
            "e poi un terzo piu' alto ma leggero e rilassato. " +
            "Il contrasto fra i primi due e il terzo e' tutto l'esercizio.",
        cues = listOf(
            Exercise.Cue(40, "Il terzo suono non deve essere piu' forte. Deve essere piu' leggero."),
            Exercise.Cue(90, "Senti il drone: il terzo suono sta da quelle parti."),
            Exercise.Cue(140, "Se il terzo ti costa fatica, abbassalo e tieni la leggerezza."),
        ),
        droneRatio = 1.5f,
    )

    private val carryover = Exercise(
        id = "carryover",
        title = "Nel parlato",
        durationSec = 150,
        intro = "Ora portiamolo nel parlato. Conta da uno a quattro con la voce leggera " +
            "che hai appena trovato. Poi una frase semplice, come se parlassi con qualcuno.",
        cues = listOf(
            Exercise.Cue(40, "Uno, due, tre, quattro. Senza spingere."),
            Exercise.Cue(80, "Adesso una frase tua. Qualunque cosa, ma parlata, non cantata."),
            Exercise.Cue(120, "Ultimo pezzo. Tieni il peso leggero fino alla fine della frase."),
        ),
        droneRatio = 1f,
        measured = true,
    )

    // --- Calibrazione: le prime sessioni servono a misurare te, non ad allenarti. ---

    private val calRange = Exercise(
        id = "cal_range",
        title = "Dove arrivi",
        durationSec = 150,
        intro = "Questa e' una sessione di calibrazione: sto misurando la tua voce, " +
            "non ti sto allenando. Fai delle scivolate dal basso verso l'alto, " +
            "fino a dove resti comoda. Fermati prima che faccia fatica.",
        cues = listOf(
            Exercise.Cue(60, "Ancora qualche scivolata. Fino al comodo, non oltre."),
            Exercise.Cue(110, "Ultime due, con calma."),
        ),
        droneRatio = null,
        measured = true,
    )

    private val calSpeech = Exercise(
        id = "cal_speech",
        title = "La tua voce",
        durationSec = 120,
        intro = "Adesso parla normale, come parli tutti i giorni. " +
            "Racconta com'e' andata la giornata, o cosa devi fare oggi. " +
            "Mi serve la tua voce di partenza.",
        cues = listOf(
            Exercise.Cue(60, "Continua, va bene qualsiasi cosa."),
        ),
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
