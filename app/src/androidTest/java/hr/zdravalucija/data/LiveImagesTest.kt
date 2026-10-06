package hr.zdravalucija.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.zdravalucija.BuildConfig
import hr.zdravalucija.LucijaApplication
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs real authenticated Pixabay searches only when a local development key is configured. */
@RunWith(AndroidJUnit4::class)
class LiveImagesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = (context as LucijaApplication).container

    private suspend fun awaitImages(): List<String> = withTimeout(180_000) {
        val dao = container.database.dao()
        while (true) {
            val urls = dao.foods().filter { it.active }.map { it.imageUrl } +
                dao.meals().filter { it.active }.map { it.imageUrl } +
                dao.categories().filter { it.active }.map { it.imageUrl }
            if (urls.isNotEmpty() && urls.all { it != null && container.images.localFile(it) != null }) {
                return@withTimeout urls.filterNotNull()
            }
            delay(500)
        }
        @Suppress("UNREACHABLE_CODE") emptyList()
    }

    @Test fun realSeedPhotosDownloadDecodeAndRemainAvailableWithoutNetwork() = runBlocking {
        assumeTrue(BuildConfig.PIXABAY_API_KEY.isNotBlank())
        container.repository.initialize()
        ImageWorker.enqueue(context)
        val urls = awaitImages()
        val offlineClient = OkHttpClient.Builder().addInterceptor { error("An offline image must not need a request") }.build()
        val offlineStore = ImageStore(context, offlineClient)
        urls.forEach { url ->
            val file = requireNotNull(offlineStore.localFile(url))
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            assertNotNull("Downloaded photo must be a valid bitmap", bitmap)
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
            bitmap.recycle()
            offlineStore.download(url)
        }
        val saved = container.database.dao().foods().associate { it.id to it.imageUrl }
        ImageWorker.enqueue(context)
        delay(1500)
        assertEquals(saved, container.database.dao().foods().associate { it.id to it.imageUrl })
    }

    @Test fun newlySavedFoodAndMealResolveAutomaticallyWithoutWaitingForNetworkOnSave() = runBlocking {
        assumeTrue(BuildConfig.PIXABAY_API_KEY.isNotBlank())
        container.repository.initialize()
        val dao = container.database.dao()
        val category = dao.categories().first { it.active }
        val foodId = container.repository.updateFood(FoodEntity(name = "Test ananas", categoryId = category.id,
            weeklyTarget = 0, imageQuery = "pineapple fruit", active = true))
        assertNotNull(dao.food(foodId))
        val mealId = container.repository.updateMeal(MealEntity(name = "Test sok", imageQuery = "fresh orange juice"), setOf(foodId))
        assertEquals(listOf(foodId), dao.foodsForMeal(mealId).map { it.id })
        try {
            withTimeout(180_000) {
                while (dao.food(foodId)?.imageUrl == null || dao.meal(mealId)?.imageUrl == null) delay(500)
            }
            assertNotNull(container.images.localFile(dao.food(foodId)!!.imageUrl))
            assertNotNull(container.images.localFile(dao.meal(mealId)!!.imageUrl))
        } finally {
            container.repository.updateMeal(dao.meal(mealId)!!.copy(active = false), setOf(foodId))
            container.repository.updateFood(dao.food(foodId)!!.copy(active = false))
        }
    }
}
