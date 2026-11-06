package app.voicevault.session


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
    /**
     * Identificativo della clip della lezione da far sentire all'avvio.
     * Mentre suona il drone tace. Vedi app.voicevault.data.Library.
     */
    val exampleClipId: String? = null,
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
        exampleClipId = "07_hum_slide",
        droneRatio = 1f,
    )

    private val heavyHeavyLight = Exercise(
        id = "heavy_heavy_light",
        title = "Pesante pesante leggero",
        durationSec = 180,
        exampleClipId = "08_heavy_light",
        droneRatio = 1.5f,
    )

    private val carryover = Exercise(
        id = "carryover",
        title = "Nel parlato",
        durationSec = 150,
        exampleClipId = "carryover_estratto",
        droneRatio = 1f,
        measured = true,
    )

    // --- Misurazione: 90 secondi in testa alla prima sessione, una volta sola. ---
    //
    // Prima era una sessione dedicata di 6 minuti. Era troppa cerimonia per
    // misurare una mediana e un tetto: 40 secondi di parlato e 50 di scivolate
    // bastano, e cosi' il primo giro e' comunque un allenamento.
    //
    // Qui la voce sintetica resta di proposito: un esempio della lezione
    // occuperebbe il tempo che serve a sentire *te*, e non si misurerebbe niente.

    private val measureSpeech = Exercise(
        id = "cal_speech",
        title = "La tua voce",
        durationSec = 40,
        spoken = "Prima di cominciare ti misuro la voce. Parla normale per " +
            "mezzo minuto: raccontami cosa devi fare oggi.",
        droneRatio = null,
        measured = true,
    )

    private val measureRange = Exercise(
        id = "cal_range",
        title = "Dove arrivi",
        durationSec = 50,
        spoken = "Ora qualche scivolata dal basso verso l'alto, fino a dove resti " +
            "comoda. Fermati prima che faccia fatica: mi serve il tuo tetto, non il massimo.",
        droneRatio = null,
        measured = true,
    )

    /** Il programma della Fase 1: esercizi 0, 1, 2 e 5 del documento. */
    val programA = Program(
        id = "A",
        title = "Programma A",
        exercises = listOf(warmup, humSlide, heavyHeavyLight, carryover),
    )

    /**
     * Il programma della sessione.
     *
     * Quando serve misurare la voce — la prima volta, o perche' e' stata chiesta
     * una ricalibrazione — i due esercizi di misurazione si mettono in testa al
     * programma normale e il riscaldamento si accorcia, cosi' la sessione resta
     * sotto i dodici minuti ed e' comunque un allenamento. Se la misurazione non
     * riesce (poca voce) si riprova al giro dopo, senza contatori da tenere.
     */
    fun programFor(needsMeasurement: Boolean): Program {
        if (!needsMeasurement) return programA
        return Program(
            id = "A0",
            title = "Sessione con misurazione",
            exercises = listOf(warmup.copy(durationSec = 90), measureSpeech, measureRange) +
                programA.exercises.drop(1),
            isCalibration = true,
        )
    }
}
