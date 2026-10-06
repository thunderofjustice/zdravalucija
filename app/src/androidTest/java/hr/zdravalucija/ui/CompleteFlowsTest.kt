package hr.zdravalucija.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import hr.zdravalucija.LucijaApplication
import hr.zdravalucija.MainActivity
import hr.zdravalucija.data.*
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties

class CompleteFlowsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as LucijaApplication).container
    private fun ready() = compose.waitUntil(15_000) {
        compose.onAllNodesWithContentDescription("Dodaj hranu").fetchSemanticsNodes().isNotEmpty()
    }
    private fun text(value: String) = compose.onNodeWithText(value)
    private fun back() = compose.onNodeWithContentDescription("Natrag").performClick()
    private fun hideKeyboard() = compose.runOnIdle {
        val input = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        input.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
    }
    private fun toggleActive() = compose.onNodeWithContentDescription("Aktivno")
        .performScrollTo().performClick()
    private fun openListedItem(value: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(value))
        text(value).performClick()
    }

    @Test fun categoryFoodAndMealEditorsPersistTargetsMembershipAndActivation() {
        ready()
        val suffix = System.nanoTime().toString().takeLast(6)
        val categoryName = "Test kategorija $suffix"
        val foodName = "Test namirnica $suffix"
        val mealName = "Test jelo $suffix"
        text("POSTAVKE").performClick()
        text("Kategorije").performClick()
        text("Dodaj").performClick()
        text("Naziv").performTextInput(categoryName)
        text("Koliko puta u 7 dana?").performTextReplacement("7")
        hideKeyboard()
        text("SPREMI").performClick()
        compose.waitUntil(5_000) { runBlocking { container.database.dao().categories().any { it.name == categoryName } } }
        back()
        text("Namirnice").performClick()
        text("Dodaj").performClick()
        text("Naziv").performTextInput(foodName)
        text("Koliko puta u 7 dana?").assertDoesNotExist()
        hideKeyboard()
        compose.onNodeWithText("Kategorija:", substring = true).performClick()
        text(categoryName).performScrollTo().performClick()
        text("SPREMI").performClick()
        compose.waitUntil(5_000) { runBlocking { container.database.dao().foods().any { it.name == foodName } } }
        val food = runBlocking { container.database.dao().foods().first { it.name == foodName } }
        back()
        text("Jela").performClick()
        text("Dodaj").performClick()
        text("Naziv").performTextInput(mealName)
        hideKeyboard()
        text("SPREMI").assertIsNotEnabled()
        text(foodName).performScrollTo().performClick()
        text("SPREMI").assertIsEnabled()
        text("SPREMI").performClick()
        compose.waitUntil(5_000) { runBlocking { container.database.dao().meals().any { it.name == mealName } } }
        val meal = runBlocking { container.database.dao().meals().first { it.name == mealName } }
        assertEquals(listOf(food.id), runBlocking { container.database.dao().foodsForMeal(meal.id).map { it.id } })
        openListedItem(mealName)
        toggleActive()
        text("SPREMI").performClick()
        compose.waitUntil(5_000) { runBlocking { container.database.dao().meal(meal.id)?.active == false } }
        back()
        text("Namirnice").performClick()
        openListedItem(foodName)
        toggleActive()
        text("SPREMI").performClick()
        compose.waitUntil(5_000) { runBlocking { container.database.dao().food(food.id)?.let { !it.active && it.weeklyTarget == 0 } == true } }
        back()
        text("Kategorije").performClick()
        openListedItem(categoryName)
        text("Koliko puta u 7 dana?").performTextReplacement("0")
        hideKeyboard()
        toggleActive()
        text("SPREMI").performClick()
        compose.waitUntil(5_000) { runBlocking { container.database.dao().category(food.categoryId)?.active == false } }
    }

    @Test fun historyShowsMealSourceAndSupportsSingleAndWholeGroupDeletion() {
        ready()
        val repo = container.repository
        val dao = container.database.dao()
        val group = runBlocking { repo.addMealConsumption(1) }
        val direct = runBlocking { repo.addFoodConsumption(1) }
        try {
            text("POVIJEST").performClick()
            text(displayDate(LocalDate.now())).performClick()
            text("Bistra juha od povrća").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Obriši unos: Brokula").performScrollTo().performClick()
            compose.waitUntil(5_000) { runBlocking { dao.entries().count { it.entryGroupId == group } == 2 } }
            compose.onNodeWithContentDescription("Obriši cijelo jelo: Bistra juha od povrća").performScrollTo().performClick()
            compose.waitUntil(5_000) { runBlocking { dao.entries().none { it.entryGroupId == group } } }
            assertEquals(1, runBlocking { dao.entries().count { it.entryGroupId == direct } })
        } finally { runBlocking { repo.deleteEntryGroup(group); repo.deleteEntryGroup(direct) } }
    }

    @Test fun firstWeekdayAndBothThemesPersistThroughSettings() {
        ready()
        val previous = runBlocking { container.preferences.preferences.first() }
        try {
            text("POSTAVKE").performClick()
            text("Početak tjedna").performScrollTo().performClick()
            text("srijeda").performClick()
            compose.waitUntil(5_000) { runBlocking { container.preferences.preferences.first().firstDayOfWeek == 3 } }
            text("Izgled").performScrollTo().performClick()
            compose.onAllNodesWithText("Tamni").onLast().performClick()
            compose.waitUntil(5_000) { runBlocking { container.preferences.preferences.first().theme == ThemeMode.DARK } }
            text("Izgled").performScrollTo().performClick()
            compose.onAllNodesWithText("Svijetli").onLast().performClick()
            compose.waitUntil(5_000) { runBlocking { container.preferences.preferences.first().theme == ThemeMode.LIGHT } }
            text("TJEDAN").performClick()
            text("OVAJ TJEDAN").assertIsDisplayed()
        } finally { runBlocking { container.preferences.save(previous) } }
    }

    @Test fun planImportPreviewCanCloseAndCategoryShortcutRecordsConcreteFood() {
        ready()
        text("POSTAVKE").performClick()
        text("Uvezi prehrambeni popis").performScrollTo().performClick()
        text("Uvezi prehrambeni popis?").assertIsDisplayed()
        text("ODUSTANI").performClick()
        val importFile = java.io.File(compose.activity.cacheDir,"test-diet-plan.json")
        importFile.writeText(compose.activity.assets.open("diet-plan.json").bufferedReader().use { it.readText() })
        compose.runOnIdle {
            androidx.lifecycle.ViewModelProvider(compose.activity)[LucijaViewModel::class.java]
                .prepareImport(android.net.Uri.fromFile(importFile),compose.activity)
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Uvezi prehrambeni popis?").fetchSemanticsNodes().isNotEmpty() }
        text("PRIMIJENI").performClick()
        compose.waitUntil(10_000) { runBlocking { container.database.dao().categories().any { it.name == "Voće" && it.weeklyTarget == 14 } } }
        text("DANAS").performClick()
        text("Voće").performScrollTo().performClick()
        text("Jabuka").performClick()
        val dao = container.database.dao()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("UNDO").fetchSemanticsNodes().isNotEmpty() }
        val group = runBlocking { dao.entries().first { it.foodId == 1L && it.localDate == LocalDate.now().toString() }.entryGroupId }
        text("UNDO").performClick()
        compose.waitUntil(5_000) { runBlocking { dao.entries().none { it.entryGroupId == group } } }
        text("GOTOVO").performClick()
        text("Voće").assertIsDisplayed()
        importFile.delete()
    }
}
