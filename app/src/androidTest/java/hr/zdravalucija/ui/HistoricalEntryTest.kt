package hr.zdravalucija.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import hr.zdravalucija.LucijaApplication
import hr.zdravalucija.MainActivity
import hr.zdravalucija.domain.*
import java.time.LocalDate
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HistoricalEntryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as LucijaApplication).container
    private fun text(value: String) = compose.onNodeWithText(value)
    private fun scroll(value: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(value))

    @Test fun yesterdayFoodMealUndoAndBothHistoryViewsUseTheSelectedDate() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("Dodaj hranu").fetchSemanticsNodes().isNotEmpty() }
        val dao = container.database.dao()
        val yesterday = LocalDate.now().minusDays(1)
        val baseline = runBlocking { dao.entries() }
        val baselineIds = baseline.map { it.id }.toSet()
        try {
            text("POVIJEST").performClick()
            text("Odaberi datum").performClick()
            text("ODUSTANI").performClick()
            scroll(displayDate(yesterday))
            text(displayDate(yesterday)).performClick()
            text("Dodaj hranu za ovaj dan").performClick()
            text("Za: ${displayDate(yesterday)} ${yesterday.year}.").assertIsDisplayed()
            text("Voće").performClick()
            text("Jabuka").performClick()
            text("Jabuka").performClick()
            compose.waitUntil(5_000) { runBlocking { dao.entries().count { it.id !in baselineIds } == 2 } }
            assertTrue(runBlocking { dao.entries().filter { it.id !in baselineIds }.all { it.localDate == yesterday.toString() } })
            text("GOTOVO").performClick()
            val daily = runBlocking { GetDailyProgressUseCase()(dao.categories(),dao.foods(),dao.entries(),yesterday).first { it.category.id == 1L } }
            scroll("${daily.consumed} / ${daily.target} · ${if (daily.remaining == 0) "✓ Ispunjeno" else "još ${daily.remaining}×"}")
            text("${daily.consumed} / ${daily.target} · ${if (daily.remaining == 0) "✓ Ispunjeno" else "još ${daily.remaining}×"}").assertIsDisplayed()
            scroll("Pregled tjedna ovog dana")
            text("Pregled tjedna ovog dana").performClick()
            text("PREGLED TJEDNA").assertIsDisplayed()
            val firstDay = runBlocking { container.preferences.preferences.first().firstDayOfWeek }
            text(historyWeekLabel(WeekCycle.containing(yesterday,DayOfWeek.of(firstDay)))).assertIsDisplayed()
            compose.onNodeWithContentDescription("Svi dani").performClick()
            text("Dani").performClick()
            scroll(displayDate(yesterday))
            text(displayDate(yesterday)).performClick()
            text("Dodaj hranu za ovaj dan").performClick()
            scroll("Jela")
            text("Jela").performClick()
            text("Bistra juha od povrća").performClick()
            compose.waitUntil(5_000) { runBlocking { dao.entries().count { it.id !in baselineIds } == 5 } }
            val mealRows = runBlocking { dao.entries().filter { it.id !in baselineIds && it.sourceMealId == 1L } }
            assertEquals(3,mealRows.size)
            assertEquals(1,mealRows.map { it.entryGroupId }.distinct().size)
            assertTrue(mealRows.all { it.localDate == yesterday.toString() })
            text("UNDO").performClick()
            compose.waitUntil(5_000) { runBlocking { dao.entries().count { it.id !in baselineIds } == 2 } }
            text("GOTOVO").performClick()
            scroll("Pojedeno")
            text("${baseline.count { it.localDate == yesterday.toString() } + 2} unosa").assertIsDisplayed()
        } finally {
            runBlocking { dao.entries().filter { it.id !in baselineIds }.map { it.entryGroupId }.distinct().forEach { dao.deleteEntryGroup(it) } }
        }
    }
}
