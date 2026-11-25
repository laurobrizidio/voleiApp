package br.com.voleipro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.voleipro.domain.Composition
import br.com.voleipro.domain.Pelada

@Composable
internal fun PeladaPicker(active: Pelada, profiles: List<Pelada>, onSelect: (Long) -> Unit,
                         onCreate: () -> Unit, onEdit: (Long) -> Unit, onHome: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = profiles.find { it.id == active.id } ?: active
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onHome) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar às ligas")
            }
            Box {
                TextButton(onClick = { expanded = true }) {
                    Column(Modifier.widthIn(max = 280.dp), horizontalAlignment = Alignment.Start) {
                        Text(current.displayName, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${current.format} · ${current.composition.label}", style = MaterialTheme.typography.labelMedium)
                    }
                    Icon(Icons.Default.ArrowDropDown, "Trocar liga")
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    profiles.forEach { profile ->
                        DropdownMenuItem(text = {
                            Column {
                                Text(profile.displayName)
                                Text("${profile.format} · ${profile.composition.label}",
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }, enabled = profile.id != current.id, onClick = {
                            expanded = false
                            onSelect(profile.id)
                        })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Nova liga") }, onClick = {
                        expanded = false
                        onCreate()
                    })
                    DropdownMenuItem(text = { Text("Editar liga atual") }, onClick = {
                        expanded = false
                        onEdit(current.id)
                    })
                }
            }
        }
    }
}

@Composable
internal fun PeladasList(
    profiles: List<Pelada>, activeId: Long?, onSelect: (Long) -> Unit, onEdit: (Long) -> Unit,
    onDelete: (Set<Long>, onSaved: () -> Unit, onFailure: (String) -> Unit) -> Unit,
) {
    var selection by rememberSaveable { mutableStateOf(longArrayOf()) }
    var pendingDeletion by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val ids = profiles.map(Pelada::id).toSet()
    val selected = selection.toSet().intersect(ids)
    val selecting = selected.isNotEmpty()
    val allSelected = selecting && selected.size == ids.size
    val selectAllState = when {
        allSelected -> ToggleableState.On
        selecting -> ToggleableState.Indeterminate
        else -> ToggleableState.Off
    }
    fun requestDeletion(targets: Set<Long>) {
        deleteError = null
        pendingDeletion = targets.toLongArray()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                if (profiles.isEmpty()) {
                    Panel { Text("Nenhuma liga cadastrada.") }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).heightIn(min = 48.dp)
                            .triStateToggleable(selectAllState, enabled = !deleting, role = Role.Checkbox, onClick = {
                                selection = if (allSelected) longArrayOf() else ids.toLongArray()
                            }), verticalAlignment = Alignment.CenterVertically) {
                            TriStateCheckbox(selectAllState, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
                            Text("Selecionar todas")
                        }
                        if (selecting) TextButton(enabled = !deleting, onClick = { requestDeletion(selected) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Excluir (${selected.size})")
                        }
                    }
                }
            }
            items(profiles, key = Pelada::id) { profile ->
                fun toggleSelection() {
                    selection = (if (profile.id in selected) selected - profile.id else selected + profile.id).toLongArray()
                }
                LigaCard(profile, active = profile.id == activeId,
                    selecting = selecting, selected = profile.id in selected, enabled = !deleting,
                    onOpen = {
                        if (selecting) toggleSelection() else onSelect(profile.id)
                    },
                    onEdit = { onEdit(profile.id) },
                    onToggle = ::toggleSelection,
                    onDelete = { requestDeletion(setOf(profile.id)) })
            }
        }
    }
    val targets = pendingDeletion?.toSet()?.intersect(ids).orEmpty()
    if (targets.isNotEmpty()) {
        val onlyLeague = profiles.singleOrNull { targets.size == 1 && it.id in targets }
        val close = { if (!deleting) pendingDeletion = null }
        AlertDialog(onDismissRequest = close, title = {
            Text(when {
                onlyLeague != null -> "Excluir ${onlyLeague.displayName}?"
                targets.size == profiles.size -> "Excluir todas as ligas?"
                else -> "Excluir ${targets.size} ligas?"
            })
        }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (onlyLeague != null) "Os times, o placar, as regras e o histórico desta liga serão excluídos. " +
                    "O cadastro de jogadores será mantido."
                else "Os times, os placares, as regras e os históricos das ligas selecionadas serão excluídos. " +
                    "O cadastro de jogadores será mantido.")
                deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {
            Button(enabled = !deleting, onClick = {
                deleting = true
                deleteError = null
                onDelete(targets, {
                    selection = (selection.toSet() - targets).toLongArray()
                    pendingDeletion = null
                    deleting = false
                }, { message ->
                    deleteError = message
                    deleting = false
                })
            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError)) {
                Text(if (deleting) "Excluindo…" else "Excluir")
            }
        }, dismissButton = {
            TextButton(enabled = !deleting, onClick = close) { Text("Cancelar") }
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LigaCard(
    profile: Pelada, active: Boolean, selecting: Boolean, selected: Boolean, enabled: Boolean,
    onOpen: () -> Unit, onEdit: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit,
) {
    val canSwipe = enabled && !selecting
    val requestDelete by rememberUpdatedState(onDelete)
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) {
            requestDelete()
            false
        } else true
    })
    SwipeToDismissBox(state = swipe, enableDismissFromStartToEnd = false, gesturesEnabled = canSwipe,
        modifier = Modifier.semantics {
            if (canSwipe) customActions = listOf(CustomAccessibilityAction("Excluir ${profile.displayName}") {
                requestDelete()
                true
            })
        }, backgroundContent = {
            Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer) {
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }) {
        Surface(shape = MaterialTheme.shapes.large, color = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            active -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(enabled = enabled, onClick = onOpen).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("${profile.format} · ${profile.composition.label}", style = MaterialTheme.typography.bodyMedium)
                    if (active) Text("Em uso", style = MaterialTheme.typography.labelMedium)
                }
                if (selecting) {
                    Checkbox(selected, enabled = enabled, onCheckedChange = { onToggle() },
                        modifier = Modifier.semantics {
                            contentDescription = "Selecionar ${profile.displayName}"
                        })
                } else {
                    IconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.padding(end = 6.dp)) {
                        Icon(Icons.Default.Edit, "Editar ${profile.displayName}")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PeladaEditor(profile: Pelada, model: LeagueViewModel, onClose: () -> Unit) {
    val creating = profile.id == 0L
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var size by rememberSaveable(profile.id) { mutableIntStateOf(profile.teamSize) }
    var composition by rememberSaveable(profile.id) { mutableStateOf(profile.composition) }
    var minWomen by rememberSaveable(profile.id) { mutableIntStateOf(profile.minWomen) }
    var minMen by rememberSaveable(profile.id) { mutableIntStateOf(profile.minMen) }
    var points by rememberSaveable(profile.id) { mutableStateOf("15") }
    var saving by remember(profile.id) { mutableStateOf(false) }
    var error by remember(profile.id) { mutableStateOf<String?>(null) }
    val target = points.toIntOrNull()
    val valid = name.isNotBlank() && (!creating || (target != null && target in 1..999))
    val close = { if (!saving) onClose() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !saving })
    ModalBottomSheet(onDismissRequest = close, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp)) {
            Text(if (creating) "Nova liga" else "Editar liga", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { if (it.length <= 60) name = it }, singleLine = true,
                    label = { Text("Nome da liga") }, modifier = Modifier.fillMaxWidth(), enabled = !saving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Column {
                    Text("Formato padrão", style = MaterialTheme.typography.titleSmall)
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (option in 2..6) {
                            FilterChip(selected = size == option, enabled = !saving, onClick = {
                                size = option
                                minWomen = minWomen.coerceAtMost(size - 1)
                                minMen = minMen.coerceAtMost(size - minWomen)
                            }, label = { Text("${option}×${option}") })
                        }
                    }
                }
                Column {
                    Text("Composição", style = MaterialTheme.typography.titleSmall)
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Composition.entries.forEach { option ->
                            FilterChip(selected = composition == option, enabled = !saving,
                                onClick = { composition = option }, label = { Text(option.label) })
                        }
                    }
                }
                if (composition == Composition.MIXED) {
                    Choice("Mínimo de jogadoras por time", (1..size - minMen).toList(), minWomen, Int::toString) {
                        minWomen = it
                    }
                    Choice("Mínimo de jogadores por time", (1..size - minWomen).toList(), minMen, Int::toString) {
                        minMen = it
                    }
                }
                if (creating) OutlinedTextField(points, { if (it.length <= 3 && it.all(Char::isDigit)) points = it },
                    label = { Text("Pontos por set") }, singleLine = true, enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
            }
            HorizontalDivider()
            error?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !saving, onClick = close) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(enabled = valid && !saving, onClick = {
                    saving = true
                    error = null
                    model.savePelada(profile.copy(name = name.trim(), teamSize = size, composition = composition,
                        minWomen = minWomen, minMen = minMen), target ?: 15, onSaved = onClose,
                        onFailure = { message -> error = message; saving = false })
                }) { Text(if (saving) "Salvando…" else if (creating) "Criar liga" else "Salvar") }
            }
        }
    }
}
