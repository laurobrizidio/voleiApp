package br.com.voleipro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import br.com.voleipro.domain.*
import br.com.voleipro.domain.Composition
import kotlin.math.roundToInt

private val PLAYER_SECTION_TITLES = mapOf(
    PlayerPosition.SETTER to "Levantadores",
    PlayerPosition.LIBERO to "Líberos",
    PlayerPosition.PRIMARY_ATTACKER to "Ataque Principal",
    PlayerPosition.SECONDARY_ATTACKER to "Ataque Secundário",
    PlayerPosition.OUTSIDE_HITTER to "Ponteiros",
    PlayerPosition.MIDDLE_BLOCKER to "Centrais",
    PlayerPosition.OPPOSITE to "Opostos",
    PlayerPosition.UNSPECIFIED to "Sem posição definida",
)

internal val Composition.defaultPlayerSex: PlayerSex
    get() = when (this) {
        Composition.FEMALE -> PlayerSex.FEMALE
        Composition.MALE -> PlayerSex.MALE
        else -> PlayerSex.UNSPECIFIED
    }

@Composable
internal fun PlayersContent(session: Session, model: LeagueViewModel, scroll: ScrollState, viewport: Rect,
                            drag: PlayerDragState) {
    var query by rememberSaveable { mutableStateOf("") }
    var positionFilter by rememberSaveable(session.pelada.id) { mutableStateOf<PlayerPosition?>(null) }
    var addingPlayers by rememberSaveable(session.pelada.id) { mutableStateOf(false) }
    var editor by rememberSaveable { mutableStateOf<Long?>(null) }
    var removing by rememberSaveable { mutableStateOf<Long?>(null) }
    var addingPresent by remember(session.pelada.id) { mutableStateOf(false) }
    val dropPositions = PlayerPosition.forTeamSize(session.pelada.teamSize)
    val target = drag.target(dropPositions, viewport)
    ScrollWhileDragging(drag, scroll, viewport)
    val eligible = remember(session.players, session.pelada.composition) { session.eligiblePlayers }
    val byPosition = remember(eligible) {
        eligible.sortedBy { it.name.lowercase() }.groupBy(Player::position)
    }
    val activeFilter = positionFilter?.takeIf { it in byPosition }
    val search = remember(query) { normalizePlayerName(query) }
    val newName = remember(session.players, search) {
        if (search.isBlank()) null else session.availablePlayerNames(listOf(search)).singleOrNull()
    }
    val sections = remember(byPosition, activeFilter, search) {
        PLAYER_SECTION_TITLES.keys.mapNotNull { position ->
            if (activeFilter != null && position != activeFilter) return@mapNotNull null
            val players = byPosition[position].orEmpty().filter { it.name.contains(search, ignoreCase = true) }
            if (players.isEmpty()) null else position to players
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        val present = eligible.count(Player::isPresent)
        Row(Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PresenceToggle(present, eligible.size, "Presença de todos da liga") {
                model.update(session.pelada.id) { it.togglePresence() }
            }
            Column {
                Text("Todos da liga", style = MaterialTheme.typography.labelLarge)
                Text("$present de ${eligible.size} presentes", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = { addingPlayers = true }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Novo jogador")
        }
    }
    OutlinedTextField(query, { query = it }, label = { Text("Buscar jogador") }, singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = if (query.isEmpty()) null else {
            { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Limpar busca") } }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
    if (newName != null) {
        FilledTonalButton(
            enabled = !addingPresent,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val submittedQuery = query
                addingPresent = true
                model.update(session.pelada.id, onSaved = {
                    addingPresent = false
                    if (query == submittedQuery) {
                        query = newName
                        positionFilter = null
                    }
                }, onFailure = { addingPresent = false }) { current ->
                    current.addPlayers(listOf(newName), Experience.INTERMEDIATE.level, present = true,
                        sex = current.pelada.composition.defaultPlayerSex)
                }
            },
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (addingPresent) "Adicionando…" else "Adicionar “$newName” como presente")
        }
    }
    if (byPosition.size > 1) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = activeFilter == null, onClick = { positionFilter = null },
                label = { Text("Todos (${eligible.size})") })
            PLAYER_SECTION_TITLES.forEach { (position, title) ->
                if (position in byPosition) FilterChip(selected = activeFilter == position,
                    onClick = { positionFilter = if (activeFilter == position) null else position },
                    label = { Text(title) })
            }
        }
    }
    if (sections.isEmpty() && newName == null) Panel {
        Text(when {
            search.length > PLAYER_NAME_LIMIT -> "Use até $PLAYER_NAME_LIMIT caracteres por nome."
            search.isNotEmpty() -> "Nenhum jogador encontrado."
            eligible.isNotEmpty() -> "Nenhum jogador encontrado."
            session.pelada.composition == Composition.FEMALE -> "Nenhuma jogadora cadastrada."
            session.pelada.composition == Composition.MALE -> "Nenhum jogador do sexo masculino cadastrado."
            else -> "Cadastre jogadores para montar equipes e sortear times."
        })
    }
    if (sections.isNotEmpty()) Panel {
        val hiddenDropTargets = if (drag.playerId != null) {
            PLAYER_SECTION_TITLES.keys.filter { position ->
                position in dropPositions && sections.none { it.first == position }
            }.map { it to emptyList<Player>() }
        } else emptyList()
        (sections + hiddenDropTargets).forEach { (position, players) ->
            key(position) {
                val allInPosition = byPosition[position].orEmpty()
                val present = allInPosition.count(Player::isPresent)
                DisposableEffect(drag, position) {
                    onDispose { drag.sectionBounds.remove(position) }
                }
                Column(Modifier.onGloballyPositioned { drag.sectionBounds[position] = it.boundsInRoot() }) {
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
                        color = if (target == position) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (target == position) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface) {
                        val title = PLAYER_SECTION_TITLES.getValue(position)
                        Row(Modifier.fillMaxWidth().padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("$title ($present/${allInPosition.size} presentes)",
                                modifier = Modifier.weight(1f).semantics { heading() }.padding(vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge)
                            if (players.isNotEmpty()) {
                                PresenceToggle(present, allInPosition.size, "Presença da seção $title") {
                                    model.update(session.pelada.id) { it.togglePresence(position) }
                                }
                            }
                        }
                    }
                    players.forEachIndexed { index, player ->
                        key(player.id) {
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            PlayerGestureRow(player, drag, onDelete = { removing = player.id }, onDrop = {
                                val destination = drag.target(dropPositions, viewport)
                                if (destination != null && destination != player.position) {
                                    model.update(session.pelada.id) { current ->
                                        current.copy(players = current.players.map {
                                            if (it.id == player.id) it.copy(position = destination) else it
                                        })
                                    }
                                }
                            }) { dragModifier ->
                                PlayerRow(player, session.team(player.assignedTeamId)?.name, dragModifier,
                                    onEdit = { editor = player.id }, onPresence = { checked ->
                                        model.update(session.pelada.id) { current ->
                                            current.copy(players = current.players.map {
                                                if (it.id == player.id) it.copy(isPresent = checked) else it
                                            })
                                        }
                                    })
                            }
                        }
                    }
                }
            }
        }
    }
    eligible.find { it.id == removing }?.let { player ->
        val close = { removing = null }
        AlertDialog(onDismissRequest = close, title = { Text("Excluir ${player.name}?") },
            text = { Text("O jogador será removido do cadastro compartilhado e das escalações de todas as ligas.") },
            confirmButton = { TextButton(onClick = {
                close()
                model.update(session.pelada.id) { current ->
                    current.copy(players = current.players.filterNot { it.id == player.id })
                }
            }) { Text("Excluir jogador", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = close) { Text("Cancelar") } })
    }
    val selected = eligible.find { it.id == editor }
    if (addingPlayers || selected != null) {
        key(selected?.id) {
            PlayerSheet(session, model, selected, onClose = {
                addingPlayers = false
                editor = null
            })
        }
    }
}

@Composable
private fun PresenceToggle(present: Int, total: Int, label: String, onToggle: () -> Unit) {
    val state = when {
        present == 0 -> ToggleableState.Off
        present == total -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    TriStateCheckbox(state = state, onClick = onToggle, enabled = total > 0,
        modifier = Modifier.semantics { contentDescription = label })
}

@Composable
private fun PlayerRow(player: Player, teamName: String?, dragModifier: Modifier,
                      onEdit: () -> Unit, onPresence: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp)
        .clickable(role = Role.Button, onClickLabel = "Editar ${player.name}", onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically) {
        PlayerIdentity(player, teamName, Modifier.weight(1f).heightIn(min = 58.dp).then(dragModifier))
        Switch(player.isPresent, onPresence, modifier = Modifier
            .semantics { contentDescription = "Presença de ${player.name}" })
    }
}

@Composable
internal fun DraggedPlayerPreview(player: Player, teamName: String?, drag: PlayerDragState, viewport: Rect,
                                  modifier: Modifier = Modifier) {
    val bounds = drag.sourceBounds
    val (width, height) = with(LocalDensity.current) { bounds.width.toDp() to bounds.height.toDp() }
    val lift = remember(player.id) { Animatable(0.98f) }
    LaunchedEffect(lift) { lift.animateTo(1.02f, animationSpec = tween(140)) }
    Box(modifier.clipToBounds().clearAndSetSemantics {}) {
        Surface(Modifier.absoluteOffset {
            IntOffset((bounds.left - viewport.left).roundToInt(), (drag.liftedTop() - viewport.top).roundToInt())
        }.size(width, height).graphicsLayer {
            scaleX = lift.value
            scaleY = lift.value
        }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp) {
            PlayerIdentity(player, teamName, Modifier.fillMaxSize().padding(horizontal = 10.dp))
        }
    }
}

@Composable
private fun PlayerIdentity(player: Player, teamName: String?, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val initials = player.name.split(' ').filter(String::isNotBlank).take(2)
            .joinToString("") { it.take(1).uppercase() }
        Surface(Modifier.size(38.dp), shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text(initials, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(player.name, style = MaterialTheme.typography.titleSmall)
            Text(listOfNotNull(
                player.experience.label,
                player.sex.takeUnless { it == PlayerSex.UNSPECIFIED }?.label,
                teamName,
            ).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerEditor(session: Session, onClose: () -> Unit,
                         onSave: (List<String>, Int, Boolean, PlayerSex, PlayerPosition) -> Unit) {
    var raw by rememberSaveable { mutableStateOf("") }
    var batch by rememberSaveable { mutableStateOf(false) }
    var experience by rememberSaveable { mutableStateOf(Experience.INTERMEDIATE) }
    var present by rememberSaveable { mutableStateOf(true) }
    var sex by rememberSaveable {
        mutableStateOf(session.pelada.composition.defaultPlayerSex)
    }
    var position by rememberSaveable(session.pelada.id) {
        mutableStateOf(PlayerPosition.UNSPECIFIED)
    }
    val input = (if (batch) raw.split(',', ';', '\n') else listOf(raw)).map(::normalizePlayerName).filter(String::isNotEmpty)
    val names = session.availablePlayerNames(input)
    val tooLong = input.any { it.length > PLAYER_NAME_LIMIT }
    val feedback = when {
        tooLong -> "Use até $PLAYER_NAME_LIMIT caracteres por nome."
        !batch && input.isNotEmpty() && names.isEmpty() -> "Já existe um jogador com esse nome."
        batch && names.size < input.size -> "${input.size - names.size} nome(s) repetido(s) serão ignorados."
        batch -> "Separe os nomes por vírgula ou quebra de linha."
        else -> null
    }
    val canSave = names.isNotEmpty() && !tooLong
    val eligible = session.pelada.composition.accepts(sex)
    val save = { if (canSave) onSave(names, experience.level, present && eligible, sex, position) }
    AlertDialog(onDismissRequest = onClose, title = {
        Text(if (batch) "Cadastro em lote" else "Novo jogador")
    }, text = {
        Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(raw, { raw = it }, label = { Text(if (batch) "Nomes dos jogadores" else "Nome") },
                singleLine = !batch, maxLines = if (batch) 4 else 1, modifier = Modifier.fillMaxWidth(),
                isError = tooLong || !batch && input.isNotEmpty() && names.isEmpty(),
                supportingText = feedback?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words,
                    imeAction = if (batch) ImeAction.Default else ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }))
            TextButton(onClick = {
                batch = !batch
                if (!batch) raw = input.firstOrNull().orEmpty()
            }) { Text(if (batch) "Cadastrar um por vez" else "Adicionar vários nomes") }
            Choice("Sexo", PlayerSex.entries, sex, PlayerSex::label) { sex = it }
            Choice("Posição nesta liga", PlayerPosition.forTeamSize(session.pelada.teamSize),
                position, PlayerPosition::label) { position = it }
            Text("Nível de experiência", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Experience.entries.forEach { option ->
                    FilterChip(selected = experience == option, onClick = { experience = option },
                        label = { Text(option.label) })
                }
            }
            Text("Usado para equilibrar os times no sorteio.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (eligible) Toggle("Presente nesta liga", present) { present = it }
        }
    }, confirmButton = {
        Button(enabled = canSave, onClick = save) {
            Text(if (batch && names.isNotEmpty()) "Cadastrar ${names.size}" else "Salvar")
        }
    }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
}
