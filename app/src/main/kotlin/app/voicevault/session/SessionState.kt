package app.voicevault.session

/**
 * Quello che la UI deve sapere. Volutamente poca roba: in guida sullo schermo
 * finiscono il nome dell'esercizio, il tempo che resta e una barra di colore.
 */
data class SessionUiState(
    val running: Boolean = false,
    val programTitle: String = "",
    val isCalibration: Boolean = false,
    val exerciseTitle: String = "",
    val exerciseIndex: Int = 0,
    val exerciseCount: Int = 0,
    val exerciseRemainingSec: Int = 0,
    val sessionRemainingSec: Int = 0,
    /** Pitch filtrato, 0 se la voce non e' rilevata in questo momento. */
    val currentHz: Float = 0f,
    val targetHz: Float = 196f,
    val voiced: Boolean = false,
    /** Inclinazione spettrale: proxy del peso. null quando non misurabile. */
    val tiltDb: Float? = null,
    /** 0..1, aggiornata durante la sessione. Bassa = segnale sporco. */
    val signalQuality: Float = 1f,
    /** Vero mentre suona l'esempio della lezione: lo schermo lo dice. */
    val examplePlaying: Boolean = false,
    val finishedSessionId: Long? = null,
)
