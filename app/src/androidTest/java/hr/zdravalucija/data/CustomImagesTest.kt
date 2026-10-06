package hr.zdravalucija.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class CustomImagesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun photo(width: Int = 1800, height: Int = 1200): File {
        val file = File(context.cacheDir, "selected-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(70, 160, 40))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
    private val offlineClient = OkHttpClient.Builder().addInterceptor { error("Custom images must never use the network") }.build()

    @Test fun allEntityTypesUsePrivatePhotosOfflineEvenAfterOriginalIsDeleted() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LucijaDatabase::class.java).build()
        val original = photo()
        val store = ImageStore(context, offlineClient)
        var customUrl: String? = null
        try {
            val repo = RoomFoodRepository(db, context)
            repo.initialize()
            repo.addMealConsumption(1)
            val entries = db.dao().entries()
            val membership = db.dao().mealFoods()
            val food = db.dao().food(1)!!
            val category = db.dao().category(1)!!
            val meal = db.dao().meal(1)!!
            val upload = UploadImageUseCase(db.dao(), store, context)
            for (type in listOf("foods", "categories", "meals")) upload(type, 1, Uri.fromFile(original))
            customUrl = db.dao().food(1)!!.imageUrl!!
            assertTrue(isCustomImage(customUrl))
            assertEquals(food.copy(imageUrl = customUrl), db.dao().food(1))
            assertEquals(category.copy(imageUrl = customUrl), db.dao().category(1))
            assertEquals(meal.copy(imageUrl = customUrl), db.dao().meal(1))
            assertEquals(entries, db.dao().entries())
            assertEquals(membership, db.dao().mealFoods())
            original.delete()
            val reopened = ImageStore(context, offlineClient)
            reopened.download(customUrl)
            val bitmap = BitmapFactory.decodeFile(reopened.localFile(customUrl)!!.absolutePath)
            assertEquals(1600, bitmap.width)
            assertTrue(bitmap.height <= 1600)
            bitmap.recycle()
        } finally { original.delete(); store.localFile(customUrl)?.delete(); db.close() }
    }

    @Test fun jsonBackupRestoresCustomPhotoOnADeviceWithoutTheOriginalFile() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LucijaDatabase::class.java).build()
        val restoredDb = Room.inMemoryDatabaseBuilder(context, LucijaDatabase::class.java).build()
        val original = photo(200, 100)
        val store = ImageStore(context, offlineClient)
        var customUrl: String? = null
        try {
            RoomFoodRepository(db, context).initialize()
            UploadImageUseCase(db.dao(), store, context)("foods", 1, Uri.fromFile(original))
            customUrl = db.dao().food(1)!!.imageUrl!!
            val prefs = PreferencesRepository(context)
            val backups = BackupRepository(db, prefs, store)
            val json = backups.export()
            val data = backups.codec.decode(json)
            assertEquals(setOf(customUrl), data.customImages.keys)
            original.delete()
            store.localFile(customUrl)!!.delete()
            assertNull(store.localFile(customUrl))
            BackupRepository(restoredDb, prefs, ImageStore(context, offlineClient)).restore(data)
            assertEquals(customUrl, restoredDb.dao().food(1)!!.imageUrl)
            assertNotNull(BitmapFactory.decodeFile(store.localFile(customUrl)!!.absolutePath))
            assertEquals(db.dao().foods(), restoredDb.dao().foods())
        } finally { original.delete(); store.localFile(customUrl)?.delete(); db.close(); restoredDb.close() }
    }

    @Test fun invalidOrUnreadablePhotoKeepsPreviousPicture() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LucijaDatabase::class.java).build()
        val original = File(context.cacheDir, "invalid-${UUID.randomUUID()}.png")
        original.writeText("not an image")
        try {
            RoomFoodRepository(db, context).initialize()
            val food = db.dao().food(1)!!.copy(imageUrl = "https://cdn.pixabay.com/original.jpg")
            db.dao().saveFood(food)
            val upload = UploadImageUseCase(db.dao(), ImageStore(context, offlineClient), context)
            for (path in listOf(original, File(context.cacheDir, "missing-${UUID.randomUUID()}.png"))) {
                var failed = false
                try { upload("foods", 1, Uri.fromFile(path)) } catch (_: Exception) { failed = true }
                assertTrue(failed)
                assertEquals(food, db.dao().food(1))
            }
        } finally { original.delete(); db.close() }
    }
}
