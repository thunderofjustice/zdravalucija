package hr.zdravalucija.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hr.zdravalucija.domain.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal fun historyWeekLabel(cycle: WeekCycle): String {
    val format = DateTimeFormatter.ofPattern("d. M. yyyy.", Croatian)
    return "${cycle.start.format(format)} – ${cycle.end.format(format)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(state: LucijaState, vm: LucijaViewModel, addForDate: (LocalDate) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var weeks by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val selectedDate = selected?.let(LocalDate::parse)
    val snapshot = state.snapshot
    val listState = rememberLazyListState()
    LaunchedEffect(selected, weeks) { listState.scrollToItem(0) }
    val firstDay = DayOfWeek.of(state.preferences.firstDayOfWeek)
    fun daily(day: LocalDate) = GetDailyProgressUseCase()(snapshot.categories, snapshot.foods, snapshot.entries, day)
    fun weekly(day: LocalDate) = GetWeeklyProgressUseCase()(snapshot.categories, snapshot.foods, snapshot.entries, WeekCycle.containing(day, firstDay))
    val recordedDays = snapshot.entries.map { LocalDate.parse(it.localDate) }
    val days = (recordedDays + (0L..6L).map { state.today.minusDays(it) }).distinct().sortedDescending()
    val cycles = (recordedDays + (0L..3L).map { state.today.minusWeeks(it) }).map { WeekCycle.containing(it, firstDay) }.distinct().sortedByDescending { it.start }
    BackHandler(enabled = selected != null) { selected = null }
    Column(Modifier.fillMaxSize()) {
        if (selected != null) Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { selected = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Svi dani") }
            Text(if (weeks) "Svi tjedni" else "Svi dani", style = MaterialTheme.typography.labelLarge)
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScreenHeading(if (selectedDate == null) "POVIJEST" else if (weeks) "PREGLED TJEDNA" else displayDate(selectedDate),
                if (selectedDate == null) "Tvoji unosi i ostvareni ciljevi." else if (weeks) historyWeekLabel(WeekCycle.containing(selectedDate, firstDay)) else "${selectedDate.year}.") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(!weeks, onClick = { weeks = false; selected = null }, label = { Text("Dani") })
                    FilterChip(weeks, onClick = { weeks = true; selected = null }, label = { Text("Tjedni") })
                }
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("Odaberi datum") }
            }
            if (selectedDate == null) {
                if (weeks) items(cycles, key = { it.start.toString() }) { cycle ->
                    val goals = weekly(cycle.start)
                    HistorySummaryRow(historyWeekLabel(cycle), "${goals.count { it.remaining == 0 }} / ${goals.size} tjednih ciljeva ostvareno") { selected = cycle.start.toString() }
                } else items(days, key = { it.toString() }) { day ->
                    val goals = daily(day)
                    val entries = snapshot.entries.count { it.localDate == day.toString() }
                    HistorySummaryRow(displayDate(day), "${day.year}. · $entries unosa · ${goals.count { it.remaining == 0 }} / ${goals.size} dnevnih ciljeva") { selected = day.toString() }
                }
            } else if (weeks) {
                val goals = weekly(selectedDate)
                val cycle = WeekCycle.containing(selectedDate, firstDay)
                item { SectionTitle("Tjedni ciljevi", "✓ ${goals.count { it.remaining == 0 }} / ${goals.size}") }
                if (goals.isEmpty()) item { FriendlyEmpty("Nema tjednih ciljeva") }
                items(goals, key = { "goal-${it.category.id}" }) { WeeklyFoodRow(it, it.category.imageQuery) }
                item { SectionTitle("Dani ovog tjedna") }
                items((0L..6L).map { cycle.start.plusDays(it) }.filter { !it.isAfter(state.today) }, key = { "day-$it" }) { day ->
                    val progress = daily(day)
                    HistorySummaryRow(displayDate(day), "${progress.count { it.remaining == 0 }} / ${progress.size} dnevnih ciljeva") { weeks = false; selected = day.toString() }
                }
                item { GoalHistoryNote() }
            } else {
                val goals = daily(selectedDate)
                val rows = snapshot.entries.filter { it.localDate == selectedDate.toString() }
                val groups = rows.groupBy { it.entryGroupId }.values.toList()
                item { Button(onClick = { addForDate(selectedDate) }, modifier = Modifier.fillMaxWidth()) { Text("Dodaj hranu za ovaj dan") } }
                item { SectionTitle("Dnevni ciljevi", "✓ ${goals.count { it.remaining == 0 }} / ${goals.size}") }
                if (goals.isEmpty()) item { FriendlyEmpty("Nema dnevnih ciljeva", "Dnevni ciljevi izvode se iz tjednih ciljeva 7, 14…") }
                items(goals, key = { "goal-${it.category.id}" }) { progress ->
                    HistorySummaryRow(progress.category.name, "${progress.consumed} / ${progress.target} · ${if (progress.remaining == 0) "✓ Ispunjeno" else "još ${progress.remaining}×"}")
                }
                item { TextButton(onClick = { weeks = true }) { Text("Pregled tjedna ovog dana") } }
                item { GoalHistoryNote() }
                item { SectionTitle("Pojedeno", "${rows.size} unosa") }
                if (groups.isEmpty()) item { FriendlyEmpty("Nema unosa za ovaj dan") }
                items(groups, key = { it.first().entryGroupId }) { EntryGroupCard(it, snapshot, vm::deleteEntry, vm::deleteGroup) }
            }
        }
    }
    if (showPicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = (selectedDate ?: state.today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(state.today)
                override fun isSelectableYear(year: Int) = year <= state.today.year
            })
        DatePickerDialog(onDismissRequest = { showPicker = false }, confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                selected = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                showPicker = false
            }) { Text("ODABERI") }
        }, dismissButton = { TextButton(onClick = { showPicker = false }) { Text("ODUSTANI") } }) { DatePicker(picker) }
    }
}

@Composable
private fun GoalHistoryNote() = Text("Pregled prema trenutačnim ciljevima i kategorijama.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun HistorySummaryRow(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
    }
    if (onClick != null) Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { content() }
    else Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { content() }
}
