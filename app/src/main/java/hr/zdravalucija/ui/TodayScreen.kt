package hr.zdravalucija.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hr.zdravalucija.data.FoodEntity

@Composable
fun TodayScreen(state: LucijaState, openCategory: (Long) -> Unit, deleteEntry: (Long) -> Unit, deleteGroup: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeading("DANAS", displayDate(state.today)) }
        item { SectionTitle("Još danas trebaš", if (state.remainingToday.isNotEmpty()) "${state.remainingToday.size}" else null) }
        if (state.remainingToday.isEmpty()) item { FriendlyEmpty("Sve za danas!", "Tvoj dnevni popis je ispunjen.") }
        items(state.remainingToday.chunked(2), key = { it.first().category.id }) { row ->
            Row(Modifier.animateContentSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { remaining ->
                    val category = remaining.category
                    Column(Modifier.weight(1f)) {
                        VisualCard(category.name, category.imageUrl, category.imageQuery ?: category.name, Modifier.fillMaxWidth(), onClick = { openCategory(category.id) })
                        Text("još ${remaining.remaining}×", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { SectionTitle("Danas si jeo", "${state.todayEntries.size} unosa") }
        if (state.todayEntries.isEmpty()) item {
            FriendlyEmpty("Što je danas bilo fino?", "Dodirni + i dodaj prvu namirnicu.", Icons.Outlined.Restaurant)
        }
        items(state.todayEntries.groupBy { it.entryGroupId }.values.toList(), key = { it.first().entryGroupId }) { entries ->
            EntryGroupCard(entries, state.snapshot, deleteEntry, deleteGroup)
        }
    }
}
