package hr.zdravalucija.data

import androidx.room.withTransaction
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.time.LocalDate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class BackupData(
    val formatVersion: Int = 2,
    val categories: List<CategoryEntity>,
    val foods: List<FoodEntity>,
    val meals: List<MealEntity>,
    val mealFoods: List<MealFoodCrossRef>,
    val entries: List<ConsumptionEntryEntity>,
    val preferences: AppPreferences,
    val customImages: Map<String, String> = emptyMap(),
)

class BackupCodec {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    fun encode(data: BackupData): String { validate(data); return gson.toJson(data) }
    fun decode(json: String): BackupData {
        try {
            val root = JsonParser.parseString(json).asJsonObject
            fun integer(value: com.google.gson.JsonElement, positive: Boolean = false, max: Long = Long.MAX_VALUE) {
                require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
                val number = value.asString.toLongOrNull() ?: error("Cijeli broj je obavezan.")
                require(number >= if (positive) 1 else 0)
                require(number <= max)
            }
            integer(root.get("formatVersion"), max = Int.MAX_VALUE.toLong())
            listOf("categories", "foods", "meals", "mealFoods", "entries").forEach { key ->
                val rows = root.getAsJsonArray(key)
                rows.forEach { row ->
                    val objectRow = row.asJsonObject
                    if (key != "mealFoods") integer(objectRow.get("id"), true)
                    if (key in listOf("categories", "foods", "meals")) {
                        integer(objectRow.get("sortOrder"), max = Int.MAX_VALUE.toLong())
                        require(objectRow.get("active").asJsonPrimitive.isBoolean)
                        require(objectRow.get("name").asJsonPrimitive.isString)
                    }
                    if (key == "foods") { integer(objectRow.get("weeklyTarget"), max = Int.MAX_VALUE.toLong()); integer(objectRow.get("categoryId"), true) }
                    if (key == "categories" && root.get("formatVersion").asInt == 2) {
                        integer(objectRow.get("weeklyTarget"), max = Int.MAX_VALUE.toLong())
                        require(objectRow.get("countingMode").asJsonPrimitive.isString)
                    }
                    if (key == "mealFoods") { integer(objectRow.get("mealId"), true); integer(objectRow.get("foodId"), true) }
                    if (key == "entries") {
                        integer(objectRow.get("foodId"), true); integer(objectRow.get("consumedAt"))
                        objectRow.get("sourceMealId")?.takeUnless { it.isJsonNull }?.let { integer(it, true) }
                    }
                }
            }
            integer(root.getAsJsonObject("preferences").get("firstDayOfWeek"), true, max = 7)
            if (root.get("formatVersion").asInt == 1) {
                val oldFoods = root.getAsJsonArray("foods")
                root.getAsJsonArray("categories").forEach { element ->
                    val category = element.asJsonObject
                    val target = oldFoods.filter { it.asJsonObject.get("categoryId").asLong == category.get("id").asLong && it.asJsonObject.get("active").asBoolean }
                        .sumOf { it.asJsonObject.get("weeklyTarget").asLong }.coerceAtMost(Int.MAX_VALUE.toLong())
                    category.addProperty("weeklyTarget", target)
                    category.addProperty("countingMode", "ENTRIES")
                }
                oldFoods.forEach { it.asJsonObject.addProperty("weeklyTarget", 0) }
                root.addProperty("formatVersion", 2)
            }
            if (!root.has("customImages")) root.add("customImages", com.google.gson.JsonObject())
            val data = gson.fromJson(root, BackupData::class.java)
            validate(data)
            return data
        } catch (error: Exception) {
            throw IllegalArgumentException("Kopija nije valjana ili koristi nepodržan format.", error)
        }
    }
    fun validate(data: BackupData) {
        require(data.formatVersion == 2)
        require(data.preferences.firstDayOfWeek in 1..7 && data.preferences.theme in ThemeMode.entries)
        val customUrls = (data.categories.map { it.imageUrl } + data.foods.map { it.imageUrl } + data.meals.map { it.imageUrl })
            .filterNotNull().filter(::isCustomImage).toSet()
        require(data.customImages.keys == customUrls)
        var imageBytes = 0L
        data.customImages.forEach { (url, encoded) ->
            require(encoded.length <= 4 * 1024 * 1024)
            val bytes = java.util.Base64.getDecoder().decode(encoded)
            require(bytes.size in 1..3 * 1024 * 1024 && customImageUrl(bytes) == url)
            imageBytes += bytes.size
            require(imageBytes <= 70L * 1024 * 1024)
        }
        fun unique(ids: List<Long>): Set<Long> { require(ids.all { it > 0 } && ids.distinct().size == ids.size); return ids.toSet() }
        val categories = unique(data.categories.map { it.id })
        val foods = unique(data.foods.map { it.id })
        val meals = unique(data.meals.map { it.id })
        unique(data.entries.map { it.id })
        data.categories.forEach {
            require(it.name.isNotBlank() && it.sortOrder >= 0 && it.weeklyTarget >= 0)
            require(it.countingMode in listOf("ENTRIES", "DAYS") && (it.countingMode != "DAYS" || it.weeklyTarget <= 7))
            validUrl(it.imageUrl)
        }
        data.foods.forEach { require(it.name.isNotBlank() && it.categoryId in categories && it.weeklyTarget >= 0 && it.sortOrder >= 0); validUrl(it.imageUrl) }
        data.meals.forEach { require(it.name.isNotBlank() && it.sortOrder >= 0); validUrl(it.imageUrl) }
        require(data.mealFoods.distinct().size == data.mealFoods.size)
        data.mealFoods.forEach { require(it.foodId in foods && it.mealId in meals) }
        data.meals.filter { it.active }.forEach { meal -> require(data.mealFoods.any { it.mealId == meal.id }) }
        data.entries.forEach { entry ->
            require(entry.foodId in foods && (entry.sourceMealId == null || entry.sourceMealId in meals))
            require(entry.consumedAt >= 0 && entry.entryGroupId.isNotBlank())
            val parsed = runCatching { LocalDate.parse(entry.localDate).toString() }.getOrNull()
            require(parsed != null && parsed == entry.localDate)
        }
        data.entries.groupBy { it.entryGroupId }.values.forEach { group ->
            require(group.map { Triple(it.sourceMealId, it.consumedAt, it.localDate) }.distinct().size == 1)
            if (group.first().sourceMealId == null) require(group.size == 1)
            else require(group.map { it.foodId }.distinct().size == group.size)
        }
    }
    private fun validUrl(url: String?) { require(url == null || isCustomImage(url) || (url.startsWith("https://") && java.net.URI(url).host != null)) }
}

class BackupRepository(private val db: LucijaDatabase, private val prefs: PreferencesRepository, private val images: ImageStore? = null) {
    private val mutex = Mutex()
    val codec = BackupCodec()
    suspend fun export(): String = mutex.withLock {
        val preferences = prefs.preferences.first()
        val snapshot = db.withTransaction {
            val dao = db.dao()
            BackupData(categories = dao.categories(), foods = dao.foods(), meals = dao.meals(),
                mealFoods = dao.mealFoods(), entries = dao.entries(), preferences = preferences)
        }
        val urls = (snapshot.categories.map { it.imageUrl } + snapshot.foods.map { it.imageUrl } + snapshot.meals.map { it.imageUrl })
            .filterNotNull().filter(::isCustomImage).distinct()
        val photos = urls.associateWith { url ->
            val file = requireNotNull(images?.localFile(url)) { "Vlastita fotografija nedostaje u sigurnosnoj kopiji." }
            java.util.Base64.getEncoder().encodeToString(file.readBytes())
        }
        codec.encode(snapshot.copy(customImages = photos))
    }
    suspend fun restore(data: BackupData) = mutex.withLock {
        codec.validate(data)
        data.customImages.forEach { (url, encoded) ->
            requireNotNull(images) { "Vlastite fotografije se ne mogu vratiti." }.restoreCustom(url, java.util.Base64.getDecoder().decode(encoded))
        }
        val previousPreferences = prefs.preferences.first()
        try {
            db.withTransaction {
                val dao = db.dao()
                dao.clearEntries(); dao.clearMealFoods(); dao.clearMeals(); dao.clearFoods(); dao.clearCategories()
                data.categories.forEach { dao.saveCategory(it) }
                data.foods.forEach { dao.saveFood(it) }
                data.meals.forEach { dao.saveMeal(it) }
                dao.insertMealFoods(data.mealFoods)
                dao.insertEntries(data.entries)
                dao.markSeeded()
                dao.markSeeded(SeedStateEntity(2))
                prefs.save(data.preferences)
            }
        } catch (error: Exception) {
            withContext(NonCancellable) { prefs.save(previousPreferences) }
            throw error
        }
    }
}
