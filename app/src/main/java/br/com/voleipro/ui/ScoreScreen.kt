package br.com.voleipro.ui

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.voleipro.domain.*
import br.com.voleipro.domain.usecase.rotation.ProcessMatchEndUseCase
import br.com.voleipro.domain.usecase.rotation.directTieBreakTarget
import br.com.voleipro.domain.usecase.rotation.isSetPoint
import br.com.voleipro.domain.usecase.rotation.setWinner
import kotlinx.coroutines.delay
import java.util.Locale

internal fun clock(ms: Long): String {
    val seconds = ms / 1000
    return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScoreScreen(session: Session, model: LeagueViewModel, preferences: Preferences,
                playSound: (Boolean) -> Unit, fullscreen: Boolean, onFullscreen: () -> Unit, onTeams: () -> Unit) {
    val leagueId = session.pelada.id
    val match = session.match
    val rules = session.rules
    val directTieBreakTarget = session.directTieBreakTarget()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(match.startedAt) {
        now = System.currentTimeMillis()
        while (match.startedAt != null) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }
    val haptic = LocalHapticFeedback.current
    val setPoint = (0..1).firstOrNull(session::isSetPoint)
    var previousSetPoint by remember { mutableStateOf(setPoint) }
    LaunchedEffect(setPoint) {
        if (setPoint != null && setPoint != previousSetPoint && session.rules.feedback) {
            if (preferences.vibration) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (preferences.sound) playSound(true)
        }
        previousSetPoint = setPoint
    }
    val courtKey = "${session.round}/${session.court.joinToString()}"
    val scoreKey = "$courtKey/${match.scores}"
    var adjustedScore by rememberSaveable(courtKey) { mutableStateOf<String?>(null) }
    var manualEnd by rememberSaveable(courtKey) { mutableStateOf(false) }
    val winner = session.setWinner()
    LaunchedEffect(winner) { if (winner == null) adjustedScore = null }
    val showEnd = manualEnd || (winner != null && adjustedScore != scoreKey)
    var reset by rememberSaveable { mutableStateOf(false) }
    val ready = session.court.size == 2
    val scoreboard: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (side in 0..1) {
                val team = session.team(session.court[side]) ?: continue
                ScoreCard(team, session, side, preferences.appearance == Appearance.CONTRAST, fullscreen,
                Modifier.weight(1f), point = { delta ->
                    if (session.rules.feedback && preferences.vibration) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    if (session.rules.feedback && preferences.sound && delta > 0) playSound(false)
                    model.point(leagueId, side, delta)
                }, serve = { model.update(leagueId) { it.copy(match = it.match.copy(serve = side)) } })
            }
        }
    }
    if (fullscreen) {
        Column(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (directTieBreakTarget != null) ScoreLabel("Desempate · até $directTieBreakTarget")
                    if (ready) {
                        TextButton(onClick = { model.timer(leagueId) }) {
                            Text("${if (match.startedAt == null) "Iniciar" else "Pausar"} ${clock(match.duration(now))}")
                        }
                        TextButton(onClick = { model.swap(leagueId) }) { Text("Inverter lados") }
                        TextButton(onClick = { model.undo(leagueId) }, enabled = match.points.isNotEmpty()) { Text("Desfazer") }
                        TextButton(onClick = { manualEnd = true }) {
                            Text("Encerrar", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                IconButton(onClick = onFullscreen) { Icon(Icons.Default.Close, "Sair da tela cheia") }
            }
            if (ready) scoreboard(Modifier.fillMaxWidth().weight(1f))
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Monte pelo menos dois times para começar.", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onTeams) { Text("Montar times") }
                }
            }
        }
    } else Page("Placar ao vivo", brand = true) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (directTieBreakTarget != null) {
                ScoreLabel("Desempate · até $directTieBreakTarget")
            } else {
                ScoreLabel("${rules.target} pontos")
                if (rules.setPointTieBreak == SetPointTieBreak.STANDARD) {
                    if (rules.winByTwo) ScoreLabel("Diferença de 2")
                    if (rules.cap > 0) ScoreLabel("Teto ${rules.cap}")
                }
            }
            OutlinedButton(onClick = onFullscreen) { Text("Tela cheia") }
        }
        if (!ready) {
            Panel {
                Text("Monte pelo menos dois times para começar.", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onTeams) { Text("Montar times") }
            }
        } else {
            val controls: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { model.swap(leagueId) }) { Text("Inverter lados") }
                        OutlinedButton(onClick = { model.undo(leagueId) }, enabled = match.points.isNotEmpty()) { Text("Desfazer") }
                        TextButton(onClick = { reset = true }) { Text("Zerar placar") }
                    }
                    Panel {
                        Text("Cronômetro  ${clock(match.duration(now))}", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { model.timer(leagueId) }) {
                                Text(if (match.startedAt == null) "Iniciar" else "Pausar")
                            }
                            TextButton(onClick = {
                                model.update(leagueId) { it.copy(match = it.match.copy(elapsedMs = 0, startedAt = null)) }
                            }) { Text("Zerar tempo") }
                        }
                    }
                    Button(onClick = { manualEnd = true }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) { Text("Encerrar partida") }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 660.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        scoreboard(Modifier.weight(2f))
                        controls(Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        scoreboard(Modifier.fillMaxWidth())
                        controls(Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (session.queue.isNotEmpty()) {
            Panel {
                Text("Próximo na fila", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(session.team(session.queue.first())?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Zerar placar?") },
        text = { Text("Os pontos e o histórico de desfazer desta partida serão apagados.") },
        confirmButton = { TextButton(onClick = {
            model.update(leagueId) {
                it.copy(match = it.match.copy(scores = listOf(0, 0), points = emptyList(), serve = 0))
            }
            reset = false
            adjustedScore = null
        }) { Text("Zerar") } }, dismissButton = { TextButton(onClick = { reset = false }) { Text("Cancelar") } })
    if (showEnd) {
        val rotation = remember(session) { ProcessMatchEndUseCase()(session, now) }
        if (rotation != null) {
            val (next, result) = rotation
            val dismiss = { adjustedScore = scoreKey; manualEnd = false }
            AlertDialog(onDismissRequest = dismiss, title = { Text("Confirmar fim da partida") },
                text = {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${result.left.name} ${result.leftScore} × ${result.rightScore} ${result.right.name}",
                            fontWeight = FontWeight.Bold)
                        Text(if (result.winner == null) "Empate" else "${if (result.winner == 0) result.left.name else result.right.name} venceu")
                        listOf(result.left to result.leftReason, result.right to result.rightReason).forEach { (team, reason) ->
                            val position = next.queue.indexOf(team.id) + 1
                            Text("${team.name} · $reason" + if (position > 0) " · ${position}º na fila" else "")
                        }
                        val winnerTeam = when (result.winner) { 0 -> result.left; 1 -> result.right; else -> null }
                        if (winnerTeam != null && session.queue.size <= 1 && !session.rules.bothLeave &&
                            session.rules.winLimit > 0 && winnerTeam.wins + 1 >= session.rules.winLimit) {
                            Text(if (session.queue.isEmpty()) "Limite atingido. Sem times na fila, haverá revanche e a sequência será zerada."
                                else "Limite atingido. Com um time na fila, um dos times atuais permanece para completar a quadra.")
                        }
                        Text("Próximo jogo · ${next.court.joinToString(" × ") { next.team(it)?.name.orEmpty() }}")
                        Text("Fila · " + next.queue.joinToString(" → ") { next.team(it)?.name.orEmpty() })
                    }
                },
                confirmButton = { Button(onClick = {
                    if (session.rules.feedback && preferences.vibration) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (session.rules.feedback && preferences.sound) playSound(true)
                    dismiss()
                    model.finish(session)
                }) { Text("Confirmar e rotacionar times") } },
                dismissButton = { TextButton(onClick = dismiss) { Text("Continuar pontuando") } })
        }
    }
}

@Composable
private fun ScoreLabel(label: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ScoreCard(team: Team, session: Session, side: Int, highContrast: Boolean, fullscreen: Boolean,
                      modifier: Modifier, point: (Int) -> Unit, serve: () -> Unit) {
    val rules = session.rules
    val setPoint = session.isSetPoint(side)
    val status = when {
        rules.bothLeave -> "${team.wins} vitórias · Rodízio total"
        rules.winLimit == 0 && team.wins > 0 -> "Rei da Quadra · ${team.wins} vitórias"
        rules.winLimit > 0 && team.wins + 1 >= rules.winLimit -> "${team.wins} vit. · Joga pela última e sai!"
        else -> "${team.wins} vitórias"
    }
    val blueSide = side == 0
    val accent = if (blueSide) Color(0xFF0349BA) else Color(0xFFFFD819)
    val dark = if (highContrast) Color.Black else Color(0xFF142448)
    val foreground = if (fullscreen) Color(0xFF142448) else Color.White
    val muted = when {
        highContrast -> foreground
        fullscreen -> if (blueSide) Color(0xFF0349BA) else Color(0xFF6A5100)
        blueSide -> Color(0xFFDCE8FF)
        else -> Color(0xFFFFE779)
    }
    val isServing = session.match.serve == side
    val size = if (fullscreen) Modifier.fillMaxHeight() else Modifier.height(318.dp)
    Surface(modifier.then(size), shape = MaterialTheme.shapes.extraLarge,
        color = if (fullscreen) Color.White else dark, contentColor = foreground,
        border = if (fullscreen) androidx.compose.foundation.BorderStroke(1.dp, lerp(accent, Color.White, 0.66f)) else null) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val roomy = fullscreen && maxHeight > 260.dp
            val compact = fullscreen && maxHeight < 205.dp
            val backdrop = if (fullscreen || highContrast) Modifier
                else Modifier.background(Brush.verticalGradient(listOf(dark, Color(0xFF213869))))
            Column(Modifier.fillMaxSize().then(backdrop).padding(if (fullscreen) 10.dp else 11.dp),
                verticalArrangement = Arrangement.spacedBy(if (fullscreen) 2.dp else 5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Color(team.color), CircleShape))
                    Text(team.name, style = if (roomy) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(status, style = MaterialTheme.typography.labelMedium, minLines = if (fullscreen) 1 else 2,
                    maxLines = if (fullscreen) 1 else 2, color = muted, overflow = TextOverflow.Ellipsis)
                if (setPoint || !fullscreen) {
                    Box(Modifier.height(23.dp)) {
                        if (setPoint) {
                            Surface(shape = MaterialTheme.shapes.small, color = Color(0xFFFFD819), contentColor = Color(0xFF142448)) {
                                Text("SET POINT", Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                val scoreFill = if (highContrast) foreground else accent
                val scoreInk = if ((!highContrast && blueSide) || (highContrast && fullscreen)) Color.White else Color(0xFF142448)
                Button(onClick = { point(1) }, modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = scoreFill, contentColor = scoreInk),
                    contentPadding = PaddingValues(0.dp), shape = MaterialTheme.shapes.large) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val score = session.match.scores[side].toString()
                        val maximum = when (score.length) { 1 -> 72; 2 -> 65; 3 -> 52; else -> 42 }
                        val scale = when {
                            roomy -> maximum + 20
                            compact -> 38
                            else -> maximum
                        }
                        Text(score, fontSize = scale.sp, lineHeight = (scale + 6).sp,
                            fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1)
                        Text("+1 ponto", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { point(-1) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("−1", color = foreground)
                    }
                    TextButton(onClick = serve, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(if (isServing) "● Saque" else "Saque", color = if (isServing) muted else foreground,
                            fontWeight = if (isServing) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                    }
                }
            }
        }
    }
}
