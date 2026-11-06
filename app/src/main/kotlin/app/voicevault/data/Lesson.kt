package app.voicevault.data

import app.voicevault.R

/**
 * La libreria delle lezioni.
 *
 * Predisposta per **piu' lezioni** anche se adesso ce n'e' una: aggiungerne
 * un'altra vuol dire aggiungere i file e una voce in [Library.lessons], senza
 * toccare gli esercizi, che pescano le clip per identificativo.
 */
data class Lesson(
    val id: String,
    val title: String,
    val author: String,
    /** Da dove viene, per poterla ritrovare. */
    val source: String,
    val clips: List<LessonClip>,
)

data class LessonClip(
    /** Identificativo stabile: e' questo che citano gli esercizi. */
    val id: String,
    val res: Int,
    val title: String,
    /** Minuto di partenza nella lezione originale. */
    val fromLabel: String,
    val durationSec: Int,
    val kind: ClipKind,
)

enum class ClipKind(val label: String) {
    TEORIA("Teoria"),
    PRATICA("Pratica"),
    DIMOSTRAZIONE("Dimostrazioni"),
}

object Library {

    private val beginners = Lesson(
        id = "tvl_beginners",
        title = "Voice Feminization for ABSOLUTE BEGINNERS",
        author = "TransVoiceLessons",
        source = "youtube.com/watch?v=BfCS01MkbIY",
        clips = listOf(
            LessonClip("01_introduzione", R.raw.lezione_01_introduzione,
                "Introduzione e panoramica", "0:27", 25, ClipKind.TEORIA),
            LessonClip("02_pitch", R.raw.lezione_02_pitch,
                "Chiave 1: l'altezza", "0:53", 64, ClipKind.TEORIA),
            LessonClip("03_peso", R.raw.lezione_03_peso,
                "Chiave 2: il peso vocale", "1:58", 51, ClipKind.TEORIA),
            LessonClip("04_interazione", R.raw.lezione_04_interazione,
                "Come altezza e peso si influenzano", "2:50", 70, ClipKind.TEORIA),
            LessonClip("05_mimica_pitch", R.raw.lezione_05_mimica_pitch,
                "Mimica dell'altezza", "3:57", 52, ClipKind.PRATICA),
            LessonClip("06_mimica_peso", R.raw.lezione_06_mimica_peso,
                "Mimica del peso", "4:49", 82, ClipKind.PRATICA),
            LessonClip("07_hum_slide", R.raw.lezione_07_hum_slide,
                "Esercizio 1: hum e scivolata", "6:12", 108, ClipKind.PRATICA),
            LessonClip("08_heavy_light", R.raw.lezione_08_heavy_light,
                "Esercizio 2: pesante pesante leggero", "8:00", 84, ClipKind.PRATICA),
            LessonClip("09_pitch_tracker", R.raw.lezione_09_pitch_tracker,
                "Esercizio 3: pitch tracker", "9:22", 115, ClipKind.PRATICA),
            LessonClip("10_creaking", R.raw.lezione_10_creaking,
                "Esercizio 4: crepitio", "11:19", 200, ClipKind.PRATICA),
            // Estratto piu' corto preso dentro la sezione del crepitio: nel primo
            // riassunto il passaggio al parlato era indicato al minuto 12:58.
            LessonClip("carryover_estratto", R.raw.esempio_carryover,
                "Estratto: portarlo nel parlato", "13:02", 47, ClipKind.PRATICA),
            LessonClip("11_dimostrazione", R.raw.lezione_11_dimostrazione,
                "Dimostrazione di una base femminilizzata", "14:38", 259, ClipKind.DIMOSTRAZIONE),
            LessonClip("12_risonanza", R.raw.lezione_12_risonanza,
                "Risonanza, riepilogo e consigli", "18:54", 373, ClipKind.DIMOSTRAZIONE),
        ),
    )

    val lessons: List<Lesson> = listOf(beginners)

    private val byId: Map<String, LessonClip> =
        lessons.flatMap { it.clips }.associateBy { it.id }

    /** Null se l'identificativo non esiste: un esercizio senza clip non si rompe. */
    fun clip(id: String): LessonClip? = byId[id]
}
