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
import app.voicevault.data.RecordingStore
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
 * La sessione: drone, registrazione, cronometro. Niente altro.
 *
 * Prima qui c'era una macchina a stati con quattro esercizi, timer per
 * esercizio, voce sintetica e clip della lezione. Erano cinque sorgenti audio in
 * un orecchio solo, e ognuna doveva spegnerne un'altra con regole invisibili:
 * quando qualcosa non si sentiva era impossibile capire se fosse rotto o
 * zittito di proposito. Adesso il drone suona e basta, il materiale di studio si
 * ascolta da fermi nella schermata Lezione, e quello che si fa con la voce lo
 * decide chi guida.
 *
 * Gira come foreground service con tipo "microphone" perche' la sessione deve
 * continuare con lo schermo spento.
 */
class SessionService : Service() {

    companion object {
        const val ACTION_START = "app.voicevault.START"
        const val ACTION_STOP = "app.voicevault.STOP"
        const val ACTION_TARGET_UP = "app.voicevault.TARGET_UP"
        const val ACTION_TARGET_DOWN = "app.voicevault.TARGET_DOWN"

        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "session"

        /**
         * Limite di sicurezza: se la sessione resta aperta per sbaglio non
         * registra l'intera giornata.
         */
        private const val MAX_SESSION_MS = 20 * 60 * 1000L
        private const val MIN_KEEP_MS = 60_000L
        private const val SAMPLE_RATE = 44_100

        private val _state = MutableStateFlow(SessionUiState())
        val state: StateFlow<SessionUiState> = _state

        fun start(context: Context) = send(context, ACTION_START, foreground = true)
        fun stop(context: Context) = send(context, ACTION_STOP)
        fun targetUp(context: Context) = send(context, ACTION_TARGET_UP)
        fun targetDown(context: Context) = send(context, ACTION_TARGET_DOWN)

        private fun send(context: Context, action: String, foreground: Boolean = false) {
            val intent = Intent(context, SessionService::class.java).setAction(action)
            if (foreground) context.startForegroundService(intent) else context.startService(intent)
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
    private var focusRequest: AudioFocusRequest? = null

    private val stopping = AtomicBoolean(false)
    private val filter = MedianFilter5()
    private val metrics = MetricAccumulator()

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (sessionJob == null) startSession()
            ACTION_STOP -> stopping.set(true)
            ACTION_TARGET_UP -> nudge(1)
            ACTION_TARGET_DOWN -> nudge(-1)
        }
        return START_NOT_STICKY
    }

    /** Sposta la nota del drone di un semitono, subito e in modo udibile. */
    private fun nudge(semitones: Int) {
        val settings = (application as VoiceVaultApp).settings
        val next = settings.nudgeTarget(semitones)
        drone?.setFrequency(next)
        _state.value = _state.value.copy(targetHz = next)
    }

    private fun startSession() {
        startForeground(NOTIFICATION_ID, buildNotification())

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
        val targetHz = settings.current.targetHz

        val startedAt = System.currentTimeMillis()
        val sessionId = dao.insert(
            TrainingSession(
                startedAt = startedAt,
                dayKey = dayKeyFormat.format(Date(startedAt)),
                programId = PROGRAM_ID,
            )
        )

        val stem = "voicevault_${fileStampFormat.format(Date(startedAt))}"
        val target = RecordingStore.create(this, "$stem.m4a")
        recordingTarget = target
        val logFile = File(RecordingStore.fallbackDir(this), "$stem.csv")

        val log = PitchLog(logFile).also { it.setExercise(PROGRAM_ID) }
        val fileWriter = AacFileWriter(SAMPLE_RATE).apply {
            val fd = target.descriptor?.fileDescriptor
            if (fd != null) start(fd) else start(target.file!!)
        }
        val droneEngine = DroneEngine(audioManager)
        val cap = AudioCapture(audioManager, SAMPLE_RATE, this, ::onFrame).apply {
            this.fileWriter = fileWriter
            this.pitchLog = log
        }

        pitchLog = log
        writer = fileWriter
        drone = droneEngine
        capture = cap

        requestAudioFocus()
        droneEngine.start()
        droneEngine.setFrequency(targetHz)
        droneEngine.setGain(settings.current.droneGain)

        if (!cap.start()) {
            cleanup()
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return
        }

        _state.value = SessionUiState(running = true, targetHz = targetHz)

        val sessionStart = System.currentTimeMillis()
        while (!stopping.get()) {
            delay(200)
            val elapsed = System.currentTimeMillis() - sessionStart
            if (elapsed > MAX_SESSION_MS) break
            _state.value = _state.value.copy(elapsedSec = (elapsed / 1000L).toInt())
        }

        val durationMs = System.currentTimeMillis() - sessionStart
        val summary = metrics.summary(settings.current.targetHz)

        dao.update(
            TrainingSession(
                id = sessionId,
                startedAt = startedAt,
                dayKey = dayKeyFormat.format(Date(startedAt)),
                durationMs = durationMs,
                programId = PROGRAM_ID,
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

        _state.value = SessionUiState(finishedSessionId = sessionId)
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onFrame(frame: AnalysisFrame) {
        metrics.add(frame)
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

    /**
     * Ordine obbligato: prima si aspetta che il thread di cattura sia finito,
     * solo dopo si chiudono encoder e file.
     */
    private fun cleanup() {
        capture?.stop()
        capture = null
        drone?.stop()
        drone = null
        writer?.stop()
        writer = null
        recordingTarget?.finish(this)
        recordingTarget = null
        pitchLog?.close()
        pitchLog = null
        abandonAudioFocus()
        sessionJob = null
        stopping.set(false)
        filter.reset()
    }

    override fun onDestroy() {
        cleanup()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Focus in ducking per tutta la sessione: il drone e' continuo, quindi la
     * musica resta bassa dall'inizio alla fine invece di saltare su e giu'.
     * Il navigatore ha priorita' piu' alta e passa comunque sopra.
     */
    private fun requestAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_session),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
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
            .setContentText("Drone e registrazione in corso")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Termina", stopIntent).build())
            .setOngoing(true)
            .build()
    }

    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val fileStampFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
}

private const val PROGRAM_ID = "drone"
