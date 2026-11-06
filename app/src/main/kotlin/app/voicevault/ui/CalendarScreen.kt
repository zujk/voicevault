package app.voicevault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.voicevault.VoiceVaultApp
import app.voicevault.data.DayCount
import app.voicevault.data.Insights
import app.voicevault.ui.theme.VV
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Calendario e insight. Si guarda a motore spento, quindi qui si puo' leggere.
 *
 * Un pallino per giorno: pieno = due sessioni, mezzo = una. Niente punteggi,
 * niente premi, nessuna pressione.
 */
@Composable
fun CalendarScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VoiceVaultApp
    val dao = app.db.sessions()

    var month by remember { mutableStateOf(YearMonth.now()) }
    val first = month.atDay(1)
    val last = month.atEndOfMonth()

    val counts by dao.dayCounts(first.toString(), last.toString()).collectAsState(emptyList())
    val recent by dao.recent(30).collectAsState(emptyList())

    val byDay = remember(counts) { counts.associateBy { it.dayKey } }
    val settings by app.settings.state.collectAsState()
    val insights = remember(recent, settings.targetHz) {
        Insights.forSessions(recent, settings.targetHz)
    }
    val streak = remember(recent) { currentStreak(recent.map { it.dayKey }.toSet()) }
    val monthMinutes = counts.sumOf { it.totalMs } / 60_000L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VV.Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextAction("← Indietro", onBack)
            Row {
                TextAction("‹", { month = month.minusMonths(1) })
                TextAction("›", { month = month.plusMonths(1) })
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = month.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).uppercase() +
                " ${month.year}",
            style = MaterialTheme.typography.titleLarge,
            color = VV.Text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$monthMinutes minuti questo mese · striscia di $streak " +
                if (streak == 1) "giorno" else "giorni",
            style = MaterialTheme.typography.bodyMedium,
            color = VV.TextDim,
        )

        Spacer(Modifier.height(18.dp))
        MonthGrid(month = month, byDay = byDay)
        Spacer(Modifier.height(24.dp))

        if (insights.isNotEmpty()) {
            insights.forEach { text ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(VV.Surface)
                        .padding(16.dp)
                ) {
                    Text(text, style = MaterialTheme.typography.bodyLarge, color = VV.Text)
                }
            }
        } else {
            Text(
                text = "Gli insight arrivano dopo qualche sessione: " +
                    "prima servono dei numeri su cui poggiare.",
                style = MaterialTheme.typography.bodyMedium,
                color = VV.TextDim,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("ULTIME SESSIONI", style = MaterialTheme.typography.labelLarge, color = VV.TextDim)
        Spacer(Modifier.height(10.dp))

        recent.take(10).forEach { s ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = s.dayKey + if (s.isCalibration) "  (calibrazione)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VV.Text,
                )
                Text(
                    text = buildString {
                        append("${s.durationMs / 60_000} min")
                        s.medianF0?.let { append(" · ${it.toInt()} Hz") }
                        s.signalQuality?.let {
                            if (it < 0.45f) append(" · segnale scarso")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = VV.TextDim,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, byDay: Map<String, DayCount>) {
    val firstDay = month.atDay(1)
    // In Italia la settimana parte da lunedi'.
    val leading = (firstDay.dayOfWeek.value + 6) % 7
    val days = month.lengthOfMonth()
    val cells = leading + days
    val rows = (cells + 6) / 7

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("L", "M", "M", "G", "V", "S", "D").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VV.Target,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cell = row * 7 + col
                    val dayOfMonth = cell - leading + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayOfMonth in 1..days) {
                            val key = month.atDay(dayOfMonth).toString()
                            DayDot(byDay[key]?.sessions ?: 0)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDot(sessions: Int) {
    val color = when {
        sessions >= 2 -> VV.Light
        sessions == 1 -> VV.Light.copy(alpha = 0.45f)
        else -> VV.SurfaceHigh
    }
    val dotSize = if (sessions > 0) 18.dp else 6.dp
    Box(
        modifier = Modifier
            .size(dotSize)
            .clip(CircleShape)
            .background(color)
    )
}

/** Giorni consecutivi fino a oggi, o fino a ieri se oggi non si e' ancora fatto niente. */
private fun currentStreak(days: Set<String>): Int {
    if (days.isEmpty()) return 0
    var cursor = LocalDate.now()
    if (!days.contains(cursor.toString())) {
        cursor = cursor.minusDays(1)
        if (!days.contains(cursor.toString())) return 0
    }
    var streak = 0
    while (days.contains(cursor.toString())) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}
