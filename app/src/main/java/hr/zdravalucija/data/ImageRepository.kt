package hr.zdravalucija.data

import android.content.Context
import com.google.gson.Gson
import hr.zdravalucija.BuildConfig
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

interface ImageSearchRepository {
    suspend fun searchImage(query: String): String?
    suspend fun searchAlternative(query: String, currentUrl: String?): String? = searchImage(query)?.takeUnless { it == currentUrl }
}
class ImageUnavailableException(code: Int) : IOException("Image no longer available: HTTP $code")
private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }

class PixabayImageSearchRepository(context: Context, private val client: OkHttpClient,
    private val apiKey: String = BuildConfig.PIXABAY_API_KEY) : ImageSearchRepository {
    private val cacheDirectory = File(context.filesDir, "image-search").apply { mkdirs() }
    private val mutex = Mutex()
    private val gson = Gson()
    override suspend fun searchImage(query: String): String? = search(query, null, false)
    override suspend fun searchAlternative(query: String, currentUrl: String?): String? = search(query, currentUrl, true)
    private suspend fun search(query: String, currentUrl: String?, alternative: Boolean): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        mutex.withLock {
            val normalized = query.trim().take(100)
            val file = File(cacheDirectory, digest(normalized + "|20") + ".json")
            val cached = try { if (file.exists()) gson.fromJson(file.readText(), CachedSearch::class.java) else null } catch (_: Exception) { null }
            fun select(urls: List<String>): String? {
                if (!alternative) return urls.firstOrNull()
                val index = urls.indexOf(currentUrl)
                return (urls.drop(index + 1) + urls.take(index.coerceAtLeast(0))).firstOrNull { it != currentUrl }
            }
            if (cached != null && System.currentTimeMillis() - cached.at in 0 until TimeUnit.HOURS.toMillis(24)) return@withLock select(cached.urls)
            val url = "https://pixabay.com/api/".toHttpUrl().newBuilder()
                .addQueryParameter("key", apiKey).addQueryParameter("q", normalized)
                .addQueryParameter("image_type", "photo").addQueryParameter("category", "food")
                .addQueryParameter("safesearch", "true").addQueryParameter("per_page", "20").build()
            val images = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Pixabay HTTP ${response.code}")
                val result = gson.fromJson(response.body!!.charStream(), PixabayResponse::class.java)
                result.hits.orEmpty().mapNotNull { it.webformatURL?.takeIf { url -> url.startsWith("https://") } }.distinct()
            }
            file.writeText(gson.toJson(CachedSearch(System.currentTimeMillis(), images)))
            select(images)
        }
    }
    private data class CachedSearch(val at: Long, val urls: List<String>)
    private data class PixabayResponse(val hits: List<PixabayHit>?)
    private data class PixabayHit(val webformatURL: String?)
}

/** Persistent private downloads, separate from Coil's evictable disk cache. */
class ImageStore(context: Context, private val client: OkHttpClient) {
    private val directory = File(context.filesDir, "food-images").apply { mkdirs() }
    private val downloadMutex = Mutex()
    val revision = MutableStateFlow(0)
    private fun file(url: String) = File(directory, if (isCustomImage(url)) url.removePrefix("local-image://") else digest(url) + ".jpg")
    fun localFile(url: String?): File? = url?.let { file(it).takeIf { image -> image.exists() && image.length() > 0 } }
    suspend fun restoreCustom(url: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        require(isCustomImage(url) && customImageUrl(bytes) == url)
        require(bytes.size in 1..3 * 1024 * 1024)
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..1600 && bounds.outHeight in 1..1600) { "Kopija sadrži preveliku fotografiju." }
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        require(bitmap != null) { "Kopija sadrži nevaljanu fotografiju." }
        bitmap.recycle()
        downloadMutex.withLock {
            if (localFile(url) == null) {
                val target = file(url)
                val temporary = File(directory, target.name + ".tmp")
                try {
                    temporary.writeBytes(bytes)
                    check(temporary.renameTo(target)) { "Fotografija se nije mogla spremiti." }
                } finally { temporary.delete() }
                revision.value += 1
            }
        }
    }
    suspend fun download(url: String) = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            if (localFile(url) != null) return@withContext
            if (isCustomImage(url)) throw IOException("Vlastita fotografija nije dostupna.")
            val parsed = url.toHttpUrl()
            require(parsed.scheme == "https")
            val target = file(url)
            val temporary = File(directory, target.name + ".tmp")
            try {
                client.newCall(Request.Builder().url(parsed).build()).execute().use { response ->
                    if (response.code in listOf(403, 404, 410)) throw ImageUnavailableException(response.code)
                    if (!response.isSuccessful) throw IOException("Image HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty image")
                    require(body.contentType()?.type == "image")
                    require(body.contentLength() <= 12 * 1024 * 1024)
                    body.byteStream().use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > 12 * 1024 * 1024) throw IOException("Image too large")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
                if (!temporary.renameTo(target)) throw IOException("Cannot persist image")
                revision.value += 1
            } finally {
                temporary.delete()
            }
        }
    }
}

class ResolveFoodImageUseCase(private val dao: LucijaDao, private val search: ImageSearchRepository, private val store: ImageStore,
    private val translator: NameTranslator = OnDeviceNameTranslator()) {
    suspend operator fun invoke(food: FoodEntity): Boolean {
        val query = if (food.imageUrl != null) food.imageQuery ?: food.name else food.imageQuery?.takeIf { it.isNotBlank() } ?: run {
            val english = translator.toEnglish(food.name)
            if (dao.assignFoodImageQuery(food.id, food.name, english) == 0) return false
            english
        }
        val url = food.imageUrl ?: search.searchImage(query) ?: return false
        try { store.download(url) } catch (error: ImageUnavailableException) {
            if (food.imageUrl != null) dao.invalidateFoodImage(food.id, url)
            throw error
        }
        if (food.imageUrl == null) dao.assignFoodImage(food.id, url, query)
        return true
    }
}
class ResolveMealImageUseCase(private val dao: LucijaDao, private val search: ImageSearchRepository, private val store: ImageStore,
    private val translator: NameTranslator = OnDeviceNameTranslator()) {
    suspend operator fun invoke(meal: MealEntity): Boolean {
        val query = if (meal.imageUrl != null) meal.imageQuery ?: meal.name else meal.imageQuery?.takeIf { it.isNotBlank() } ?: run {
            val english = translator.toEnglish(meal.name)
            if (dao.assignMealImageQuery(meal.id, meal.name, english) == 0) return false
            english
        }
        val url = meal.imageUrl ?: search.searchImage(query) ?: return false
        try { store.download(url) } catch (error: ImageUnavailableException) {
            if (meal.imageUrl != null) dao.invalidateMealImage(meal.id, url)
            throw error
        }
        if (meal.imageUrl == null) dao.assignMealImage(meal.id, url, query)
        return true
    }
}
