package br.com.voleipro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import br.com.voleipro.domain.*
import kotlinx.coroutines.flow.first

private val QuickAddPositions = listOf(
    PlayerPosition.SETTER, PlayerPosition.LIBERO, PlayerPosition.PRIMARY_ATTACKER, PlayerPosition.SECONDARY_ATTACKER,
    PlayerPosition.OUTSIDE_HITTER, PlayerPosition.MIDDLE_BLOCKER, PlayerPosition.OPPOSITE,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSheet(session: Session, model: LeagueViewModel, player: Player?, onClose: () -> Unit) {
    val pelada = session.pelada
    var raw by rememberSaveable(pelada.id) { mutableStateOf(player?.name.orEmpty()) }
    var batch by rememberSaveable(pelada.id) { mutableStateOf(false) }
    var position by rememberSaveable(pelada.id) {
        val default = if (pelada.teamSize == 4) PlayerPosition.PRIMARY_ATTACKER else PlayerPosition.UNSPECIFIED
        mutableStateOf(player?.position ?: default)
    }
    var experience by rememberSaveable(pelada.id) { mutableStateOf(player?.experience ?: Experience.INTERMEDIATE) }
    var sex by rememberSaveable(pelada.id) {
        val default = pelada.composition.defaultPlayerSex.takeUnless { it == PlayerSex.UNSPECIFIED } ?: PlayerSex.MALE
        mutableStateOf(player?.sex ?: default)
    }
    var present by rememberSaveable(pelada.id) { mutableStateOf(player?.isPresent ?: true) }
    var addedCount by rememberSaveable(pelada.id) { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val names = (if (batch) raw.split(',', ';', '\n') else listOf(raw))
        .map(::normalizePlayerName).filter(String::isNotEmpty)
    val available = remember(session.players, names, player?.id) {
        if (player == null) session.availablePlayerNames(names)
        else names.filter { name ->
            session.players.none { it.id != player.id && it.name.equals(name, ignoreCase = true) }
        }
    }
    val tooLong = names.any { it.length > PLAYER_NAME_LIMIT }
    val validationError = when {
        tooLong -> "Use até $PLAYER_NAME_LIMIT caracteres por nome."
        names.isNotEmpty() && available.isEmpty() ->
            if (batch) "Esses nomes já estão cadastrados." else "Já existe um jogador com esse nome."
        else -> null
    }
    val feedback = saveError ?: validationError
    val canSave = available.isNotEmpty() && !tooLong && !saving

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !saving })
    val leaguePositions = PlayerPosition.forTeamSize(pelada.teamSize)
    val orderedPositions = QuickAddPositions.filter { it in leaguePositions }
    val positions = if (player == null) orderedPositions else
        (orderedPositions + player.position + PlayerPosition.UNSPECIFIED).distinct()
    val sexes = if (player == null) listOf(PlayerSex.MALE, PlayerSex.FEMALE) else PlayerSex.entries
    val theme = MaterialTheme.colorScheme
    val colors = if (theme.surface.luminance() < 0.5f) theme.copy(
        primary = Color(0xFFF5B7EA), onPrimary = Color(0xFF2B1226),
        primaryContainer = Color(0xFF4E2646), onPrimaryContainer = Color.White,
        surface = Color(0xFF161E31), onSurface = Color.White,
        surfaceVariant = Color(0xFF0E1424), onSurfaceVariant = Color(0xFF9EA7BD),
        outline = Color(0xFF333D54),
    ) else theme

    fun submit() {
        if (saving || !canSave || raw.isBlank()) return
        val submitted = raw
        saving = true
        saveError = null
        val showError: (String) -> Unit = { message ->
            saveError = message
            saving = false
        }
        if (player != null) {
            model.editPlayer(pelada.id, player.id, available.single(), experience, present, sex, position,
                onSaved = onClose, onFailure = showError)
            return
        }
        model.quickAddPlayers(pelada.id, available, experience, present, sex, position, onSaved = { count ->
            addedCount += count
            if (raw == submitted) raw = ""
            saving = false
        }, onFailure = showError)
    }

    MaterialTheme(colorScheme = colors) {
        ModalBottomSheet(
            onDismissRequest = { if (!saving) onClose() },
            sheetState = sheet,
            containerColor = colors.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outline) },
        ) {
            if (player == null) LaunchedEffect(sheet) {
                snapshotFlow { sheet.currentValue }.first { it == SheetValue.Expanded }
                focus.requestFocus()
                keyboard?.show()
            }
            Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 8.dp)) {
                        Text(if (player == null) "Novo jogador" else "Editar jogador",
                            style = MaterialTheme.typography.titleLarge)
                        if (addedCount > 0) Text(
                            if (addedCount == 1) "1 adicionado nesta sessão" else "$addedCount adicionados nesta sessão",
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = colors.primary, style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (player == null) TextButton(enabled = !saving, onClick = {
                        if (batch) raw = names.firstOrNull().orEmpty()
                        batch = !batch
                        saveError = null
                        focus.requestFocus()
                        keyboard?.show()
                    }) { Text(if (batch) "Um por vez" else "Colar lista") }
                }
                OutlinedTextField(
                    raw, { raw = it; saveError = null },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text(if (batch) "Nomes dos jogadores" else "Nome do jogador") },
                    singleLine = !batch, maxLines = if (batch) 3 else 1,
                    shape = MaterialTheme.shapes.medium,
                    isError = feedback != null,
                    supportingText = when {
                        feedback != null -> { { Text(feedback) } }
                        batch -> { { Text("Separe os nomes por vírgula ou quebra de linha.") } }
                        else -> null
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceVariant,
                        unfocusedContainerColor = colors.surfaceVariant,
                    ),
                    trailingIcon = if (player == null) {
                        {
                            FilledIconButton(onClick = ::submit, enabled = canSave) {
                                Icon(Icons.Default.Add, "Adicionar jogador")
                            }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words,
                        imeAction = if (batch) ImeAction.Default else ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAddChips("Posição", positions, position, { option ->
                        when (option) {
                            PlayerPosition.PRIMARY_ATTACKER -> "Atq Prim"
                            PlayerPosition.SECONDARY_ATTACKER -> "Atq Sec"
                            else -> option.label
                        }
                    }) { position = if (position == it) PlayerPosition.UNSPECIFIED else it }
                    QuickAddChips("Nível", Experience.entries, experience, {
                        if (it == Experience.INTERMEDIATE) "Médio" else it.label
                    }) { experience = it }
                    QuickAddChips("Gênero", sexes, sex, {
                        when (it) {
                            PlayerSex.MALE -> "Masc"
                            PlayerSex.FEMALE -> "Fem"
                            else -> it.label
                        }
                    }, isEnabled = { player != null || pelada.composition.accepts(it) }) { sex = it }
                    if (pelada.composition.accepts(sex)) Surface(color = colors.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                            val label = if (player == null) "Já marcar como presente na liga" else "Presente nesta liga"
                            Toggle(label, present) { present = it }
                        }
                    }
                }
                if (player != null) {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(enabled = !saving, onClick = onClose) { Text("Cancelar") }
                        Button(enabled = canSave, onClick = ::submit) { Text(if (saving) "Salvando…" else "Salvar") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> QuickAddChips(
    label: String, options: List<T>, selected: T, text: (T) -> String,
    isEnabled: (T) -> Boolean = { true }, onSelect: (T) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val checked = option == selected
                FilterChip(
                    selected = checked, enabled = isEnabled(option), shape = CircleShape,
                    onClick = { onSelect(option) }, label = { Text(text(option)) },
                    leadingIcon = if (checked) {
                        { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}
