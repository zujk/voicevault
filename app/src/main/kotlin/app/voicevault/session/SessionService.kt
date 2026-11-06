package app.voicevault.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import app.voicevault.CrashLog
import app.voicevault.MainActivity
import app.voicevault.R
import app.voicevault.VoiceVaultApp
import app.voicevault.audio.AacFileWriter
import app.voicevault.audio.AnalysisFrame
import app.voicevault.audio.AudioCapture
import app.voicevault.audio.DroneEngine
import app.voicevault.audio.PitchLog
import app.voicevault.data.ExerciseSegment
import app.voicevault.data.RecordingStore
import app.voicevault.data.Settings
import app.voicevault.data.TrainingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orchestratore della sessione: registra, analizza, suona il drone, parla,
 * e alla fine scrive il riassunto nel database.
 *
 * Gira come foreground service con tipo "microphone" perche' la sessione deve
 * continuare con lo schermo spento: in auto non si tiene il telefono sveglio
 * per dodici minuti senza motivo.
 */
class SessionService : Service() {

    companion object {
        const val ACTION_START = "app.voicevault.START"
        const val ACTION_NEXT = "app.voicevault.NEXT"
        const val ACTION_STOP = "app.voicevault.STOP"

        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "session"

        /**
         * Stato condiviso con la UI. Un StateFlow di processo invece del binding
         * al service: l'app ha una sola Activity e questo evita un bel po' di
         * cerimonia per nessun vantaggio.
         */
        private val _state = MutableStateFlow(SessionUiState())
        val state: StateFlow<SessionUiState> = _state

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SessionService::class.java).setAction(ACTION_START))
        }

        fun next(context: Context) {
            context.startService(Intent(context, SessionService::class.java).setAction(ACTION_NEXT))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, SessionService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sessionJob: Job? = null

    private lateinit var audioManager: AudioManager
    private var capture: AudioCapture? = null
    private var drone: DroneEngine? = null
    private var writer: AacFileWriter? = null
    private var pitchLog: PitchLog? = null
    private var recordingTarget: RecordingStore.Target? = null
    private var speaker: Speaker? = null
    private var focusRequest: AudioFocusRequest? = null

    private val skip = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)

    private val filter = MedianFilter5()
    private val sessionMetrics = MetricAccumulator()
    private val perExercise = LinkedHashMap<String, MetricAccumulator>()

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (sessionJob == null) startSession()
            ACTION_NEXT -> skip.set(true)
            ACTION_STOP -> stopSession()
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- sessione

    private fun startSession() {
        startForeground(NOTIFICATION_ID, buildNotification("Sessione in corso"))

        sessionJob = scope.launch {
            try {
                runSession()
            } catch (t: Throwable) {
                CrashLog.record(this@SessionService, "sessione", t)
                _state.value = SessionUiState()
                cleanup()
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
            }
        }
    }

    private suspend fun runSession() {
            val app = application as VoiceVaultApp
            val dao = app.db.sessions()
            val settings = app.settings

            // Le prime sessioni misurano la voce invece di allenarla.
            val calibrationsDone = dao.calibrationSessionCount()
            val program = if (!settings.current.calibrated &&
                calibrationsDone < Exercises.CALIBRATION_SESSIONS
            ) {
                Exercises.calibration
            } else {
                Exercises.programA
            }

            val targetHz = settings.current.targetHz
            val startedAt = System.currentTimeMillis()
            val dayKey = dayKeyFormat.format(Date(startedAt))

            val sessionId = dao.insert(
                TrainingSession(
                    startedAt = startedAt,
                    dayKey = dayKey,
                    programId = program.id,
                    isCalibration = program.isCalibration,
                )
            )

            val stem = "voicevault_${fileStampFormat.format(Date(startedAt))}"
            val target = RecordingStore.create(this@SessionService, "$stem.m4a")
            recordingTarget = target
            val logFile = File(RecordingStore.fallbackDir(this@SessionService), "$stem.csv")

            val log = PitchLog(logFile)
            val fileWriter = AacFileWriter(SAMPLE_RATE).apply {
                val fd = target.descriptor?.fileDescriptor
                if (fd != null) start(fd) else start(target.file!!)
            }
            val droneEngine = DroneEngine(audioManager)
            val cap = AudioCapture(audioManager, SAMPLE_RATE, this@SessionService, ::onFrame).apply {
                this.fileWriter = fileWriter
                this.pitchLog = log
            }

            pitchLog = log
            writer = fileWriter
            drone = droneEngine
            capture = cap
            speaker = Speaker(this@SessionService)

            requestAudioFocus()
            droneEngine.start()

            val started = cap.start()
            if (!started) {
                speaker?.say("Non riesco ad accedere al microfono. Sessione annullata.")
                cleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

            _state.value = SessionUiState(
                running = true,
                programTitle = program.title,
                isCalibration = program.isCalibration,
                exerciseCount = program.exercises.size,
                targetHz = targetHz,
                sessionRemainingSec = program.totalSec,
            )

            if (program.isCalibration) {
                speaker?.say("Sessione di calibrazione, ${program.totalSec / 60} minuti.")
            } else {
                speaker?.say("${program.title}, ${program.totalSec / 60} minuti. Si parte.")
            }

            val sessionStartMs = System.currentTimeMillis()

            for ((index, exercise) in program.exercises.withIndex()) {
                if (stopping.get()) break

                val acc = MetricAccumulator()
                perExercise[exercise.id] = acc
                currentAccumulator = acc
                log.setExercise(exercise.id)
                filter.reset()

                val ratio = exercise.droneRatio
                if (ratio == null) {
                    droneEngine.setGain(0f)
                } else {
                    droneEngine.setFrequency(targetHz * ratio)
                    droneEngine.setGain(settings.current.droneGain)
                }

                val segmentStart = System.currentTimeMillis() - sessionStartMs
                _state.value = _state.value.copy(
                    exerciseTitle = exercise.title,
                    exerciseIndex = index,
                    exerciseRemainingSec = exercise.durationSec,
                )
                speaker?.interruptWith(exercise.intro)

                skip.set(false)
                val pending = exercise.cues.sortedBy { it.atSec }.toMutableList()
                val exerciseStart = System.currentTimeMillis()
                val endAt = exerciseStart + exercise.durationSec * 1000L

                while (!stopping.get() && !skip.get() && System.currentTimeMillis() < endAt) {
                    delay(200)
                    val now = System.currentTimeMillis()
                    val remaining = ((endAt - now) / 1000L).toInt().coerceAtLeast(0)
                    val sessionElapsed = (now - sessionStartMs) / 1000L
                    _state.value = _state.value.copy(
                        exerciseRemainingSec = remaining,
                        sessionRemainingSec = (program.totalSec - sessionElapsed).toInt().coerceAtLeast(0),
                    )
                    val sinceStart = ((now - exerciseStart) / 1000L).toInt()
                    while (pending.isNotEmpty() && pending.first().atSec <= sinceStart) {
                        speaker?.say(pending.removeAt(0).text)
                    }
                }

                val segmentEnd = System.currentTimeMillis() - sessionStartMs
                val exerciseSummary = acc.summary(targetHz * (ratio ?: 1f))
                dao.insertSegment(
                    ExerciseSegment(
                        sessionId = sessionId,
                        exerciseId = exercise.id,
                        position = index,
                        startMs = segmentStart,
                        endMs = segmentEnd,
                        medianF0 = exerciseSummary?.medianF0,
                        maxF0 = exerciseSummary?.maxF0,
                        pctInTarget = exerciseSummary?.pctInTarget,
                        tiltAvg = exerciseSummary?.tiltAvg,
                    )
                )
            }

            currentAccumulator = null
            droneEngine.setGain(0f)

            val durationMs = System.currentTimeMillis() - sessionStartMs
            val summary = sessionMetrics.summary(targetHz)

            if (program.isCalibration) {
                applyCalibration(settings)
            }

            dao.update(
                TrainingSession(
                    id = sessionId,
                    startedAt = startedAt,
                    dayKey = dayKey,
                    durationMs = durationMs,
                    programId = program.id,
                    isCalibration = program.isCalibration,
                    medianF0 = summary?.medianF0,
                    p10F0 = summary?.p10F0,
                    p90F0 = summary?.p90F0,
                    pctInTarget = summary?.pctInTarget,
                    tiltAvg = summary?.tiltAvg,
                    signalQuality = summary?.signalQuality,
                    audioPath = target.reference,
                    pitchLogPath = logFile.absolutePath,
                    completed = durationMs > MIN_KEEP_MS,
                )
            )

            if (!stopping.get()) {
                speaker?.interruptWith("Sessione finita. Bel lavoro.")
                delay(2500)
            }

            _state.value = SessionUiState(finishedSessionId = sessionId)
            cleanup()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
    }

    /**
     * Calibrazione: il target del parlato viene dalla mediana misurata in
     * "cal_speech", il tetto comodo dal novantesimo percentile di "cal_range".
     */
    private fun applyCalibration(settings: Settings) {
        val speech = perExercise["cal_speech"]?.summary(settings.current.targetHz)
        val range = perExercise["cal_range"]?.summary(settings.current.targetHz)
        if (speech == null || speech.voicedFrames < 200) {
            speaker?.say("Non ho abbastanza voce per calibrare: rifacciamo la calibrazione la prossima volta.")
            return
        }
        // Target: a meta' strada fra la voce attuale e il tetto comodo, cosi' e'
        // un passo raggiungibile e non un numero preso da una tabella.
        val comfortableMax = range?.p90F0 ?: (speech.medianF0 * 1.3f)
        val target = speech.medianF0 + (comfortableMax - speech.medianF0) * 0.45f
        settings.saveCalibration(medianSpeechHz = target, comfortableMaxHz = comfortableMax)
        speaker?.say("Calibrazione fatta. Partiamo da ${target.toInt()} hertz.")
    }

    @Volatile private var currentAccumulator: MetricAccumulator? = null

    private fun onFrame(frame: AnalysisFrame) {
        sessionMetrics.add(frame)
        currentAccumulator?.add(frame)

        val p = frame.pitch
        val shownHz = if (p.voiced) filter.push(p.hz) else 0f
        val s = _state.value
        if (!s.running) return
        _state.value = s.copy(
            currentHz = shownHz,
            voiced = p.voiced,
            tiltDb = frame.weight.tiltDb.takeIf { frame.weight.valid },
        )
    }

    private fun stopSession() {
        if (stopping.getAndSet(true)) return
        speaker?.stopSpeaking()
        skip.set(true)
    }

    /**
     * Ordine obbligato: prima si aspetta che il thread di cattura sia finito,
     * solo dopo si chiudono encoder e file. Al contrario si chiudeva un codec
     * che il thread audio stava ancora usando.
     */
    private fun cleanup() {
        capture?.stop()
        capture = null
        drone?.stop()
        drone = null
        writer?.stop()
        writer = null
        // Solo dopo che l'encoder ha chiuso il file lo si rende visibile.
        recordingTarget?.finish(this)
        recordingTarget = null
        pitchLog?.close()
        pitchLog = null
        speaker?.release()
        speaker = null
        abandonAudioFocus()
        sessionJob = null
        stopping.set(false)
        skip.set(false)
        perExercise.clear()
    }

    override fun onDestroy() {
        cleanup()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------- audio focus

    /**
     * Focus in ducking per tutta la sessione: il drone e' continuo, quindi la
     * musica resta bassa dall'inizio alla fine invece di saltare su e giu'.
     * Il navigatore ha priorita' piu' alta e passa comunque sopra.
     */
    private fun requestAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setWillPauseWhenDucked(false)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    // ------------------------------------------------------------- notifica

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_session),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(null, "Termina", stopIntent).build()
            )
            .setOngoing(true)
            .build()
    }

    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val fileStampFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
}

private const val SAMPLE_RATE = 44_100
private const val MIN_KEEP_MS = 60_000L
