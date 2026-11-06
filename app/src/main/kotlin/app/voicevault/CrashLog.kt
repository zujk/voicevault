package app.voicevault

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registra su file l'ultima eccezione non catturata.
 *
 * Esiste perche' questa app si prova in auto, dove non c'e' un cavo ne' adb:
 * se qualcosa va in crash, il motivo deve poter essere letto dalla schermata
 * di diagnostica invece di andare perso.
 */
object CrashLog {

    private const val FILE_NAME = "last_crash.txt"
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ITALY)

    fun install(context: Context) {
        val dir = context.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(File(dir, FILE_NAME), thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Per gli errori che si riescono a catturare: finiscono nello stesso file. */
    fun record(context: Context, where: String, error: Throwable) {
        runCatching { write(File(context.filesDir, FILE_NAME), where, error) }
    }

    fun read(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }

    private fun write(file: File, where: String, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        file.writeText(
            buildString {
                append(stamp.format(Date())).append('\n')
                append("thread: ").append(where).append('\n')
                append(trace.lineSequence().take(40).joinToString("\n"))
            }
        )
    }
}
