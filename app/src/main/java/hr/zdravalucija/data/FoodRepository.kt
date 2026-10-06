package hr.zdravalucija.data

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class FoodSnapshot(
    val categories: List<CategoryEntity> = emptyList(),
    val foods: List<FoodEntity> = emptyList(),
    val meals: List<MealEntity> = emptyList(),
    val mealFoods: List<MealFoodCrossRef> = emptyList(),
    val entries: List<ConsumptionEntryEntity> = emptyList(),
)

interface FoodRepository {
    fun observeSnapshot(): Flow<FoodSnapshot>
    fun observeFoods(): Flow<List<FoodEntity>>
    fun observeMeals(): Flow<List<MealEntity>>
    fun observeEntriesForDate(date: LocalDate): Flow<List<ConsumptionEntryEntity>>
    fun observeEntriesForWeek(startDate: LocalDate, endDate: LocalDate): Flow<List<ConsumptionEntryEntity>>
    suspend fun addFoodConsumption(foodId: Long): String
    suspend fun addMealConsumption(mealId: Long): String
    suspend fun deleteEntry(entryId: Long)
    suspend fun deleteEntryGroup(entryGroupId: String)
    suspend fun updateFood(food: FoodEntity): Long
    suspend fun updateMeal(meal: MealEntity, foodIds: Set<Long>): Long
    suspend fun updateCategory(category: CategoryEntity): Long
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoomFoodRepository(
    private val db: LucijaDatabase,
    private val context: Context,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) : FoodRepository {
    private val dao = db.dao()
    private val seedMutex = Mutex()
    private val ready = MutableStateFlow(false)
    private var onChanged: () -> Unit = {}
    fun onImagesNeeded(callback: () -> Unit) { onChanged = callback }

    suspend fun initialize() = seedMutex.withLock {
        if (ready.value) return@withLock
        db.withTransaction {
            if (dao.seedCount() == 0) {
                val gson = Gson()
                fun <T> read(file: String, token: TypeToken<List<T>>): List<T> =
                    context.assets.open("seed/$file").bufferedReader().use { gson.fromJson(it, token.type) }
                read("categories.json", object : TypeToken<List<CategoryEntity>>() {}).forEach { dao.saveCategory(it) }
                read("foods.json", object : TypeToken<List<FoodEntity>>() {}).forEach { dao.saveFood(it) }
                val seeds = read("meals.json", object : TypeToken<List<MealSeed>>() {})
                seeds.forEach { seed ->
                    dao.saveMeal(seed.meal)
                    dao.insertMealFoods(seed.foodIds.distinct().map { MealFoodCrossRef(seed.meal.id, it) })
                }
                dao.markSeeded()
                dao.markSeeded(SeedStateEntity(2))
            }
            if (dao.catalogCount() == 0) {
                // Upgrade an existing catalog once; intentionally empty backups remain empty.
                if (dao.categories().isNotEmpty()) {
                    val plans = DietPlanRepository(db, context)
                    plans.apply(plans.readBundled(), preserveExistingChoices = true)
                }
                dao.markSeeded(SeedStateEntity(2))
            }
        }
        ready.value = true
        onChanged()
    }

    override fun observeSnapshot(): Flow<FoodSnapshot> = ready.filter { it }.flatMapLatest {
        combine(dao.observeCategories(), dao.observeFoods(), dao.observeMeals(), dao.observeMealFoods(), dao.observeEntries()) {
            categories, foods, meals, refs, entries -> FoodSnapshot(categories, foods, meals, refs, entries)
        }
    }
    override fun observeFoods() = dao.observeFoods()
    override fun observeMeals() = dao.observeMeals()
    override fun observeEntriesForDate(date: LocalDate) = dao.observeEntriesForDate(date.toString())
    override fun observeEntriesForWeek(startDate: LocalDate, endDate: LocalDate) = dao.observeEntriesForWeek(startDate.toString(), endDate.toString())

    override suspend fun addFoodConsumption(foodId: Long): String {
        initialize()
        val group = UUID.randomUUID().toString()
        val now = clock()
        val instant = now.instant()
        val date = instant.atZone(now.zone).toLocalDate().toString()
        db.withTransaction {
            val food = requireNotNull(dao.food(foodId)) { "Namirnica ne postoji." }
            require(food.active && dao.category(food.categoryId)?.active == true) { "Namirnica nije aktivna." }
            dao.insertEntries(listOf(ConsumptionEntryEntity(foodId = foodId, consumedAt = instant.toEpochMilli(),
                localDate = date, entryGroupId = group)))
        }
        return group
    }

    override suspend fun addMealConsumption(mealId: Long): String {
        initialize()
        val group = UUID.randomUUID().toString()
        val now = clock()
        val instant = now.instant()
        val date = instant.atZone(now.zone).toLocalDate().toString()
        db.withTransaction {
            require(dao.meal(mealId)?.active == true) { "Jelo nije aktivno." }
            val foods = dao.foodsForMeal(mealId)
            require(foods.isNotEmpty()) { "Odaberi barem jednu namirnicu u jelu." }
            // A meal always inserts all its members, including archived members retained for history.
            dao.insertEntries(foods.map { ConsumptionEntryEntity(foodId = it.id, consumedAt = instant.toEpochMilli(),
                localDate = date, sourceMealId = mealId, entryGroupId = group) })
        }
        return group
    }
    override suspend fun deleteEntry(entryId: Long) = dao.deleteEntry(entryId)
    override suspend fun deleteEntryGroup(entryGroupId: String) = dao.deleteEntryGroup(entryGroupId)
    override suspend fun updateFood(food: FoodEntity): Long {
        require(food.name.isNotBlank() && food.weeklyTarget >= 0)
        val id = db.withTransaction {
            require(dao.category(food.categoryId) != null) { "Odaberi kategoriju." }
            val inserted = dao.saveFood(food.copy(name = food.name.trim(), imageQuery = food.imageQuery?.trim()?.takeIf { it.isNotEmpty() }))
            if (food.id == 0L) inserted else food.id
        }
        onChanged()
        return id
    }
    override suspend fun updateCategory(category: CategoryEntity): Long {
        require(category.name.isNotBlank() && category.weeklyTarget >= 0 && category.countingMode in listOf("ENTRIES", "DAYS"))
        require(category.countingMode != "DAYS" || category.weeklyTarget <= 7)
        val id = dao.saveCategory(category.copy(name = category.name.trim()))
        onChanged()
        return if (category.id == 0L) id else category.id
    }
    override suspend fun updateMeal(meal: MealEntity, foodIds: Set<Long>): Long {
        require(meal.name.isNotBlank() && foodIds.isNotEmpty()) { "Odaberi barem jednu namirnicu." }
        val id = db.withTransaction {
            foodIds.forEach { require(dao.food(it) != null) }
            val inserted = dao.saveMeal(meal.copy(name = meal.name.trim(), imageQuery = meal.imageQuery?.trim()?.takeIf { it.isNotEmpty() }))
            val saved = if (meal.id == 0L) inserted else meal.id
            dao.clearMealFoods(saved)
            dao.insertMealFoods(foodIds.map { MealFoodCrossRef(saved, it) })
            saved
        }
        onChanged()
        return id
    }
}

data class MealSeed(val meal: MealEntity, val foodIds: List<Long>)
