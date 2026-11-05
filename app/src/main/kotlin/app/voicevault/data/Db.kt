package app.voicevault.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Una riga per sessione. Tutto quello che serve a calendario e insight sta qui:
 * la traccia completa del pitch vive su file accanto all'audio.
 */
@Entity(tableName = "sessions")
data class TrainingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    /** "2026-11-05" — chiave di giornata per il calendario, in fuso locale. */
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "program_id") val programId: String,
    @ColumnInfo(name = "is_calibration") val isCalibration: Boolean = false,
    @ColumnInfo(name = "median_f0") val medianF0: Float? = null,
    @ColumnInfo(name = "p10_f0") val p10F0: Float? = null,
    @ColumnInfo(name = "p90_f0") val p90F0: Float? = null,
    @ColumnInfo(name = "pct_in_target") val pctInTarget: Float? = null,
    @ColumnInfo(name = "tilt_avg") val tiltAvg: Float? = null,
    /** 0..1 — quota di frame giudicati affidabili. In autostrada scende, e si vede. */
    @ColumnInfo(name = "signal_quality") val signalQuality: Float? = null,
    @ColumnInfo(name = "audio_path") val audioPath: String? = null,
    @ColumnInfo(name = "pitch_log_path") val pitchLogPath: String? = null,
    @ColumnInfo(name = "completed") val completed: Boolean = false,
)

@Entity(tableName = "segments")
data class ExerciseSegment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id", index = true) val sessionId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
    @ColumnInfo(name = "median_f0") val medianF0: Float? = null,
    @ColumnInfo(name = "max_f0") val maxF0: Float? = null,
    @ColumnInfo(name = "pct_in_target") val pctInTarget: Float? = null,
    @ColumnInfo(name = "tilt_avg") val tiltAvg: Float? = null,
)

data class DayCount(
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "sessions") val sessions: Int,
    @ColumnInfo(name = "total_ms") val totalMs: Long,
)

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: TrainingSession): Long

    @Update
    suspend fun update(session: TrainingSession)

    @Insert
    suspend fun insertSegment(segment: ExerciseSegment): Long

    @Query("SELECT * FROM sessions WHERE completed = 1 ORDER BY started_at DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<TrainingSession>>

    @Query("SELECT * FROM sessions WHERE completed = 1 ORDER BY started_at DESC LIMIT :limit")
    suspend fun recentOnce(limit: Int): List<TrainingSession>

    @Query(
        """
        SELECT day_key, COUNT(*) AS sessions, SUM(duration_ms) AS total_ms
        FROM sessions
        WHERE completed = 1 AND day_key BETWEEN :from AND :to
        GROUP BY day_key
        """
    )
    fun dayCounts(from: String, to: String): Flow<List<DayCount>>

    @Query("SELECT COUNT(*) FROM sessions WHERE completed = 1 AND is_calibration = 1")
    suspend fun calibrationSessionCount(): Int

}

@Database(entities = [TrainingSession::class, ExerciseSegment::class], version = 1, exportSchema = true)
abstract class VoiceVaultDb : RoomDatabase() {
    abstract fun sessions(): SessionDao
}
