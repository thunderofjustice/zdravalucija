package hr.zdravalucija.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import hr.zdravalucija.LucijaApplication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ImageTranslationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun store() = ImageStore(context,OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(byteArrayOf(1,2,3).toResponseBody("image/jpeg".toMediaType())).build()
    }.build())

    @Test fun translatedQueryIsSavedBeforeSearchAndReusedForFoodAndMeal() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context,LucijaDatabase::class.java).build()
        val imageStore = store()
        val url = "https://example.org/${UUID.randomUUID()}.jpg"
        try {
            val repo = RoomFoodRepository(db,context); repo.initialize()
            val foodId = repo.updateFood(FoodEntity(name="Kruška",categoryId=1))
            val mealId = repo.updateMeal(MealEntity(name="Juha od rajčice"),setOf(foodId))
            var calls = 0
            val translator = object : NameTranslator {
                override suspend fun toEnglish(name: String): String { calls++; return if(name=="Kruška") "pear" else "tomato soup" }
            }
            val search = object : ImageSearchRepository {
                override suspend fun searchImage(query: String): String {
                    assertTrue(query == "pear" || query == "tomato soup")
                    assertTrue(db.dao().food(foodId)!!.imageQuery == query || db.dao().meal(mealId)!!.imageQuery == query)
                    return url
                }
            }
            assertTrue(ResolveFoodImageUseCase(db.dao(),search,imageStore,translator)(db.dao().food(foodId)!!))
            assertTrue(ResolveMealImageUseCase(db.dao(),search,imageStore,translator)(db.dao().meal(mealId)!!))
            assertEquals(2,calls)
            val offlineTranslator = object : NameTranslator { override suspend fun toEnglish(name: String): String = error("Saved photos must not translate again") }
            assertTrue(ResolveMealImageUseCase(db.dao(),search,imageStore,offlineTranslator)(db.dao().meal(mealId)!!))
            assertEquals("tomato soup",db.dao().meal(mealId)!!.imageQuery)
        } finally { imageStore.localFile(url)?.delete(); db.close() }
    }

    @Test fun failedTranslationDoesNotSearchInCroatianAndRenamedRowsRejectStaleQuery() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context,LucijaDatabase::class.java).build()
        try {
            val repo = RoomFoodRepository(db,context); repo.initialize()
            val foodId = repo.updateFood(FoodEntity(name="Kruška",categoryId=1))
            val food = db.dao().food(foodId)!!
            val search = object : ImageSearchRepository { override suspend fun searchImage(query: String): String = error("Must not search without a current English query") }
            val failing = object : NameTranslator { override suspend fun toEnglish(name: String): String = throw java.io.IOException("Offline") }
            try { ResolveFoodImageUseCase(db.dao(),search,store(),failing)(food); fail("Expected failure") } catch (_: java.io.IOException) { }
            assertNull(db.dao().food(foodId)!!.imageQuery)
            val renamed = object : NameTranslator {
                override suspend fun toEnglish(name: String): String { repo.updateFood(food.copy(name="Breskva")); return "pear" }
            }
            assertFalse(ResolveFoodImageUseCase(db.dao(),search,store(),renamed)(food))
            assertNull(db.dao().food(foodId)!!.imageQuery)
        } finally { db.close() }
    }

    @Test fun realCroatianNamesTranslateAndFindDecodablePixabayPhotos() = runBlocking {
        org.junit.Assume.assumeTrue(hr.zdravalucija.BuildConfig.PIXABAY_API_KEY.isNotBlank())
        val container = (context as LucijaApplication).container
        val db = Room.inMemoryDatabaseBuilder(context,LucijaDatabase::class.java).build()
        try {
            val repo = RoomFoodRepository(db,context); repo.initialize()
            val foodId = repo.updateFood(FoodEntity(name="Kruška",categoryId=1))
            val mealId = repo.updateMeal(MealEntity(name="Juha od rajčice"),setOf(foodId))
            withTimeout(300_000) {
                assertTrue(ResolveFoodImageUseCase(db.dao(),container.imageSearch,container.images,container.nameTranslator)(db.dao().food(foodId)!!))
                assertTrue(ResolveMealImageUseCase(db.dao(),container.imageSearch,container.images,container.nameTranslator)(db.dao().meal(mealId)!!))
            }
            val food = db.dao().food(foodId)!!; val meal = db.dao().meal(mealId)!!
            assertTrue(food.imageQuery!!.lowercase().contains("pear"))
            assertTrue(meal.imageQuery!!.lowercase().contains("tomato") && meal.imageQuery!!.lowercase().contains("soup"))
            listOf(food.imageUrl,meal.imageUrl).forEach { url ->
                val bitmap = android.graphics.BitmapFactory.decodeFile(container.images.localFile(url)!!.absolutePath)
                assertNotNull(bitmap); bitmap.recycle()
            }
        } finally { db.close() }
    }
}
