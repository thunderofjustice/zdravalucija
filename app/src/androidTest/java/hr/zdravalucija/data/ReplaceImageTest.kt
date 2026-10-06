package hr.zdravalucija.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ReplaceImageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val translator = object : NameTranslator {
        override suspend fun toEnglish(name: String) = "translated food"
    }

    @Test fun alternativesAdvanceAndWrapWithoutRepeatingCurrentOrRequestingAgain() = runBlocking {
        val calls = AtomicInteger()
        val urls = listOf("https://cdn.pixabay.com/a.jpg", "https://cdn.pixabay.com/b.jpg", "https://cdn.pixabay.com/c.jpg")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            assertEquals("20", chain.request().url.queryParameter("per_page"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"hits":[{"webformatURL":"${urls[0]}"},{"webformatURL":"${urls[1]}"},{"webformatURL":"${urls[2]}"}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val query = "apple ${UUID.randomUUID()}"
        val search = PixabayImageSearchRepository(context, client, "test-key")
        assertEquals(urls[0], search.searchImage(query))
        assertEquals(urls[1], search.searchAlternative(query, urls[0]))
        assertEquals(urls[2], search.searchAlternative(query, urls[1]))
        assertEquals(urls[0], search.searchAlternative(query, urls[2]))
        assertEquals(1, calls.get())
    }

    @Test fun replacementPreservesTargetsMembershipAndHistoryForAllThreeTypes() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LucijaDatabase::class.java).build()
        val url = "https://cdn.pixabay.com/${UUID.randomUUID()}.jpg"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(byteArrayOf(1,2,3).toResponseBody("image/jpeg".toMediaType())).build()
        }.build()
        val store = ImageStore(context, client)
        try {
            val repo = RoomFoodRepository(db, context)
            repo.initialize()
            repo.addMealConsumption(1)
            val entries = db.dao().entries()
            val membership = db.dao().mealFoods()
            val food = db.dao().food(1)!!
            val category = db.dao().category(1)!!
            val meal = db.dao().meal(1)!!
            val search = object : ImageSearchRepository { override suspend fun searchImage(query: String) = url }
            val replace = ReplaceImageUseCase(db.dao(), search, store, translator)
            replace("foods", 1); replace("categories", 1); replace("meals", 1)
            assertEquals(food.copy(imageUrl = url), db.dao().food(1))
            assertEquals(category.copy(imageUrl = url), db.dao().category(1))
            assertEquals(meal.copy(imageUrl = url), db.dao().meal(1))
            assertEquals(entries, db.dao().entries())
            assertEquals(membership, db.dao().mealFoods())
            assertNotNull(ImageStore(context, client).localFile(url))
        } finally { store.localFile(url)?.delete(); db.close() }
    }

    @Test fun oneSearchResultHasNoAlternativeAndIsNotRequestedAgain() = runBlocking {
        val calls = AtomicInteger()
        val url = "https://cdn.pixabay.com/only.jpg"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"hits":[{"webformatURL":"$url"}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val query = "food ${UUID.randomUUID()}"
        val search = PixabayImageSearchRepository(context, client, "test-key")
        assertEquals(url, search.searchImage(query))
        assertNull(search.searchAlternative(query, url))
        assertNull(search.searchAlternative(query, url))
        assertEquals(1, calls.get())
    }

    @Test fun failureAndNoAlternativeLeaveTheOriginalPhotoIntact() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LucijaDatabase::class.java).build()
        try {
            RoomFoodRepository(db, context).initialize()
            val food = db.dao().food(1)!!.copy(imageUrl = "https://cdn.pixabay.com/original.jpg")
            db.dao().saveFood(food)
            val client = OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build()
            val store = ImageStore(context, client)
            for (candidate in listOf(food.imageUrl, "https://cdn.pixabay.com/new-${UUID.randomUUID()}.jpg")) {
                val search = object : ImageSearchRepository { override suspend fun searchImage(query: String) = candidate }
                try { ReplaceImageUseCase(db.dao(), search, store, translator)("foods", 1); fail("Expected failure") }
                catch (_: Exception) { }
                assertEquals(food, db.dao().food(1))
            }
        } finally { db.close() }
    }

    @Test fun translatedQueryAndConcurrentRenameAreHandledWithoutOverwritingNewerEdits() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LucijaDatabase::class.java).build()
        val url = "https://cdn.pixabay.com/${UUID.randomUUID()}.jpg"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            runBlocking { db.dao().saveFood(db.dao().food(1)!!.copy(name = "Renamed")) }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(byteArrayOf(1,2,3).toResponseBody("image/jpeg".toMediaType())).build()
        }.build()
        val store = ImageStore(context, client)
        try {
            RoomFoodRepository(db, context).initialize()
            db.dao().saveFood(db.dao().food(1)!!.copy(imageQuery = null))
            val search = object : ImageSearchRepository {
                override suspend fun searchImage(query: String): String { assertEquals("translated food", query); return url }
            }
            try { ReplaceImageUseCase(db.dao(), search, store, translator)("foods", 1); fail("Expected concurrent edit") }
            catch (_: IllegalStateException) { }
            assertEquals("Renamed", db.dao().food(1)!!.name)
            assertNull(db.dao().food(1)!!.imageUrl)
        } finally { store.localFile(url)?.delete(); db.close() }
    }
}
