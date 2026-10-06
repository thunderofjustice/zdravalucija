package hr.zdravalucija.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import hr.zdravalucija.LucijaApplication
import hr.zdravalucija.MainActivity
import hr.zdravalucija.data.isCustomImage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UploadImageFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as LucijaApplication).container

    @Test fun photoPickerCancellationAndSelectionWorkForFoodsCategoriesAndMeals() {
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("Dodaj hranu").fetchSemanticsNodes().isNotEmpty() }
        val dao = container.database.dao()
        val originals = runBlocking { Triple(dao.food(1)!!, dao.category(1)!!, dao.meal(1)!!) }
        val file = File(container.appContext.cacheDir, "picker-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(230, 90, 30))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        var cancel = true
        var launches = 0
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != MediaStore.ACTION_PICK_IMAGES) return null
                assertEquals("image/*", intent.type)
                launches++
                return if (cancel) Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                    else Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
            }
        }
        instrumentation.addMonitor(monitor)
        val importedUrls = mutableSetOf<String>()
        try {
            compose.onNodeWithText("POSTAVKE").performClick()
            for ((section, name, originalUrl) in listOf(
                Triple("Namirnice", originals.first.name, originals.first.imageUrl),
                Triple("Kategorije", originals.second.name, originals.second.imageUrl),
                Triple("Jela", originals.third.name, originals.third.imageUrl),
            )) {
                compose.onNodeWithText(section).performClick()
                compose.onNode(hasScrollAction()).performScrollToNode(hasText(name))
                compose.onNodeWithText(name).performClick()
                cancel = true
                compose.onNodeWithText("Odaberi svoju sliku").performScrollTo().performClick()
                compose.waitForIdle()
                fun currentUrl() = runBlocking { when (section) { "Namirnice" -> dao.food(1)!!.imageUrl; "Kategorije" -> dao.category(1)!!.imageUrl; else -> dao.meal(1)!!.imageUrl } }
                assertEquals(originalUrl, currentUrl())
                cancel = false
                compose.onNodeWithText("Odaberi svoju sliku").performClick()
                compose.waitUntil(15_000) { isCustomImage(currentUrl()) }
                val url = currentUrl()!!
                importedUrls += url
                assertNotNull(container.images.localFile(url))
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Dohvati drugu sliku").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Dohvati drugu sliku").assertIsEnabled()
                if (section == "Namirnice") {
                    compose.onNodeWithText("Naziv").performScrollTo().performTextReplacement(originals.first.name + " test")
                    compose.onNodeWithText("SPREMI").performClick()
                    compose.waitUntil(5_000) { runBlocking { dao.food(1)!!.name.endsWith(" test") } }
                    assertEquals(url, currentUrl())
                } else compose.onNodeWithContentDescription("Natrag").performClick()
                compose.onNodeWithContentDescription("Natrag").performClick()
            }
            assertEquals(6, launches)
        } finally {
            instrumentation.removeMonitor(monitor)
            runBlocking { dao.saveFood(originals.first); dao.saveCategory(originals.second); dao.saveMeal(originals.third) }
            file.delete()
            importedUrls.forEach { container.images.localFile(it)?.delete() }
        }
    }
}
