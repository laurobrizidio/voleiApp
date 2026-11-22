package br.com.voleipelada.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import br.com.voleipelada.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.voleipelada.domain.*

private enum class Section(val label: String, val icon: ImageVector) {
    SCORE("Placar", Icons.Default.Home),
    TEAMS("Times", Icons.Default.Person),
    QUEUE("Fila", Icons.AutoMirrored.Filled.List),
    RULES("Regras", Icons.Default.Settings),
    HISTORY("Histórico", Icons.Default.DateRange),
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFD819), onPrimary = Color(0xFF142448),
    primaryContainer = Color(0xFFFFD819), onPrimaryContainer = Color(0xFF142448),
    background = Color(0xFF0B1731), onBackground = Color.White,
    surface = Color(0xFF142449), onSurface = Color.White,
    surfaceContainer = Color(0xFF1C3060), surfaceContainerLow = Color(0xFF142449),
    surfaceVariant = Color(0xFF263965), onSurfaceVariant = Color(0xFFCCD9FA),
    secondary = Color(0xFFAFC9FF), onSecondary = Color(0xFF002F79),
    secondaryContainer = Color(0xFF1E4389), onSecondaryContainer = Color(0xFFEAF0FF),
    tertiary = Color(0xFFFF9DA9), onTertiary = Color(0xFF680D21),
    error = Color(0xFFFFA7B2), onError = Color(0xFF680D21),
    errorContainer = Color(0xFF851B32), onErrorContainer = Color(0xFFFFDAE0),
    outline = Color(0xFF8EA4CA), outlineVariant = Color(0xFF3B5282),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF0349BA), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD819), onPrimaryContainer = Color(0xFF142448),
    background = Color.White, onBackground = Color(0xFF142448),
    surface = Color.White, onSurface = Color(0xFF142448),
    surfaceContainer = Color(0xFFEAF1FF), surfaceContainerLow = Color(0xFFF6F9FF),
    surfaceVariant = Color(0xFFE5EDFF), onSurfaceVariant = Color(0xFF374D78),
    secondary = Color(0xFF145398), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF2A3), onSecondaryContainer = Color(0xFF332900),
    tertiary = Color(0xFFC5233D), onTertiary = Color.White,
    error = Color(0xFFC5233D), onError = Color.White,
    errorContainer = Color(0xFFFFDAE0), onErrorContainer = Color(0xFF680D21),
    outline = Color(0xFF60769D), outlineVariant = Color(0xFFC7D7F3),
)
private val ContrastColors = LightColors.copy(primary = Color.Black, onPrimary = Color.White,
    primaryContainer = Color.White, onPrimaryContainer = Color.Black,
    background = Color.White, surface = Color.White, surfaceContainer = Color.White,
    surfaceContainerLow = Color.White, secondaryContainer = Color(0xFFE4E4E4), onSecondaryContainer = Color.Black,
    surfaceVariant = Color(0xFFF0F0F0), onSurface = Color.Black, onSurfaceVariant = Color.Black,
    outline = Color.Black, outlineVariant = Color.Black)

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
    val section = Section.entries.find { it.name == entry?.destination?.route } ?: Section.SCORE
    val league = state.league
    var expandedScore by rememberSaveable { mutableStateOf(false) }
    val fullscreen = expandedScore && section == Section.SCORE
    LaunchedEffect(fullscreen) { onFullscreen(fullscreen) }
    BackHandler(enabled = fullscreen) { expandedScore = false }
    val keepAwake = section == Section.SCORE && league?.session?.rules?.keepScreenOn == true
    DisposableEffect(keepAwake) {
        keepScreenOn(keepAwake)
        onDispose { keepScreenOn(false) }
    }
    val colors = if (fullscreen) LightColors.copy(background = Color.White, surface = Color.White)
    else when (state.preferences.appearance) {
        Appearance.SYSTEM -> if (isSystemInDarkTheme()) DarkColors else LightColors
        Appearance.DARK -> DarkColors
        Appearance.LIGHT -> LightColors
        Appearance.CONTRAST -> ContrastColors
    }
    MaterialTheme(colorScheme = colors, shapes = AppShapes, typography = AppTypography) {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.error, league != null) {
            val error = state.error
            if (error != null && league != null) {
                snackbar.showSnackbar(error)
                model.clearError()
            }
        }
        val navigate: (Section) -> Unit = { destination ->
            if (destination != Section.SCORE) expandedScore = false
            if (destination != section) nav.navigate(destination.name) {
                popUpTo(Section.SCORE.name) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            if (!fullscreen) NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                Section.entries.forEach { destination ->
                    NavigationBarItem(selected = destination == section, onClick = {
                        navigate(destination)
                    }, icon = { Icon(destination.icon, contentDescription = null) },
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
            NavHost(nav, startDestination = Section.SCORE.name, modifier = Modifier.padding(padding)) {
                Section.entries.forEach { destination ->
                    composable(destination.name) {
                        val current = state.league
                        if (current == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (state.error == null) CircularProgressIndicator()
                                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(state.error.orEmpty())
                                    Button(onClick = model::retry) { Text("Tentar novamente") }
                                }
                            }
                        } else {
                            when (destination) {
                                Section.SCORE -> ScoreScreen(current.session, model, state.preferences, playSound, fullscreen,
                                    onFullscreen = { expandedScore = !expandedScore }, onTeams = { navigate(Section.TEAMS) })
                                Section.TEAMS -> TeamsScreen(current.session, state.preferences.leagueName, model, onShareTeams)
                                Section.QUEUE -> QueueScreen(current.session, model)
                                Section.RULES -> RulesScreen(current.session.rules, state.preferences, model)
                                Section.HISTORY -> HistoryScreen(current, model)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Page(title: String, brand: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth().fillMaxHeight()
            .verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 20.dp),
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
