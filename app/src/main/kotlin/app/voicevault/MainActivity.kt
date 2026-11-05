package app.voicevault

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import app.voicevault.session.SessionService
import app.voicevault.ui.CalendarScreen
import app.voicevault.ui.DiagnosticsScreen
import app.voicevault.ui.HomeScreen
import app.voicevault.ui.SessionScreen
import app.voicevault.ui.theme.VoiceVaultTheme
import kotlinx.coroutines.flow.MutableStateFlow

enum class Screen { Home, Session, Calendar, Diagnostics }

class MainActivity : ComponentActivity() {

    private val micGranted = MutableStateFlow(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        micGranted.value = result[Manifest.permission.RECORD_AUDIO] ?: micGranted.value
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        micGranted.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            VoiceVaultTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppRoot(
                            micGranted = micGranted,
                            onRequestPermissions = { requestPermissions() },
                        )
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }
}

@Composable
private fun AppRoot(
    micGranted: MutableStateFlow<Boolean>,
    onRequestPermissions: () -> Unit,
) {
    val sessionState by SessionService.state.collectAsState()
    val granted by micGranted.collectAsState()
    var screen by remember { mutableStateOf(Screen.Home) }

    // La sessione comanda la navigazione: se parte, si va alla schermata di guida.
    LaunchedEffect(sessionState.running) {
        screen = if (sessionState.running) Screen.Session else Screen.Home
    }

    when (screen) {
        Screen.Session -> SessionScreen(state = sessionState)
        Screen.Calendar -> CalendarScreen(onBack = { screen = Screen.Home })
        Screen.Diagnostics -> DiagnosticsScreen(
            micGranted = granted,
            onRequestPermission = onRequestPermissions,
            onBack = { screen = Screen.Home },
        )
        Screen.Home -> HomeScreen(
            micGranted = granted,
            finishedSessionId = sessionState.finishedSessionId,
            onRequestPermission = onRequestPermissions,
            onOpenCalendar = { screen = Screen.Calendar },
            onOpenDiagnostics = { screen = Screen.Diagnostics },
        )
    }
}
