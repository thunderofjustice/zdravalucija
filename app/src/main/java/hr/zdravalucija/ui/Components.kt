package hr.zdravalucija.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.zdravalucija.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val Croatian: Locale = Locale.forLanguageTag("hr-HR")
val LocalImageStore = staticCompositionLocalOf<ImageStore?> { null }
fun FoodSnapshot.visualQuery(food: FoodEntity): String? = food.imageQuery ?: categories.find { it.id == food.categoryId }?.imageQuery
fun displayDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Croatian))
    .replaceFirstChar { it.titlecase(Croatian) }

@Composable
fun ScreenHeading(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 20.dp, bottom = 22.dp)) {
        Text("ZDRAVA LUCIJA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun categorySymbol(query: String?): String {
    val text = query.orEmpty().lowercase()
    return when {
        "apple" in text -> "🍎"; "banana" in text -> "🍌"; "orange" in text -> "🍊"
        "broccoli" in text -> "🥦"; "cauliflower" in text -> "🥬"; "carrot" in text -> "🥕"
        "fruit" in text -> "🍐"; "vegetable" in text && "soup" !in text -> "🥕"
        "salmon" in text || "fish" in text -> "🐟"; "chicken" in text -> "🍗"
        "beef" in text || "meat" in text -> "🥩"; "juice" in text -> "🍊"
        "grain" in text -> "🌾"; "nut" in text -> "🥜"; "bean" in text -> "🫘"
        else -> "🥣"
    }
}

@Composable
fun FoodVisual(image: String?, query: String?, description: String, modifier: Modifier = Modifier) {
    val store = LocalImageStore.current
    val revision = store?.revision?.collectAsStateWithLifecycle()?.value
    val local = remember(image, revision) { store?.localFile(image) }
    Box(modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.surfaceVariant))), contentAlignment = Alignment.Center) {
        Text(categorySymbol(query), style = MaterialTheme.typography.displayLarge)
        if (local != null) AsyncImage(model = local, contentDescription = description,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun VisualCard(name: String, url: String?, query: String?, modifier: Modifier = Modifier, secondary: String? = null, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        FoodVisual(url, query, name, Modifier.fillMaxWidth().height(150.dp))
        Column(Modifier.padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (secondary != null) Text(secondary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun SectionTitle(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (trailing != null) Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun FriendlyEmpty(title: String, subtitle: String? = null, icon: ImageVector = Icons.Outlined.CheckCircle) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun EntryGroupCard(entries: List<ConsumptionEntryEntity>, snapshot: FoodSnapshot, deleteEntry: (Long) -> Unit, deleteGroup: (String) -> Unit) {
    val first = entries.first()
    val meal = snapshot.meals.find { it.id == first.sourceMealId }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            if (meal != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Restaurant, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(meal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Iz jela", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { deleteGroup(first.entryGroupId) }) {
                    Icon(Icons.Outlined.DeleteOutline, "Obriši cijelo jelo: ${meal.name}")
                }
            }
            entries.forEach { entry ->
                val food = snapshot.foods.find { it.id == entry.foodId }
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    FoodVisual(food?.imageUrl, food?.imageQuery ?: snapshot.categories.find { it.id == food?.categoryId }?.imageQuery,
                        food?.name.orEmpty(), Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)))
                    Text(food?.name ?: "Namirnica", Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { deleteEntry(entry.id) }) { Icon(Icons.Outlined.Close, "Obriši unos: ${food?.name}") }
                }
            }
        }
    }
}
