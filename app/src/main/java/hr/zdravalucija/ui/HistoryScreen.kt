package hr.zdravalucija.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import androidx.activity.compose.BackHandler

@Composable
fun HistoryScreen(state: LucijaState, vm: LucijaViewModel) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = selected != null) { selected = null }
    val days = state.snapshot.entries.groupBy { it.localDate }.toSortedMap(reverseOrder())
    Column(Modifier.fillMaxSize()) {
        if (selected != null) Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { selected = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Svi dani") }
            Text("Svi dani", style = MaterialTheme.typography.labelLarge)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScreenHeading(if (selected == null) "POVIJEST" else displayDate(LocalDate.parse(selected)),
                if (selected == null) "Tvoji tanjuri, dan po dan." else "${days[selected]?.size ?: 0} unosa") }
            if (selected == null) {
                if (days.isEmpty()) item { FriendlyEmpty("Ovdje počinje tvoja priča", "Prvo zabilježi što si jeo.", Icons.Outlined.History) }
                items(days.keys.toList(), key = { it }) { day ->
                    val entries = days.getValue(day)
                    Card(onClick = { selected = day }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(displayDate(LocalDate.parse(day)), style = MaterialTheme.typography.titleMedium)
                                Text("${LocalDate.parse(day).year}. · ${entries.size} unosa", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                        }
                    }
                }
            } else {
                val groups = days[selected].orEmpty().groupBy { it.entryGroupId }.values.toList()
                if (groups.isEmpty()) item { FriendlyEmpty("Nema unosa za ovaj dan") }
                items(groups, key = { it.first().entryGroupId }) { entries -> EntryGroupCard(entries, state.snapshot, vm::deleteEntry, vm::deleteGroup) }
            }
        }
    }
}
