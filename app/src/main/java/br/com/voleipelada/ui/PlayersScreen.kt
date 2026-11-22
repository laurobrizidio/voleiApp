package br.com.voleipelada.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import br.com.voleipelada.domain.*

@Composable
fun PlayersContent(session: Session, model: LeagueViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var editor by rememberSaveable { mutableStateOf<Long?>(null) }
    var removing by rememberSaveable { mutableStateOf<Long?>(null) }
    val sorted = remember(session.players, query) {
        session.players.filter { it.name.contains(query.trim(), ignoreCase = true) }.sortedBy { it.name.lowercase() }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        val present = session.players.count(Player::isPresent)
        Text("$present de ${session.players.size} presentes", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Button(onClick = { editor = 0L }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Novo jogador")
        }
    }
    if (session.players.isNotEmpty()) {
        OutlinedTextField(query, { query = it }, label = { Text("Buscar jogador") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large)
    }
    if (sorted.isEmpty()) Panel {
        Text(if (query.isBlank()) "Cadastre jogadores para montar equipes e sortear times." else "Nenhum jogador encontrado.")
    }
    if (sorted.isNotEmpty()) Panel {
        sorted.forEachIndexed { index, player ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PlayerRow(player, session.team(player.assignedTeamId)?.name, onEdit = { editor = player.id },
                onDelete = { removing = player.id }, onPresence = { checked ->
                    model.update { current ->
                        current.copy(players = current.players.map {
                            if (it.id == player.id) it.copy(isPresent = checked) else it
                        })
                    }
                })
        }
    }
    session.players.find { it.id == removing }?.let { player ->
        val close = { removing = null }
        AlertDialog(onDismissRequest = close, title = { Text("Excluir ${player.name}?") },
            text = { Text(if (player.assignedTeamId == null) "O jogador será removido da lista de cadastro."
                else "O jogador será removido do cadastro e do time atual.") },
            confirmButton = { TextButton(onClick = {
                close()
                model.update { current -> current.copy(players = current.players.filterNot { it.id == player.id }) }
            }) { Text("Excluir jogador", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = close) { Text("Cancelar") } })
    }
    val selected = session.players.find { it.id == editor }
    if (editor == 0L || selected != null) {
        PlayerEditor(session, selected, onClose = { editor = null }) { names, skill, present, sex ->
            model.update(onSaved = {
                if (selected == null) query = ""
                editor = null
            }) {
                if (selected == null) it.addPlayers(names, skill, present, sex)
                else it.editPlayer(selected.id, names.single(), skill, present, sex)
            }
        }
    }
}

@Composable
private fun PlayerRow(player: Player, teamName: String?, onEdit: () -> Unit,
                      onDelete: () -> Unit, onPresence: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        val initials = player.name.split(' ').filter(String::isNotBlank).take(2).joinToString("") { it.take(1).uppercase() }
        Surface(Modifier.size(38.dp), shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text(initials, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(player.name, style = MaterialTheme.typography.titleSmall)
            Text(listOfNotNull(player.experience.label, player.sex.takeUnless { it == PlayerSex.UNSPECIFIED }?.label, teamName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar ${player.name}") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Excluir ${player.name}", tint = MaterialTheme.colorScheme.error)
        }
        Switch(player.isPresent, onPresence, modifier = Modifier
            .semantics { contentDescription = "Presença de ${player.name}" })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerEditor(session: Session, player: Player?, onClose: () -> Unit,
                         onSave: (List<String>, Int, Boolean, PlayerSex) -> Unit) {
    var raw by rememberSaveable(player?.id) { mutableStateOf(player?.name.orEmpty()) }
    var batch by rememberSaveable { mutableStateOf(false) }
    var experience by rememberSaveable(player?.id) { mutableStateOf(player?.experience ?: Experience.INTERMEDIATE) }
    var present by rememberSaveable(player?.id) { mutableStateOf(player?.isPresent ?: true) }
    var sex by rememberSaveable(player?.id) { mutableStateOf(player?.sex ?: PlayerSex.UNSPECIFIED) }
    val input = (if (batch) raw.split(',', ';', '\n') else listOf(raw)).map(::normalizePlayerName).filter(String::isNotEmpty)
    val names = if (player == null) session.availablePlayerNames(input)
        else input.filter { name -> name.length <= PLAYER_NAME_LIMIT &&
            session.players.none { it.id != player.id && it.name.equals(name, ignoreCase = true) } }
    val tooLong = input.any { it.length > PLAYER_NAME_LIMIT }
    val feedback = when {
        tooLong -> "Use até $PLAYER_NAME_LIMIT caracteres por nome."
        !batch && input.isNotEmpty() && names.isEmpty() -> "Já existe um jogador com esse nome."
        batch && names.size < input.size -> "${input.size - names.size} nome(s) repetido(s) serão ignorados."
        batch -> "Separe os nomes por vírgula ou quebra de linha."
        else -> null
    }
    val canSave = names.isNotEmpty() && !tooLong
    val save = { if (canSave) onSave(names, experience.level, present, sex) }
    AlertDialog(onDismissRequest = onClose, title = {
        Text(if (player != null) "Editar jogador" else if (batch) "Cadastro em lote" else "Novo jogador")
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
            if (player == null) TextButton(onClick = {
                batch = !batch
                if (!batch) raw = input.firstOrNull().orEmpty()
            }) { Text(if (batch) "Cadastrar um por vez" else "Adicionar vários nomes") }
            Choice("Sexo", PlayerSex.entries, sex, PlayerSex::label) { sex = it }
            Text("Nível de experiência", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Experience.entries.forEach { option ->
                    FilterChip(selected = experience == option, onClick = { experience = option },
                        label = { Text(option.label) })
                }
            }
            Text("Usado para equilibrar os times no sorteio.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Toggle("Presente hoje", present) { present = it }
        }
    }, confirmButton = {
        Button(enabled = canSave, onClick = save) {
            Text(if (batch && names.isNotEmpty()) "Cadastrar ${names.size}" else "Salvar")
        }
    }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
}
