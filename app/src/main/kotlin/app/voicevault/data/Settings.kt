package app.voicevault.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Preferenze locali. SharedPreferences e non DataStore: sono sei valori,
 * letti anche dal thread audio, e l'asincronia qui non paga.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("voicevault", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state

    val current: SettingsState get() = _state.value

    private fun read() = SettingsState(
        targetHz = prefs.getFloat(KEY_TARGET_HZ, DEFAULT_TARGET_HZ),
        calibrated = prefs.getBoolean(KEY_CALIBRATED, false),
        recalibrateRequested = prefs.getBoolean(KEY_RECALIBRATE, false),
        comfortableMaxHz = prefs.getFloat(KEY_COMFORT_MAX, 0f).takeIf { it > 0f },
        droneGain = prefs.getFloat(KEY_DRONE_GAIN, 0.25f),
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
    )

    private fun commit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = read()
    }

    /**
     * Esito della calibrazione: la zona comoda misurata nelle prime sessioni.
     * Il target del drone si posa qui, invece di venire da una tabella.
     */
    fun saveCalibration(medianSpeechHz: Float, comfortableMaxHz: Float) = commit {
        putFloat(KEY_TARGET_HZ, medianSpeechHz.coerceIn(120f, 260f))
        putFloat(KEY_COMFORT_MAX, comfortableMaxHz)
        putBoolean(KEY_CALIBRATED, true)
        // La richiesta e' soddisfatta. Se la misurazione invece fallisse, questo
        // metodo non viene chiamato e la richiesta resta in piedi per il giro dopo.
        putBoolean(KEY_RECALIBRATE, false)
    }

    /**
     * Chiede che la prossima sessione rimisuri la voce.
     *
     * Serve perche' la voce si muove: dopo qualche settimana il target misurato
     * all'inizio diventa il punto di partenza invece del bersaglio.
     */
    fun requestRecalibration(on: Boolean) = commit { putBoolean(KEY_RECALIBRATE, on) }

    // I setter manuali (target, volume del drone, schermo sempre acceso) arrivano
    // con la schermata delle impostazioni, in Fase 3. Finora decide la calibrazione.

    private companion object {
        const val KEY_TARGET_HZ = "target_hz"
        const val KEY_CALIBRATED = "calibrated"
        const val KEY_RECALIBRATE = "recalibrate_requested"
        const val KEY_COMFORT_MAX = "comfortable_max_hz"
        const val KEY_DRONE_GAIN = "drone_gain"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

        /** ~G3. Punto di partenza provvisorio, sostituito dalla calibrazione. */
        const val DEFAULT_TARGET_HZ = 196f
    }
}

data class SettingsState(
    val targetHz: Float,
    val calibrated: Boolean,
    /** La prossima sessione comincia con i 90 secondi di misurazione. */
    val recalibrateRequested: Boolean,
    val comfortableMaxHz: Float?,
    val droneGain: Float,
    val keepScreenOn: Boolean,
)
