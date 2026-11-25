package br.com.voleipro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.voleipro.domain.*
import br.com.voleipro.domain.Composition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamsScreen(session: Session, leagueName: String, model: LeagueViewModel, onShareTeams: (Session, String) -> Unit) {
    var showPlayers by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val drag = remember(session.pelada.id) { PlayerDragState() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.fillMaxSize()) {
        Page("Times", modifier = Modifier.onGloballyPositioned { viewport = it.boundsInRoot() },
            scrollState = scrollState) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !showPlayers, onClick = { showPlayers = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Equipes") }
                SegmentedButton(selected = showPlayers, onClick = { showPlayers = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Jogadores") }
            }
            if (showPlayers) PlayersContent(session, model, scrollState, viewport, drag)
            else TeamsContent(session, model, onPlayers = { showPlayers = true },
                onShare = { onShareTeams(session, leagueName) })
        }
        if (showPlayers) session.players.find { it.id == drag.playerId }?.let { player ->
            DraggedPlayerPreview(player, session.team(player.assignedTeamId)?.name, drag, viewport,
                Modifier.matchParentSize())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamsContent(session: Session, model: LeagueViewModel,
                         onPlayers: () -> Unit, onShare: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var drawing by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf<Long?>(null) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { adding = true }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Novo time")
        }
        OutlinedButton(onClick = { drawing = true }) { Text("Sortear times") }
    }
    if (session.teams.isNotEmpty()) TextButton(onClick = onShare) {
        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Compartilhar times como imagem")
    }
    SectionTitle("Equipes", session.teams.size)
    if (session.teams.isEmpty()) Panel { Text("Nenhum time cadastrado.") }
    session.teams.forEach { team ->
        val status = when (session.court.indexOf(team.id)) {
            0 -> "Em quadra · Lado A"
            1 -> "Em quadra · Lado B"
            else -> if (team.id in session.queue) "Na fila" else "Fora do rodízio"
        }
        key(team.id) {
            TeamPanel(team, session.roster(team.id), status) { close ->
                DropdownMenuItem(text = { Text("Editar") }, onClick = { close(); editing = team.id })
                DropdownMenuItem(text = { Text("Remover") }, onClick = { close(); removing = team.id })
            }
        }
    }

    val team = session.team(editing)
    if (adding || team != null) {
        val close = { adding = false; editing = null }
        TeamEditor(session, team, model, onClose = close) { name, roster ->
            model.update(session.pelada.id, onSaved = close) { it.saveTeam(team?.id, name, roster) }
        }
    }
    if (drawing) DrawDialog(session, onClose = { drawing = false }, onPlayers = { drawing = false; onPlayers() },
        onDraw = { roster, size, replace, minWomen, minMen ->
            drawing = false
            model.draw(session.pelada.id, roster, size, replace, minWomen, minMen)
        })
    session.team(removing)?.let { removed ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remover ${removed.name}?") },
            text = { Text(if (removed.id in session.court) "O placar será zerado. Os jogadores ficarão sem time."
                else "Os jogadores ficarão sem time e continuarão cadastrados.") },
            confirmButton = { TextButton(onClick = {
                model.update(session.pelada.id) { it.removeTeam(removed.id) }
                removing = null
            }) { Text("Remover") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } })
    }
}

@Composable
fun QueueScreen(session: Session, model: LeagueViewModel) {
    var substitute by rememberSaveable { mutableStateOf<Int?>(null) }

    Page("Fila") {
        SectionTitle("Fila de espera", session.queue.size)
        if (session.queue.isEmpty()) Panel { Text("A fila está vazia.") }
        session.queue.forEachIndexed { index, id ->
            val team = session.team(id) ?: return@forEachIndexed
            key(id) {
                TeamPanel(team, session.roster(id), "${index + 1}º na fila", hasActions = session.queue.size > 1) { close ->
                    if (index > 0) DropdownMenuItem(text = { Text("Subir") },
                        onClick = { close(); model.move(session.pelada.id, id, -1) })
                    if (index < session.queue.lastIndex)
                        DropdownMenuItem(text = { Text("Descer") },
                            onClick = { close(); model.move(session.pelada.id, id, 1) })
                }
            }
        }

        SectionTitle("Em quadra", session.court.size)
        if (session.court.isEmpty()) Panel { Text("Nenhum time em quadra.") }
        session.court.forEachIndexed { side, id ->
            val team = session.team(id) ?: return@forEachIndexed
            key(id) {
                TeamPanel(team, session.roster(id), "Lado ${if (side == 0) "A" else "B"}",
                    hasActions = session.queue.isNotEmpty()) { close ->
                    DropdownMenuItem(text = { Text("Substituir") }, onClick = { close(); substitute = side })
                }
            }
        }
    }

    substitute?.let { side ->
        AlertDialog(onDismissRequest = { substitute = null }, title = { Text("Substituir time em quadra") },
            text = {
                Column {
                    Text("O time substituído vai ao fim da fila e o placar é zerado.")
                    Choice("Time que entra", session.queue.mapNotNull(session::team), null, { it.name }) { entrant ->
                        model.update(session.pelada.id) { it.substitute(side, entrant.id) }
                        substitute = null
                    }
                }
            }, confirmButton = { TextButton(onClick = { substitute = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun TeamPanel(team: Team, roster: List<Player>, status: String, hasActions: Boolean = true,
                      actions: @Composable (() -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(12.dp).background(Color(team.color), CircleShape))
            Column(Modifier.weight(1f)) {
                Text(team.name, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (hasActions) Box {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, "Opções de ${team.name}") }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    actions { expanded = false }
                }
            }
        }
        Text(roster.joinToString(" · ") { player ->
            if (player.position == PlayerPosition.UNSPECIFIED) player.name else "${player.name} (${player.position.label})"
        }.ifEmpty { "Sem jogadores" },
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TeamEditor(session: Session, team: Team?, model: LeagueViewModel, onClose: () -> Unit,
                       onSave: (String, Set<Long>) -> Unit) {
    var name by rememberSaveable(team?.id) { mutableStateOf(team?.name.orEmpty()) }
    var selected by rememberSaveable(team?.id) {
        mutableStateOf(team?.let { session.roster(it.id).map(Player::id).toLongArray() } ?: longArrayOf())
    }
    var addingPlayer by rememberSaveable { mutableStateOf(false) }
    var newNames by rememberSaveable { mutableStateOf(emptyArray<String>()) }
    val available = session.eligiblePlayers.filter { it.assignedTeamId == null || it.assignedTeamId == team?.id }
    val selectedPlayers = available.filter { it.id in selected }
    val validRoster = session.pelada.acceptsRoster(selectedPlayers)
    val addedIds = available.filter { it.name in newNames }.map { it.id }
    LaunchedEffect(addedIds) {
        if (addedIds.isNotEmpty()) {
            selected = (selected.toList() + addedIds).distinct().toLongArray()
            newNames = emptyArray()
        }
    }
    if (addingPlayer) {
        PlayerEditor(session, onClose = { addingPlayer = false }) { names, skill, present, sex, position ->
            model.update(session.pelada.id, onSaved = { newNames = names.toTypedArray(); addingPlayer = false }) {
                it.addPlayers(names, skill, present, sex, position)
            }
        }
        return
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(if (team == null) "Novo time" else "Editar time") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome do time") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                SectionTitle("Jogadores", selectedPlayers.size)
                if (session.pelada.mixed) Text(session.pelada.minimumsLabel(),
                    style = MaterialTheme.typography.bodySmall)
                if (available.isNotEmpty()) RosterPicker(available, isSelected = { it in selected }, onToggle = { id, checked ->
                    selected = if (checked) selected + id else selected.filterNot { it == id }.toLongArray()
                }, showSex = session.pelada.mixed)
                TextButton(onClick = { addingPlayer = true }) { Text("Cadastrar jogador") }
            }
        }, confirmButton = {
            Button(enabled = name.isNotBlank() && validRoster,
                onClick = { onSave(name.trim(), selectedPlayers.map { it.id }.toSet()) }) { Text("Salvar time") }
        }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
}

private fun Pelada.minimumsLabel() = "Mínimo por time · Feminino ${minWomen} · Masculino ${minMen}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrawDialog(session: Session, onClose: () -> Unit, onPlayers: () -> Unit,
                       onDraw: (Set<Long>, Int, Boolean, Int, Int) -> Unit) {
    val profile = session.pelada
    var size by rememberSaveable(profile.teamSize) { mutableIntStateOf(profile.teamSize) }
    var replace by rememberSaveable { mutableStateOf(false) }
    var mixed by rememberSaveable { mutableStateOf(false) }
    var minimum by rememberSaveable { mutableIntStateOf(1) }
    var excluded by rememberSaveable { mutableStateOf(longArrayOf()) }
    val eligible = session.drawCandidates(replace)
    val selected = eligible.filterNot { it.id in excluded }
    val selectedIds = selected.map { it.id }.toSet()
    val openMixed = mixed && profile.composition == Composition.OPEN
    val minWomen = if (profile.mixed) profile.minWomen else if (openMixed) minimum else 0
    val minMen = if (profile.mixed) profile.minMen else if (openMixed) 1 else 0
    val womenCount = selected.count { it.sex == PlayerSex.FEMALE }
    val menCount = selected.count { it.sex == PlayerSex.MALE }
    val issue = drawProblem(selected, size, minWomen, minMen)
    AlertDialog(onDismissRequest = onClose, title = { Text("Sortear times") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Formato", (2..6).toList(), size, { "${it}×${it}" }) {
                    size = it
                    if (size == 2) minimum = 1
                }
                if (profile.mixed) Text(profile.minimumsLabel(), style = MaterialTheme.typography.bodySmall)
                if (profile.composition == Composition.OPEN) Toggle("Sorteio misto", mixed) { mixed = it }
                if (openMixed) {
                    Text("Mínimo de jogadoras por time", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = minimum == 1, onClick = { minimum = 1 }, label = { Text("1 jogadora") })
                        FilterChip(selected = minimum == 2, onClick = { minimum = 2 }, enabled = size >= 3,
                            label = { Text("2 jogadoras") })
                    }
                    Text("Cada time terá também pelo menos um jogador do sexo masculino.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Toggle("Substituir times atuais", replace) { replace = it }
                Text(if (replace) "O sorteio substituirá os times, a fila e o placar e zerará a classificação."
                    else "Disponíveis para este sorteio: jogadores presentes e sem time.")
                SectionTitle("Selecionados", selected.size)
                if (profile.mixed || openMixed) Text("$womenCount feminino · $menCount masculino",
                    style = MaterialTheme.typography.labelMedium)
                if (issue != null) {
                    val message = when (issue.constraint) {
                        DrawConstraint.PLAYER_COUNT -> "Faltam ${issue.required - selected.size} jogadores na seleção."
                        DrawConstraint.TEAM_SIZE -> "Cada time precisa de pelo menos ${issue.required} jogadores. Altere o formato ou a seleção."
                        DrawConstraint.WOMEN -> "Faltam ${issue.required - womenCount} jogadoras na seleção."
                        DrawConstraint.MEN -> "Faltam ${issue.required - menCount} jogadores do sexo masculino na seleção."
                    }
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (eligible.isEmpty()) TextButton(onClick = onPlayers) { Text("Gerenciar jogadores") }
                else RosterPicker(eligible, isSelected = { it in selectedIds }, onToggle = { id, checked ->
                    excluded = if (checked) excluded.filterNot { it == id }.toLongArray() else excluded + id
                }, showSex = profile.mixed || openMixed)
            }
        }, confirmButton = {
            Button(enabled = issue == null, onClick = { onDraw(selectedIds, size, replace, minWomen, minMen) }) {
                Text("Sortear")
            }
        }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
}

@Composable
private fun RosterPicker(players: List<Player>, isSelected: (Long) -> Boolean,
                         onToggle: (Long, Boolean) -> Unit, showSex: Boolean = false) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 208.dp)) {
        items(players.sortedBy { it.name.lowercase() }, key = { it.id }) { player ->
            val checked = isSelected(player.id)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(checked, role = Role.Checkbox, onValueChange = { onToggle(player.id, it) }),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, onCheckedChange = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(player.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val details = listOfNotNull(
                        player.position.takeUnless { it == PlayerPosition.UNSPECIFIED }?.label,
                        player.sex.label.takeIf { showSex },
                    )
                    if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                Text(player.experience.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
