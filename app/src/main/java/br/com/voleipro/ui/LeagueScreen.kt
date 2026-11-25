package br.com.voleipro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import br.com.voleipro.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.voleipro.domain.*
import kotlinx.coroutines.launch

private enum class Section(val label: String, val icon: ImageVector? = null) {
    SCORE("Placar"),
    TEAMS("Times", Icons.Default.Person),
    QUEUE("Fila", Icons.AutoMirrored.Filled.List),
    RULES("Regras", Icons.Default.Settings),
    HISTORY("Histórico", Icons.Default.DateRange),
}

private const val PELADAS_ROUTE = "peladas"
private const val SETTINGS_ROUTE = "configuracoes"

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(26.dp),
)

private val AppTypography = Typography().let { defaults ->
    defaults.copy(
        headlineMedium = defaults.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeagueScreen(
    model: LeagueViewModel,
    keepScreenOn: (Boolean) -> Unit,
    playSound: (Boolean) -> Unit,
    onFullscreen: (Boolean) -> Unit,
    onShareTeams: (Session, String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: PELADAS_ROUTE
    val showingPeladas = route == PELADAS_ROUTE
    val showingSettings = route == SETTINGS_ROUTE
    val section = Section.entries.find { it.name == route }
    val league = state.league
    var expandedScore by rememberSaveable { mutableStateOf(false) }
    var editingPelada by rememberSaveable { mutableStateOf<Long?>(null) }
    var openingPelada by rememberSaveable { mutableStateOf<Long?>(null) }
    val fullscreen = expandedScore && section == Section.SCORE
    LaunchedEffect(fullscreen) { onFullscreen(fullscreen) }
    BackHandler(enabled = fullscreen) { expandedScore = false }
    val keepAwake = section == Section.SCORE && league?.session?.rules?.keepScreenOn == true
    DisposableEffect(keepAwake) {
        keepScreenOn(keepAwake)
        onDispose { keepScreenOn(false) }
    }
    val colors = leagueColors(state.preferences, fullscreen)
    MaterialTheme(colorScheme = colors, shapes = AppShapes, typography = AppTypography) {
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        LaunchedEffect(state.error, state.loaded) {
            val error = state.error
            if (error != null && state.loaded) {
                snackbar.showSnackbar(error)
                model.clearError()
            }
        }
        fun navigate(destination: Section) {
            if (destination != Section.SCORE) expandedScore = false
            if (destination.name != nav.currentDestination?.route) nav.navigate(destination.name) {
                popUpTo(PELADAS_ROUTE) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        fun goHome() {
            if (nav.currentDestination?.route != PELADAS_ROUTE &&
                !nav.popBackStack(PELADAS_ROUTE, inclusive = false)) {
                nav.navigate(PELADAS_ROUTE) {
                    popUpTo(nav.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        LaunchedEffect(state.loaded, league == null, section) {
            if (state.loaded && league == null && section != null) goHome()
        }
        LaunchedEffect(openingPelada, league?.session?.pelada?.id, route, state.error, state.profiles) {
            val pending = openingPelada
            if (pending != null && (state.error != null || !showingPeladas ||
                    (state.loaded && state.profiles.none { it.id == pending }))) {
                openingPelada = null
            } else if (pending != null && league?.session?.pelada?.id == pending) {
                openingPelada = null
                navigate(Section.SCORE)
            }
        }
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
            when {
                showingSettings -> TopAppBar(title = { Text("Configurações") }, navigationIcon = {
                    IconButton(onClick = ::goHome) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar às ligas") }
                })
                !showingPeladas && !fullscreen && league != null -> PeladaPicker(league.session.pelada, state.profiles,
                    onSelect = model::selectPelada, onCreate = { editingPelada = 0L }, onEdit = { editingPelada = it },
                    onHome = ::goHome)
            }
        }, floatingActionButton = {
            if (showingPeladas && state.loaded) {
                ExtendedFloatingActionButton(text = { Text("Nova liga") }, icon = { Icon(Icons.Default.Add, null) },
                    onClick = { editingPelada = 0L })
            }
        }, bottomBar = {
            if (showingPeladas) BottomAppBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = ::goHome) {
                        Icon(Icons.Default.Home, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Home")
                    }
                    TextButton(onClick = { nav.navigate(SETTINGS_ROUTE) { launchSingleTop = true } }) {
                        Icon(Icons.Default.Settings, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Configurações")
                    }
                }
            } else if (!fullscreen && !showingSettings) NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                Section.entries.forEach { destination ->
                    NavigationBarItem(selected = destination == section, onClick = {
                        navigate(destination)
                    }, icon = {
                        val icon = destination.icon
                        if (icon == null) Icon(painterResource(R.drawable.ic_scoreboard), contentDescription = null)
                        else Icon(icon, contentDescription = null)
                    },
                        label = { Text(destination.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = colors.primaryContainer,
                            selectedIconColor = colors.onPrimaryContainer,
                            selectedTextColor = colors.onSurface,
                            unselectedIconColor = colors.onSurfaceVariant,
                            unselectedTextColor = colors.onSurfaceVariant,
                        ))
                }
            }
        }, contentWindowInsets = if (fullscreen) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets) { padding ->
            NavHost(nav, startDestination = PELADAS_ROUTE, modifier = Modifier.padding(padding)) {
                Section.entries.forEach { destination ->
                    composable(destination.name) {
                        val current = state.league
                        if (current == null) {
                            LeagueLoading(state.error, model::retry)
                        } else key(current.session.pelada.id) {
                            when (destination) {
                                Section.SCORE -> ScoreScreen(current.session, model, state.preferences, playSound, fullscreen,
                                    onFullscreen = { expandedScore = !expandedScore }, onTeams = { navigate(Section.TEAMS) })
                                Section.TEAMS -> TeamsScreen(current.session,
                                    state.profiles.find { it.id == current.session.pelada.id }?.displayName.orEmpty(),
                                    model, onShareTeams)
                                Section.QUEUE -> QueueScreen(current.session, model)
                                Section.RULES -> RulesScreen(current.session, state.preferences, model) { message ->
                                    scope.launch { snackbar.showSnackbar(message) }
                                }
                                Section.HISTORY -> HistoryScreen(current, model)
                            }
                        }
                    }
                }
                composable(PELADAS_ROUTE) {
                    if (!state.loaded) LeagueLoading(state.error, model::retry)
                    else PeladasList(state.profiles, state.league?.session?.pelada?.id, onSelect = { id ->
                        openingPelada = id
                        if (state.league?.session?.pelada?.id != id) model.selectPelada(id)
                    }, onEdit = { editingPelada = it }, onDelete = model::deletePeladas)
                }
                composable(SETTINGS_ROUTE) {
                    SettingsScreen(state.preferences, model) { message ->
                        scope.launch { snackbar.showSnackbar(message) }
                    }
                }
            }
        }
        editingPelada?.let { id ->
            val profile = if (id == 0L) Pelada(id = 0) else state.profiles.find { it.id == id }
            if (profile != null) PeladaEditor(profile, model, onClose = { editingPelada = null })
        }
    }
}

@Composable
private fun LeagueLoading(error: String?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (error == null) CircularProgressIndicator()
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error)
            Button(onClick = onRetry) { Text("Tentar novamente") }
        }
    }
}

@Composable
fun Page(title: String, brand: Boolean = false, modifier: Modifier = Modifier,
         scrollState: ScrollState = rememberScrollState(), content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth().fillMaxHeight()
            .verticalScroll(scrollState).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (brand) Surface(shape = MaterialTheme.shapes.medium, color = Color.White) {
                    Icon(painterResource(R.drawable.ic_volei_logo), contentDescription = null,
                        modifier = Modifier.size(48.dp), tint = Color.Unspecified)
                }
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(bottom = 2.dp))
            }
            content()
        }
    }
}

@Composable
fun SectionTitle(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(count.toString(), Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
