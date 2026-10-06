package hr.zdravalucija.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import hr.zdravalucija.data.FoodSnapshot

internal fun FoodSnapshot.mealCategoryIds() = categories.filter { it.name.trim().equals("Jela", ignoreCase = true) }.map { it.id }.toSet()

@Composable
fun AddFoodScreen(state: LucijaState, vm: LucijaViewModel, initialCategory: Long? = null, close: () -> Unit) {
    // null = category picker, -1 = virtual meal collection, positive = real category.
    val mealCategoryIds = state.snapshot.mealCategoryIds()
    var selected by rememberSaveable { mutableStateOf<Long?>(if (initialCategory in mealCategoryIds) -1L else initialCategory) }
    BackHandler(enabled = selected != null) { selected = null }
    val categories = state.snapshot.categories.filter { it.active && it.id !in mealCategoryIds && state.snapshot.foods.any { food -> food.active && food.categoryId == it.id } }
    val mealCategory = state.snapshot.categories.firstOrNull { it.active && it.id in mealCategoryIds }
    val title = when (selected) { null -> "ŠTO SI JEO?"; -1L -> "Jela"; else -> state.snapshot.categories.find { it.id == selected }?.name.orEmpty() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { if (selected != null) selected = null else close() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Natrag") }
            TextButton(onClick = close) { Text("GOTOVO") }
        }
        LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { ScreenHeading(title, if (selected == null) "Odaberi što je bilo na tvom tanjuru." else "Jedan dodir i zabilježeno je.") }
            if (selected == null) {
                items(categories, key = { it.id }) { category ->
                    VisualCard(category.name, category.imageUrl, category.imageQuery, onClick = { selected = category.id })
                }
                item {
                    VisualCard("Jela", mealCategory?.imageUrl,
                        mealCategory?.imageQuery ?: "vegetable soup", onClick = { selected = -1 })
                }
            } else if (selected == -1L) {
                items(state.snapshot.meals.filter { it.active }, key = { "meal-${it.id}" }) { meal ->
                    VisualCard(meal.name, meal.imageUrl, meal.imageQuery, onClick = { vm.addMeal(meal) })
                }
                items(state.snapshot.foods.filter { it.active && it.categoryId in mealCategoryIds && state.snapshot.categories.any { category -> category.id == it.categoryId && category.active } }, key = { "food-${it.id}" }) { food ->
                    VisualCard(food.name, food.imageUrl, state.snapshot.visualQuery(food), onClick = { vm.addFood(food) })
                }
            } else {
                items(state.snapshot.foods.filter { it.active && it.categoryId == selected }, key = { it.id }) { food ->
                    VisualCard(food.name, food.imageUrl, state.snapshot.visualQuery(food), onClick = { vm.addFood(food) })
                }
            }
        }
    }
}
