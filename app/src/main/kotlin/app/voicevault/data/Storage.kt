package app.voicevault.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File

/**
 * Dove finiscono le registrazioni.
 *
 * Prima stavano in `filesDir`, che e' privata e invisibile: non si vedevano da
 * nessuna parte. Ora vanno nella cartella pubblica **Registrazioni/VoiceVault**
 * via MediaStore, quindi si vedono nel gestore file del telefono e si copiano
 * dal PC via cavo, che e' il piano di backup previsto (nessun cloud).
 *
 * Se per qualche motivo MediaStore rifiuta, si ricade su una cartella privata
 * dell'app invece di perdere la registrazione.
 */
object RecordingStore {

    private const val FOLDER = "VoiceVault"

    class Target(
        /** Quello che finisce nel database: una uri content:// o un percorso. */
        val reference: String,
        val descriptor: ParcelFileDescriptor?,
        val file: File?,
        private val uri: Uri?,
    ) {
        /** Chiude il file e, su MediaStore, lo rende visibile alle altre app. */
        fun finish(context: Context) {
            runCatching { descriptor?.close() }
            // Copia locale: dentro un lambda Kotlin non propaga lo smart cast
            // di una proprieta' di classe.
            val target = uri ?: return
            runCatching {
                context.contentResolver.update(
                    target,
                    ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
        }
    }

    fun create(context: Context, displayName: String): Target {
        mediaStoreTarget(context, displayName)?.let { return it }
        val file = File(fallbackDir(context), displayName)
        return Target(reference = file.absolutePath, descriptor = null, file = file, uri = null)
    }

    private fun mediaStoreTarget(context: Context, displayName: String): Target? = runCatching {
        val relative = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${Environment.DIRECTORY_RECORDINGS}/$FOLDER"
        } else {
            "${Environment.DIRECTORY_MUSIC}/$FOLDER"
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, relative)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: return@runCatching null
        val pfd = context.contentResolver.openFileDescriptor(uri, "w")
            ?: return@runCatching null
        Target(reference = uri.toString(), descriptor = pfd, file = null, uri = uri)
    }.getOrNull()

    /** Nome leggibile della cartella pubblica, da mostrare nell'app. */
    fun folderLabel(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Registrazioni/$FOLDER" else "Music/$FOLDER"

    /** Dove vanno le tracce del pitch, e le registrazioni se MediaStore rifiuta. */
    fun fallbackDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "voicevault").apply { mkdirs() }
    }
}
