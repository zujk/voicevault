package app.voicevault.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow

/**
 * Preferenze locali. SharedPreferences e non DataStore: sono tre valori,
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
        droneGain = prefs.getFloat(KEY_DRONE_GAIN, 0.25f),
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
    )

    private fun commit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = read()
    }

    fun setTargetHz(hz: Float) = commit {
        putFloat(KEY_TARGET_HZ, hz.coerceIn(MIN_TARGET_HZ, MAX_TARGET_HZ))
    }

    /**
     * Sposta il target di [semitones] semitoni e restituisce il valore nuovo.
     *
     * Il semitono e' l'unita' giusta: in auto conta fare pochi tocchi, e un
     * passo in Hz sarebbe grosso in basso e minuscolo in alto.
     */
    fun nudgeTarget(semitones: Int): Float {
        val next = current.targetHz * 2f.pow(semitones / 12f)
        setTargetHz(next)
        return current.targetHz
    }

    fun setDroneGain(gain: Float) = commit { putFloat(KEY_DRONE_GAIN, gain.coerceIn(0f, 0.6f)) }

    companion object {
        private const val KEY_TARGET_HZ = "target_hz"
        private const val KEY_DRONE_GAIN = "drone_gain"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

        /** ~G3: punto di partenza, da spostare a mano con i due pulsanti. */
        const val DEFAULT_TARGET_HZ = 196f
        const val MIN_TARGET_HZ = 100f
        const val MAX_TARGET_HZ = 320f
    }
}

data class SettingsState(
    val targetHz: Float,
    val droneGain: Float,
    val keepScreenOn: Boolean,
)
