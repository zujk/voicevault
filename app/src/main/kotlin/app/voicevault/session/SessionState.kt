package app.voicevault.session

/**
 * Quello che la UI deve sapere durante una sessione.
 *
 * La sessione e' una cosa sola: il drone suona, l'app registra, il cronometro
 * sale. Nessun esercizio, nessuna fase, nessuno stato da ricordare.
 */
data class SessionUiState(
    val running: Boolean = false,
    /** Secondi dall'inizio: sale, non scende. Non c'e' niente da finire. */
    val elapsedSec: Int = 0,
    /** Pitch filtrato, 0 quando la voce non e' rilevata in questo momento. */
    val currentHz: Float = 0f,
    val targetHz: Float = 196f,
    val voiced: Boolean = false,
    /** Inclinazione spettrale: proxy del peso. null quando non misurabile. */
    val tiltDb: Float? = null,
    val finishedSessionId: Long? = null,
)
