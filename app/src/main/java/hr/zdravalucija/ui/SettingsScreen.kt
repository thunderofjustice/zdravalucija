package hr.zdravalucija.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hr.zdravalucija.data.ThemeMode
import java.time.DayOfWeek
import java.time.format.TextStyle

@Composable
fun SettingsScreen(state: LucijaState, vm: LucijaViewModel, navigate: (String) -> Unit,
    export: () -> Unit = {}, import: () -> Unit = {}) {
    var weekMenu by remember { mutableStateOf(false) }
    var themeMenu by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeading("POSTAVKE", "Mali popis koji odgovara tebi.") }
        item { SectionTitle("Tvoj popis") }
        item { SettingsRow("Namirnice", "Uredi namirnice i pripadnost kategoriji") { navigate("manage/foods") } }
        item { SettingsRow("Kategorije", "Zajednički ciljevi u 7 dana") { navigate("manage/categories") } }
        item { SettingsRow("Jela", "Odaberi namirnice koje pripadaju jelu") { navigate("manage/meals") } }
        item { SettingsRow("Uvezi prehrambeni popis", "Lucijin popis · 12 kategorija · donja granica ciljeva") { vm.preparePlan() } }
        item { SectionTitle("Prikaz i ciklus") }
        item {
            Box {
                SettingsRow("Početak tjedna", DayOfWeek.of(state.preferences.firstDayOfWeek).getDisplayName(TextStyle.FULL, Croatian)
                    .replaceFirstChar { it.titlecase(Croatian) }) { weekMenu = true }
                DropdownMenu(weekMenu, onDismissRequest = { weekMenu = false }) {
                    DayOfWeek.entries.forEach { day -> DropdownMenuItem(text = { Text(day.getDisplayName(TextStyle.FULL, Croatian)) }, onClick = {
                        vm.savePreferences(state.preferences.copy(firstDayOfWeek = day.value)); weekMenu = false
                    }) }
                }
            }
        }
        item {
            Box {
                SettingsRow("Izgled", themeLabel(state.preferences.theme)) { themeMenu = true }
                DropdownMenu(themeMenu, onDismissRequest = { themeMenu = false }) {
                    ThemeMode.entries.forEach { theme -> DropdownMenuItem(text = { Text(themeLabel(theme)) }, onClick = {
                        vm.savePreferences(state.preferences.copy(theme = theme)); themeMenu = false
                    }) }
                }
            }
        }
        item { SectionTitle("Sigurnosna kopija") }
        item { SettingsRow("Izvezi podatke", "Spremi JSON kopiju") { export() } }
        item { SettingsRow("Uvezi podatke", "Vrati prethodno spremljenu kopiju") { import() } }
        item { Text("Zdrava Lucija · ${hr.zdravalucija.BuildConfig.VERSION_NAME}\nFotografije: Pixabay", Modifier.padding(top = 22.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
private fun themeLabel(theme: ThemeMode) = when (theme) { ThemeMode.SYSTEM -> "Prema uređaju"; ThemeMode.LIGHT -> "Svijetli"; ThemeMode.DARK -> "Tamni" }

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
    }
}
