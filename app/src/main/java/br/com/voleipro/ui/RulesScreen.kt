package br.com.voleipro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.voleipro.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(session: Session, preferences: Preferences, model: LeagueViewModel, showMessage: (String) -> Unit) {
    val rules = session.rules
    val leagueId = session.pelada.id
    var target by rememberSaveable(rules.target) { mutableStateOf(rules.target.toString()) }
    var cap by rememberSaveable(rules.cap) { mutableStateOf(rules.cap.takeIf { it > 0 }?.toString().orEmpty()) }
    var limit by rememberSaveable(rules.winLimit) { mutableStateOf(rules.winLimit.takeIf { it > 0 }?.toString().orEmpty()) }
    var position by rememberSaveable(rules.returnPosition) { mutableStateOf(rules.returnPosition.toString()) }
    var byTwo by rememberSaveable(rules.winByTwo) { mutableStateOf(rules.winByTwo) }
    var setPointTieBreak by rememberSaveable(rules.setPointTieBreak) { mutableStateOf(rules.setPointTieBreak) }
    var bothLeave by rememberSaveable(rules.bothLeave) { mutableStateOf(rules.bothLeave) }
    var policy by rememberSaveable(rules.returnRule) { mutableStateOf(rules.returnRule) }
    var tie by rememberSaveable(rules.tieRule) { mutableStateOf(rules.tieRule) }
    var saving by remember { mutableStateOf(false) }
    val goal = target.toIntOrNull()?.takeIf { it in 1..999 }
    val standardScoring = setPointTieBreak == SetPointTieBreak.STANDARD
    val ceiling = if (cap.isBlank()) 0 else cap.toIntOrNull()?.takeIf { goal != null && it in goal..999 }
    val streak = if (limit.isBlank()) 0 else limit.toIntOrNull()?.takeIf { it in 1..999 }
    val returnPosition = position.toIntOrNull()?.takeIf { it in 1..999 }
    val valid = goal != null && (!standardScoring || ceiling != null) && streak != null &&
        (returnPosition != null || policy != ReturnRule.FIXED_POSITION || bothLeave || streak == 0)
    val draft = rules.copy(target = goal ?: rules.target, cap = if (standardScoring) ceiling ?: rules.cap else rules.cap,
        winByTwo = byTwo, winLimit = streak ?: rules.winLimit, bothLeave = bothLeave, returnRule = policy,
        returnPosition = returnPosition ?: rules.returnPosition, tieRule = tie, setPointTieBreak = setPointTieBreak)
    val hasChanges = !valid || draft != rules
    Page("Regras da liga") {
        Panel {
            Text("Pontuação", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(12, 15, 21, 25).forEach { preset ->
                    FilterChip(selected = goal == preset, onClick = { target = preset.toString() }, label = { Text(preset.toString()) })
                }
            }
            NumberField("Pontos para vencer", target, { target = it }, goal == null)
            Choice("Empate no set point", SetPointTieBreak.entries, setPointTieBreak, { it.label }) {
                setPointTieBreak = it
            }
            if (standardScoring) {
                Toggle("Exigir diferença de 2 pontos", byTwo) { byTwo = it }
                NumberField("Teto de pontos (opcional)", cap, { cap = it }, ceiling == null)
                if (ceiling == null) Text("O teto precisa ser igual ou maior que a meta.")
            } else if (goal != null) {
                val tieScore = goal - 1
                Text("No $tieScore×$tieScore, vence quem chegar primeiro a ${tieScore + setPointTieBreak.extraPoints}.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        Panel {
            Text("Rodízio", style = MaterialTheme.typography.titleMedium)
            Toggle("Rodízio total", bothLeave) { bothLeave = it }
            if (!bothLeave) {
                NumberField("Limite de vitórias (opcional)", limit, { limit = it }, streak == null)
                if (limit.isNotBlank()) Choice("Retorno do campeão", ReturnRule.entries, policy, { it.label }) { policy = it }
                if (limit.isNotBlank() && policy == ReturnRule.FIXED_POSITION) {
                    NumberField("Posição na fila", position, { position = it }, returnPosition == null)
                }
            }
            Choice("Se a partida terminar empatada", TieRule.entries, tie, { it.label }) { tie = it }
            Text(when {
                saving -> "Salvando regras…"
                hasChanges -> "Alterações pendentes"
                else -> "Regras em vigor"
            }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = valid && hasChanges && !saving,
                onClick = {
                    saving = true
                    model.update(leagueId, onSaved = {
                        saving = false
                        showMessage("Regras salvas e em vigor.")
                    }, onFailure = { saving = false }) { current ->
                        current.copy(rules = draft.copy(keepScreenOn = current.rules.keepScreenOn,
                            feedback = current.rules.feedback))
                    }
                }) { Text("Salvar regras") }
        }
        Panel {
            Text("Uso em quadra", style = MaterialTheme.typography.titleMedium)
            Toggle("Manter placar aceso", rules.keepScreenOn) { value ->
                model.update(leagueId) { it.copy(rules = it.rules.copy(keepScreenOn = value)) }
            }
            Toggle("Feedback de jogo", rules.feedback) { value ->
                model.update(leagueId) { it.copy(rules = it.rules.copy(feedback = value)) }
            }
            if (rules.feedback) {
                Toggle("Vibração", preferences.vibration) { value -> model.preferences { it.copy(vibration = value) } }
                Toggle("Som", preferences.sound) { value -> model.preferences { it.copy(sound = value) } }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, change: (String) -> Unit, error: Boolean) {
    OutlinedTextField(value, { if (it.all { c -> c in '0'..'9' } && it.length <= 3) change(it) },
        label = { Text(label) }, isError = error, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}

@Composable
fun Toggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f))
        Switch(checked, change)
    }
}

@Composable
fun <T> Choice(label: String, options: List<T>, selected: T?, text: (T) -> String, pick: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(if (selected == null) label else "$label · ${text(selected)}")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(text(option)) }, onClick = { expanded = false; pick(option) })
            }
        }
    }
}
