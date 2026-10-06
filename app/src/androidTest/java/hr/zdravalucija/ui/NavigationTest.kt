package hr.zdravalucija.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import hr.zdravalucija.MainActivity
import org.junit.Rule
import org.junit.Test
import hr.zdravalucija.LucijaApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun allFourTabsAndQuickEntryAreReachable() {
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Dodaj hranu").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Dodaj hranu").performClick()
        compose.onNodeWithText("ŠTO SI JEO?").assertIsDisplayed()
        compose.onNodeWithText("Voće").performClick()
        compose.onNodeWithText("Jabuka").assertIsDisplayed()
        compose.onNodeWithText("GOTOVO").performClick()
        compose.onNodeWithText("TJEDAN").performClick()
        compose.onNodeWithText("OVAJ TJEDAN").assertIsDisplayed()
        compose.onNodeWithText("POVIJEST").performClick()
        compose.onNodeWithText("POSTAVKE").performClick()
        compose.onNodeWithText("Namirnice").performClick()
        compose.onNodeWithText("Dodaj").performClick()
        compose.onNodeWithText("Naziv").assertIsDisplayed()
        compose.onNodeWithText("Koliko puta u 7 dana?").assertDoesNotExist()
    }
    @Test fun mealCategoryAndMealShortcutsShareOneNavigationCard() {
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Dodaj hranu").fetchSemanticsNodes().isNotEmpty() }
        val container = (compose.activity.application as LucijaApplication).container
        val categoryCount = runBlocking { container.database.dao().categories().size }
        val categoryId = runBlocking { container.repository.updateCategory(hr.zdravalucija.data.CategoryEntity(name=" jELA ",imageQuery="vegetable soup")) }
        val foodId = runBlocking { container.repository.updateFood(hr.zdravalucija.data.FoodEntity(name="Test obično jelo",categoryId=categoryId,imageQuery="vegetable soup")) }
        runBlocking { container.repository.updateMeal(hr.zdravalucija.data.MealEntity(name="Test novo jelo",imageQuery="vegetable soup"),setOf(1)) }
        assertEquals(categoryCount+1,runBlocking { container.database.dao().categories().size })
        compose.onNodeWithContentDescription("Dodaj hranu").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Jela"))
        compose.onAllNodesWithText("Jela").assertCountEquals(1)
        compose.onNodeWithText("Jela").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Test novo jelo"))
        compose.onNodeWithText("Test novo jelo").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Test obično jelo"))
        compose.onNodeWithText("Test obično jelo").assertIsDisplayed()
    }
    @Test fun quickEntryAndMealUndoPreserveManualEntry() {
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Dodaj hranu").fetchSemanticsNodes().isNotEmpty() }
        val repository = (compose.activity.application as LucijaApplication).container.repository
        val dao = (compose.activity.application as LucijaApplication).container.database.dao()
        val baseline = runBlocking { dao.entries().size }
        val manual = runBlocking { repository.addFoodConsumption(4) }
        try {
            compose.onNodeWithContentDescription("Dodaj hranu").performClick()
            compose.onNodeWithText("Povrće").performClick()
            compose.onNodeWithText("Brokula").performClick()
            compose.waitUntil(5_000) { runBlocking { dao.entries().size == baseline + 2 } }
            compose.onNodeWithText("UNDO").performClick()
            compose.waitUntil(5_000) { runBlocking { dao.entries().size == baseline + 1 } }
            compose.onNodeWithContentDescription("Natrag").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Jela"))
            compose.onNodeWithText("Jela").performClick()
            compose.onNodeWithText("Bistra juha od povrća").performClick()
            compose.waitUntil(5_000) { runBlocking { dao.entries().size == baseline + 4 } }
            compose.onNodeWithText("UNDO").performClick()
            compose.waitUntil(5_000) { runBlocking { dao.entries().size == baseline + 1 } }
            assertEquals(1, runBlocking { dao.entries().count { it.entryGroupId == manual } })
        } finally { runBlocking { repository.deleteEntryGroup(manual) } }
    }
}
