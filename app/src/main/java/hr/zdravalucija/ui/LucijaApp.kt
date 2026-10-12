package hr.zdravalucija.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import hr.zdravalucija.data.ThemeMode

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(Tab("today", "DANAS", Icons.Outlined.WbSunny), Tab("week", "TJEDAN", Icons.Outlined.DateRange),
    Tab("history", "POVIJEST", Icons.Outlined.History), Tab("settings", "POSTAVKE", Icons.Outlined.Tune))

@Composable
fun LucijaApp(vm: LucijaViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dark = state.preferences.theme == ThemeMode.DARK ||
        (state.preferences.theme == ThemeMode.SYSTEM && isSystemInDarkTheme())
    LaunchedEffect(dark) {
        val transparent = android.graphics.Color.TRANSPARENT
        val bars = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        (context as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }
    val pending by vm.pendingImport.collectAsStateWithLifecycle()
    val plan by vm.pendingPlan.collectAsStateWithLifecycle()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri, context)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareImport(uri, context)
    }
    CompositionLocalProvider(LocalImageStore provides vm.container.images) {
    LucijaTheme(state.preferences.theme) {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route ?: "today"
        val snackbars = remember { SnackbarHostState() }
        var feedback by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(vm) {
            var messageJob: Job? = null
            var feedbackJob: Job? = null
            vm.events.collect { event ->
                messageJob?.cancel()
                feedbackJob?.cancel()
                feedback = event.added
                if (event.added) feedbackJob = scope.launch { delay(700); feedback = false }
                messageJob = scope.launch {
                    val result = snackbars.showSnackbar(event.text, actionLabel = if (event.undoGroupId != null) "UNDO" else null,
                        withDismissAction = true, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) event.undoGroupId?.let(vm::undo)
                }
            }
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbars) },
            bottomBar = {
                if (!state.loading && state.error == null && route in tabs.map { it.route }) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab -> NavigationBarItem(selected = route == tab.route,
                        onClick = { nav.navigate(tab.route) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(tab.icon, null) }, label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) }) }
                }
            },
            floatingActionButton = {
                if (!state.loading && state.error == null && route == "today") LargeFloatingActionButton(onClick = { nav.navigate("add") },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Icon(Icons.Outlined.Add, "Dodaj hranu", Modifier.size(32.dp))
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (state.error != null) Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) { Text(state.error!!) }
                else if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else NavHost(navController = nav, startDestination = "today") {
                    composable("today") { TodayScreen(state, { nav.navigate("add/$it") }, vm::deleteEntry, vm::deleteGroup) }
                    composable("add") { AddFoodScreen(state, vm) { nav.popBackStack() } }
                    composable("add/{category}") { entry -> AddFoodScreen(state, vm, entry.arguments?.getString("category")?.toLongOrNull()) { nav.popBackStack() } }
                    composable("week") { WeeklyScreen(state) }
                    composable("history") { HistoryScreen(state, vm, addForDate = { nav.navigate("add-date/$it") }) }
                    composable("add-date/{date}") { entry ->
                        val selectedDate = java.time.LocalDate.parse(entry.arguments?.getString("date"))
                        AddFoodScreen(state, vm, onDate = selectedDate) { nav.popBackStack() }
                    }
                    composable("settings") { SettingsScreen(state, vm, navigate = { nav.navigate(it) },
                        export = { export.launch("zdrava-lucija-${state.today}.json") }, import = { import.launch(arrayOf("application/json", "text/plain")) }) }
                    composable("manage/{type}") { entry -> ManageScreen(entry.arguments?.getString("type") ?: "foods", state,
                        navigate = { nav.navigate(it) }, back = { nav.popBackStack() }) }
                    composable("edit/{type}/{id}") { entry -> EditScreen(entry.arguments?.getString("type") ?: "foods",
                        entry.arguments?.getString("id")?.toLongOrNull() ?: 0, state, vm, back = { nav.popBackStack() }) }
                }
                AnimatedVisibility(feedback, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.Center)) {
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 8.dp) { Icon(Icons.Outlined.Check, "Zabilježeno", Modifier.padding(24.dp).size(48.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                }
            }
        }
        if (pending != null) AlertDialog(onDismissRequest = vm::cancelImport,
            title = { Text("Vrati sigurnosnu kopiju?") },
            text = { Text("Ova kopija sadrži ${pending!!.foods.size} namirnica, ${pending!!.meals.size} jela i ${pending!!.entries.size} unosa. Zamijenit će trenutne podatke.") },
            confirmButton = { TextButton(onClick = { vm.confirmImport() }) { Text("UVEZI") } },
            dismissButton = { TextButton(onClick = vm::cancelImport) { Text("ODUSTANI") } })
        plan?.let { currentPlan -> AlertDialog(onDismissRequest = vm::cancelPlan,
            title = { Text("Uvezi prehrambeni popis?") },
            text = { androidx.compose.foundation.lazy.LazyColumn {
                item { Text("Ciljevi kategorija ažuriraju se, a namirnice dopunjuju. Povijest, jela i postavke ostaju sačuvani.") }
                items(currentPlan.categories.size) { index ->
                    val category = currentPlan.categories[index]
                    Text("${category.name}: ${category.weeklyTarget} ${if (category.countingMode == "DAYS") "dana" else "unosa"} / 7 dana", Modifier.padding(top = 8.dp))
                }
                item { Text("${currentPlan.categories.sumOf { it.foods.size }} namirnica u popisu", Modifier.padding(top = 12.dp)) }
            } },
            confirmButton = { TextButton(onClick = { vm.confirmPlan() }) { Text("PRIMIJENI") } },
            dismissButton = { TextButton(onClick = vm::cancelPlan) { Text("ODUSTANI") } }) }
    }
    }
}
