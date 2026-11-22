package br.com.voleipelada.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.voleipelada.domain.League
import br.com.voleipelada.domain.standings
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(league: League, model: LeagueViewModel) {
    var showStandings by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Page("Histórico") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !showStandings, onClick = { showStandings = false },
                shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Partidas") }
            SegmentedButton(selected = showStandings, onClick = { showStandings = true },
                shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Classificação") }
        }
        if (showStandings) {
            val standings = remember(league) { league.standings() }
            if (league.standingsMatches.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Zerar classificação")
                }
            }
            if (standings.isEmpty()) Panel { Text("Nenhum time cadastrado.") }
            else Panel {
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    TableRow(listOf("Time", "J", "V", "D", "PP", "PC", "Saldo", "Aprov."), bold = true)
                    standings.forEach {
                        HorizontalDivider()
                        TableRow(listOf(it.team.name, it.games.toString(), it.wins.toString(), it.losses.toString(),
                            it.scored.toString(), it.conceded.toString(), it.balance.toString(), "${it.percent}%"))
                    }
                }
                Text("J · Jogos   V · Vitórias   D · Derrotas   PP · Pontos pró   PC · Pontos contra",
                    style = MaterialTheme.typography.labelSmall)
            }
        } else {
            MatchHistory(league, onClear = { confirmClear = true })
        }
    }
    if (confirmClear) {
        val close = { confirmClear = false }
        AlertDialog(onDismissRequest = close,
            title = { Text(if (showStandings) "Zerar classificação?" else "Limpar histórico de partidas?") },
            text = {
                Text(if (showStandings) "As estatísticas dos times voltam a zero. As partidas continuam no histórico."
                    else "Todas as partidas serão removidas do histórico, incluindo as de outras datas. A classificação será preservada.")
            },
            confirmButton = { TextButton(onClick = {
                close()
                if (showStandings) model.resetStandings() else model.clearHistory()
            }) {
                Text(if (showStandings) "Zerar classificação" else "Limpar histórico",
                    color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = close) { Text("Cancelar") } })
    }
}

private val UTC = TimeZone.getTimeZone("UTC")

// The date picker encodes a calendar date at UTC midnight. Group matches by local calendar date.
internal fun localDayAsUtc(time: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val local = Calendar.getInstance(zone).apply { timeInMillis = time }
    return Calendar.getInstance(UTC).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MatchHistory(league: League, onClear: () -> Unit) {
    var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val days = remember(league.history) { league.history.groupBy { localDayAsUtc(it.finishedAt) }.toSortedMap(reverseOrder()) }
    val dateFormat = remember {
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag("pt-BR")).apply { timeZone = UTC }
    }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val visibleDays = if (selectedDay == null) days else days.filterKeys { it == selectedDay }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picking = true }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(Icons.Default.DateRange, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(selectedDay?.let { dateFormat.format(Date(it)) } ?: "Selecionar data")
        }
        FilterChip(selected = selectedDay != null && selectedDay == localDayAsUtc(System.currentTimeMillis()),
            onClick = { selectedDay = localDayAsUtc(System.currentTimeMillis()) }, label = { Text("Hoje") })
        if (selectedDay != null) TextButton(onClick = { selectedDay = null }) { Text("Todas as datas") }
    }
    if (league.history.isNotEmpty()) {
        TextButton(onClick = onClear) {
            Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Limpar histórico")
        }
    }
    if (visibleDays.isEmpty()) Panel {
        Text(if (selectedDay == null) "Nenhuma partida finalizada." else "Nenhuma partida nesta data.")
    }
    visibleDays.forEach { (day, matches) ->
        SectionTitle(dateFormat.format(Date(day)), matches.size)
        matches.forEach { result ->
            Panel {
                Text("${result.left.name} ${result.leftScore} × ${result.rightScore} ${result.right.name}",
                    style = MaterialTheme.typography.titleMedium)
                Text("${timeFormat.format(Date(result.finishedAt))} · ${clock(result.durationMs)}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${result.left.name} · ${result.leftReason}")
                Text("${result.right.name} · ${result.rightReason}")
            }
        }
    }
    if (picking) {
        val compact = LocalConfiguration.current.screenHeightDp < 540
        val picker = rememberDatePickerState(initialSelectedDateMillis = selectedDay ?: localDayAsUtc(System.currentTimeMillis()),
            initialDisplayMode = if (compact) DisplayMode.Input else DisplayMode.Picker)
        DatePickerDialog(onDismissRequest = { picking = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                selectedDay = picker.selectedDateMillis
                picking = false
            }) { Text("Aplicar") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } }) {
            Column(Modifier.heightIn(max = if (compact) 220.dp else 540.dp).verticalScroll(rememberScrollState())) {
                DatePicker(state = picker, title = null)
            }
        }
    }
}

@Composable
private fun TableRow(values: List<String>, bold: Boolean = false) {
    Row {
        values.forEachIndexed { index, value ->
            Text(value, Modifier.width(if (index == 0) 150.dp else 52.dp).padding(vertical = 8.dp),
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        }
    }
}
