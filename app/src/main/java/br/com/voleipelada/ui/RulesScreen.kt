package br.com.voleipelada.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.voleipelada.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(rules: Rules, preferences: Preferences, model: LeagueViewModel) {
    var target by rememberSaveable(rules.target) { mutableStateOf(rules.target.toString()) }
    var cap by rememberSaveable(rules.cap) { mutableStateOf(rules.cap.takeIf { it > 0 }?.toString().orEmpty()) }
    var limit by rememberSaveable(rules.winLimit) { mutableStateOf(rules.winLimit.takeIf { it > 0 }?.toString().orEmpty()) }
    var position by rememberSaveable(rules.returnPosition) { mutableStateOf(rules.returnPosition.toString()) }
    var byTwo by rememberSaveable(rules.winByTwo) { mutableStateOf(rules.winByTwo) }
    var bothLeave by rememberSaveable(rules.bothLeave) { mutableStateOf(rules.bothLeave) }
    var policy by rememberSaveable(rules.returnRule) { mutableStateOf(rules.returnRule) }
    var tie by rememberSaveable(rules.tieRule) { mutableStateOf(rules.tieRule) }
    val goal = target.toIntOrNull()?.takeIf { it in 1..999 }
    val ceiling = if (cap.isBlank()) 0 else cap.toIntOrNull()?.takeIf { goal != null && it in goal..999 }
    val streak = if (limit.isBlank()) 0 else limit.toIntOrNull()?.takeIf { it in 1..999 }
    val returnPosition = position.toIntOrNull()?.takeIf { it in 1..999 }
    Page("Regras da pelada") {
        Panel {
            Text("Pontuação", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(12, 15, 21, 25).forEach { preset ->
                    FilterChip(selected = goal == preset, onClick = { target = preset.toString() }, label = { Text(preset.toString()) })
                }
            }
            NumberField("Pontos para vencer", target, { target = it }, goal == null)
            Toggle("Exigir diferença de 2 pontos", byTwo) { byTwo = it }
            NumberField("Teto de pontos (opcional)", cap, { cap = it }, ceiling == null)
            if (ceiling == null) Text("O teto precisa ser igual ou maior que a meta.")
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
            Choice("Em caso de empate", TieRule.entries, tie, { it.label }) { tie = it }
            Button(enabled = goal != null && ceiling != null && streak != null &&
                (returnPosition != null || policy != ReturnRule.FIXED_POSITION || bothLeave || streak == 0),
                onClick = {
                    model.update { s -> s.copy(rules = s.rules.copy(target = goal ?: rules.target,
                        cap = ceiling ?: 0, winByTwo = byTwo, winLimit = streak ?: 0, bothLeave = bothLeave,
                        returnRule = policy, returnPosition = returnPosition ?: rules.returnPosition, tieRule = tie)) }
                }) { Text("Salvar regras") }
        }
        Panel {
            Text("Uso em quadra", style = MaterialTheme.typography.titleMedium)
            Choice("Aparência", Appearance.entries, preferences.appearance, { it.label }) {
                model.preferences(preferences.copy(appearance = it))
            }
            Toggle("Manter placar aceso", rules.keepScreenOn) { value ->
                model.update { it.copy(rules = it.rules.copy(keepScreenOn = value)) }
            }
            Toggle("Feedback de jogo", rules.feedback) { value ->
                model.update { it.copy(rules = it.rules.copy(feedback = value)) }
            }
            if (rules.feedback) {
                Toggle("Vibração", preferences.vibration) { model.preferences(preferences.copy(vibration = it)) }
                Toggle("Som", preferences.sound) { model.preferences(preferences.copy(sound = it)) }
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
