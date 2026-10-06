package hr.zdravalucija.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import hr.zdravalucija.LucijaApplication
import hr.zdravalucija.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses the actual button and Pixabay; restores the three original pictures afterwards. */
class ReplaceImageFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as LucijaApplication).container

    @Test fun actualButtonsReplaceFoodCategoryAndMealPhotosAndPersistThemLocally() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("Dodaj hranu").fetchSemanticsNodes().isNotEmpty() }
        val dao = container.database.dao()
        val originals = runBlocking { Triple(dao.food(1)!!, dao.category(1)!!, dao.meal(1)!!) }
        compose.onNodeWithText("POSTAVKE").performClick()
        try {
            for ((section, name, oldUrl) in listOf(
                Triple("Namirnice", originals.first.name, originals.first.imageUrl),
                Triple("Kategorije", originals.second.name, originals.second.imageUrl),
                Triple("Jela", originals.third.name, originals.third.imageUrl),
            )) {
                compose.onNodeWithText(section).performClick()
                compose.onNode(hasScrollAction()).performScrollToNode(hasText(name))
                compose.onNodeWithText(name).performClick()
                compose.onNodeWithText("Dohvati drugu sliku").performScrollTo().performClick()
                compose.waitUntil(90_000) {
                    runBlocking {
                        val url = when (section) { "Namirnice" -> dao.food(1)?.imageUrl; "Kategorije" -> dao.category(1)?.imageUrl; else -> dao.meal(1)?.imageUrl }
                        url != null && url != oldUrl && container.images.localFile(url) != null
                    }
                }
                val url = runBlocking { when (section) { "Namirnice" -> dao.food(1)!!.imageUrl!!; "Kategorije" -> dao.category(1)!!.imageUrl!!; else -> dao.meal(1)!!.imageUrl!! } }
                assertNotNull(android.graphics.BitmapFactory.decodeFile(container.images.localFile(url)!!.absolutePath))
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Dohvati drugu sliku").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Dohvati drugu sliku").assertIsEnabled()
                compose.onNodeWithContentDescription("Natrag").performClick()
                compose.onNodeWithContentDescription("Natrag").performClick()
            }
        } finally {
            runBlocking { dao.saveFood(originals.first); dao.saveCategory(originals.second); dao.saveMeal(originals.third) }
        }
    }
}
