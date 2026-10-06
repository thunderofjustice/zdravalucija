package hr.zdravalucija.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hr.zdravalucija.domain.WeeklyProgress
import java.time.format.DateTimeFormatter

@Composable
fun WeeklyScreen(state: LucijaState) {
    val format = DateTimeFormatter.ofPattern("d. MMM", Croatian)
    val completed = state.weeklyProgress.filter { it.remaining == 0 }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeading("OVAJ TJEDAN", "${state.cycle.start.format(format)} – ${state.cycle.end.format(format)}") }
        item { SectionTitle("Još ovaj tjedan", "${state.remainingWeek.size} kategorija") }
        if (state.weeklyProgress.isEmpty()) item { FriendlyEmpty("Tvoj popis, tvoj ritam", "Dodaj tjedne ciljeve u Postavkama.") }
        else if (state.remainingWeek.isEmpty()) item { FriendlyEmpty("Sve za ovaj tjedan!", "Svi ciljevi kategorija su ispunjeni.") }
        items(state.remainingWeek, key = { it.category.id }) { WeeklyFoodRow(it, it.category.imageQuery ?: it.category.name) }
        if (completed.isNotEmpty()) {
            item { SectionTitle("Ostvareno", "✓ ${completed.size}") }
            items(completed, key = { it.category.id }) { WeeklyFoodRow(it, it.category.imageQuery ?: it.category.name) }
        }
    }
}

@Composable
private fun WeeklyFoodRow(progress: WeeklyProgress, visualQuery: String?) {
    val ratio by animateFloatAsState((progress.weeklyConsumed.toFloat() / progress.weeklyTarget).coerceIn(0f, 1f), label = "Tjedni napredak")
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FoodVisual(progress.category.imageUrl, visualQuery, progress.category.name,
                Modifier.size(100.dp).clip(RoundedCornerShape(20.dp)))
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(progress.category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${progress.weeklyConsumed} / ${progress.weeklyTarget}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (progress.remaining == 0) Icon(Icons.Outlined.CheckCircle, "Cilj ostvaren", tint = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)))
                Text(if (progress.remaining == 0) "Ispunjeno" else if (progress.category.countingMode == "DAYS") "još ${progress.remaining} dana" else "još ${progress.remaining}×", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
