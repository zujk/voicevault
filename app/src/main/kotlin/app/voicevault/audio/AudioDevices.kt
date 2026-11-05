package app.voicevault.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Selezione esplicita del dispositivo audio.
 *
 * Il setup e' cuffie USB-C con microfono inline: il mic sta a pochi centimetri
 * dalla bocca ed e' l'unica ragione per cui le metriche in auto sono affidabili.
 * Quindi l'app non si affida al routing automatico, lo chiede esplicitamente.
 */
object AudioDevices {

    private val USB_TYPES = intArrayOf(
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
    )

    fun usbInput(am: AudioManager): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type in USB_TYPES }

    fun usbOutput(am: AudioManager): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in USB_TYPES }

    fun builtinInput(am: AudioManager): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

    fun describe(device: AudioDeviceInfo?): String = when {
        device == null -> "nessuno"
        else -> "${device.productName} (${typeName(device.type)})"
    }

    fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_USB_HEADSET -> "headset USB"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "dispositivo USB"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "accessorio USB"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "mic interno"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker interno"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "cuffie con filo"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        else -> "tipo $type"
    }
}
