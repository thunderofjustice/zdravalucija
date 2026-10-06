package hr.zdravalucija.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.room.Room

@RunWith(AndroidJUnit4::class)
class ImageRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test fun queryIsEncodedSafeAndCachedForRepeatedCalls() = runBlocking {
        val calls = AtomicInteger()
        val query = "orange juice & ${UUID.randomUUID()}"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            assertEquals(query, chain.request().url.queryParameter("q"))
            assertEquals("true", chain.request().url.queryParameter("safesearch"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"hits":[{"webformatURL":"https://cdn.pixabay.com/test.jpg"}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val repository = PixabayImageSearchRepository(context,client,"test-key")
        assertEquals("https://cdn.pixabay.com/test.jpg",repository.searchImage(query))
        assertEquals("https://cdn.pixabay.com/test.jpg",repository.searchImage(query))
        assertEquals(1,calls.get())
    }
    @Test fun missingApiKeyNeverNeedsNetwork() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { error("Network must not be used") }.build()
        assertNull(PixabayImageSearchRepository(context,client,"").searchImage("apple"))
    }
    @Test fun downloadedImageSurvivesNewStoreAndDoesNotDownloadAgain() = runBlocking {
        val calls = AtomicInteger()
        val bitmap = android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888)
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,output)
        bitmap.recycle()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(output.toByteArray().toResponseBody("image/jpeg".toMediaType())).build()
        }.build()
        val url = "https://cdn.pixabay.com/${UUID.randomUUID()}.jpg"
        val first = ImageStore(context,client)
        coroutineScope { repeat(3) { launch(Dispatchers.IO) { first.download(url) } } }
        val second = ImageStore(context,client)
        assertTrue(second.localFile(url)!!.exists())
        second.download(url)
        assertEquals(1,calls.get())
        second.localFile(url)!!.delete()
        Unit
    }
    @Test fun expiredRestoredUrlCanBeResolvedAgainWithoutChangingCachedPictures() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context,LucijaDatabase::class.java).build()
        val old = "https://cdn.pixabay.com/expired-${UUID.randomUUID()}.jpg"
        val fresh = "https://cdn.pixabay.com/fresh-${UUID.randomUUID()}.jpg"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val expired = chain.request().url.toString() == old
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (expired) 404 else 200).message(if (expired) "Not found" else "OK")
                .body(byteArrayOf(1,2,3).toResponseBody("image/jpeg".toMediaType())).build()
        }.build()
        val store = ImageStore(context,client)
        try {
            val repo = RoomFoodRepository(db,context)
            repo.initialize()
            repo.updateFood(db.dao().food(1)!!.copy(imageUrl=old))
            val search = object : ImageSearchRepository { override suspend fun searchImage(query: String) = fresh }
            val resolve = ResolveFoodImageUseCase(db.dao(),search,store)
            try { resolve(db.dao().food(1)!!); fail("Expected expired URL") } catch (_: ImageUnavailableException) { }
            assertNull(db.dao().food(1)!!.imageUrl)
            assertTrue(resolve(db.dao().food(1)!!))
            assertEquals(fresh,db.dao().food(1)!!.imageUrl)
            assertNotNull(store.localFile(fresh))
        } finally { store.localFile(fresh)?.delete(); db.close() }
    }
}
