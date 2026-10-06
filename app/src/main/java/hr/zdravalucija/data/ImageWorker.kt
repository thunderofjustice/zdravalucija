package hr.zdravalucija.data

import android.content.Context
import androidx.work.*
import hr.zdravalucija.BuildConfig
import hr.zdravalucija.LucijaApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class ImageWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (BuildConfig.PIXABAY_API_KEY.isBlank()) return Result.success()
        val container = (applicationContext as LucijaApplication).container
        container.repository.initialize()
        val dao = container.database.dao()
        var failed = false
        suspend fun attempt(block: suspend () -> Boolean) {
            try { if (!block()) failed = true }
            catch (error: Exception) { if (error is CancellationException) throw error; failed = true }
        }
        val resolveFood = ResolveFoodImageUseCase(dao, container.imageSearch, container.images, container.nameTranslator)
        val resolveMeal = ResolveMealImageUseCase(dao, container.imageSearch, container.images, container.nameTranslator)
        dao.foods().filter { it.active && !isCustomImage(it.imageUrl) && container.images.localFile(it.imageUrl) == null }.forEach { food -> attempt { resolveFood(food) } }
        dao.meals().filter { it.active && !isCustomImage(it.imageUrl) && container.images.localFile(it.imageUrl) == null }.forEach { meal -> attempt { resolveMeal(meal) } }
        dao.categories().filter { it.active && !isCustomImage(it.imageUrl) && container.images.localFile(it.imageUrl) == null }.forEach { category ->
            attempt {
                val query = if (category.imageUrl != null) category.imageQuery ?: category.name else
                    category.imageQuery?.takeIf { it.isNotBlank() } ?: run {
                        val english = container.nameTranslator.toEnglish(category.name)
                        if (dao.assignCategoryImageQuery(category.id, category.name, english) == 0) return@attempt false
                        english
                    }
                val url = category.imageUrl ?: container.imageSearch.searchImage(query)
                if (url != null) {
                    try { container.images.download(url) } catch (error: ImageUnavailableException) {
                        if (category.imageUrl != null) dao.invalidateCategoryImage(category.id, url)
                        throw error
                    }
                    if (category.imageUrl == null) dao.assignCategoryImage(category.id, url, query)
                    true
                }
                else false
            }
        }
        return if (failed && runAttemptCount < 4) Result.retry() else Result.success()
    }
    companion object {
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun enqueue(context: Context) {
            if (BuildConfig.PIXABAY_API_KEY.isBlank()) return
            WorkManager.getInstance(context).enqueueUniqueWork("resolve-food-images", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<ImageWorker>().setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build())
        }
        fun scheduleRetry(context: Context) {
            if (BuildConfig.PIXABAY_API_KEY.isBlank()) return
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("retry-food-images", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ImageWorker>(24, TimeUnit.HOURS).setConstraints(constraints).build())
        }
    }
}
