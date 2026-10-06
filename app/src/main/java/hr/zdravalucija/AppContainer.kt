package hr.zdravalucija

import android.content.Context
import androidx.room.Room
import hr.zdravalucija.data.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database = Room.databaseBuilder(context, LucijaDatabase::class.java, "zdrava-lucija.db")
        .addMigrations(LucijaDatabase.MIGRATION_1_2).build()
    val repository = RoomFoodRepository(database, context)
    val preferences = PreferencesRepository(context)
    val plan = DietPlanRepository(database, context)
    val httpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    val imageSearch: ImageSearchRepository = PixabayImageSearchRepository(context, httpClient)
    val nameTranslator: NameTranslator = OnDeviceNameTranslator()
    val images = ImageStore(context, httpClient)
    val backup = BackupRepository(database, preferences, images)
    init {
        repository.onImagesNeeded { ImageWorker.enqueue(context) }
        ImageWorker.scheduleRetry(context)
    }
}
