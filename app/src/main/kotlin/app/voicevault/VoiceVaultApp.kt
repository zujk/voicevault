package app.voicevault

import android.app.Application
import androidx.room.Room
import app.voicevault.data.Settings
import app.voicevault.data.VoiceVaultDb

class VoiceVaultApp : Application() {

    lateinit var db: VoiceVaultDb
        private set

    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, VoiceVaultDb::class.java, "voicevault.db").build()
        settings = Settings(this)
    }
}
