package hr.zdravalucija.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ManageScreen(type: String, state: LucijaState, navigate: (String) -> Unit, back: () -> Unit) {
    val title = when (type) { "foods" -> "Namirnice"; "categories" -> "Kategorije"; else -> "Jela" }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Natrag") }
            TextButton(onClick = { navigate("edit/$type/0") }) { Icon(Icons.Outlined.Add, null); Text("Dodaj") }
        }
        LazyColumn(contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScreenHeading(title) }
            when (type) {
                "foods" -> items(state.snapshot.foods, key = { it.id }) { food ->
                    ManageRow(food.name, if (food.active) state.snapshot.categories.find { it.id == food.categoryId }?.name.orEmpty() else "Neaktivno") { navigate("edit/foods/${food.id}") }
                }
                "categories" -> items(state.snapshot.categories, key = { it.id }) { category ->
                    ManageRow(category.name, if (category.active) "${category.weeklyTarget} ${if (category.countingMode == "DAYS") "dana" else "×"} / 7 dana" else "Neaktivno") { navigate("edit/categories/${category.id}") }
                }
                else -> items(state.snapshot.meals, key = { it.id }) { meal ->
                    val count = state.snapshot.mealFoods.count { it.mealId == meal.id }
                    ManageRow(meal.name, if (meal.active) "$count namirnica" else "Neaktivno") { navigate("edit/meals/${meal.id}") }
                }
            }
        }
    }
}
@Composable
private fun ManageRow(name: String, detail: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
