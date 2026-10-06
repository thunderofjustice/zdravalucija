package hr.zdravalucija.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LucijaDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name") fun observeCategories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM foods ORDER BY sortOrder, name") fun observeFoods(): Flow<List<FoodEntity>>
    @Query("SELECT * FROM meals ORDER BY sortOrder, name") fun observeMeals(): Flow<List<MealEntity>>
    @Query("SELECT * FROM meal_foods") fun observeMealFoods(): Flow<List<MealFoodCrossRef>>
    @Query("SELECT * FROM consumption_entries ORDER BY consumedAt DESC, id DESC") fun observeEntries(): Flow<List<ConsumptionEntryEntity>>
    @Query("SELECT * FROM consumption_entries WHERE localDate = :date ORDER BY consumedAt DESC, id DESC")
    fun observeEntriesForDate(date: String): Flow<List<ConsumptionEntryEntity>>
    @Query("SELECT * FROM consumption_entries WHERE localDate BETWEEN :start AND :end ORDER BY consumedAt DESC, id DESC")
    fun observeEntriesForWeek(start: String, end: String): Flow<List<ConsumptionEntryEntity>>
    @Query("SELECT * FROM foods WHERE id = :id") suspend fun food(id: Long): FoodEntity?
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun category(id: Long): CategoryEntity?
    @Query("SELECT * FROM meals WHERE id = :id") suspend fun meal(id: Long): MealEntity?
    @Query("SELECT * FROM foods WHERE id IN (SELECT foodId FROM meal_foods WHERE mealId = :mealId) ORDER BY sortOrder, id")
    suspend fun foodsForMeal(mealId: Long): List<FoodEntity>
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM foods ORDER BY sortOrder, id") suspend fun foods(): List<FoodEntity>
    @Query("SELECT * FROM meals ORDER BY sortOrder, id") suspend fun meals(): List<MealEntity>
    @Query("SELECT * FROM meal_foods") suspend fun mealFoods(): List<MealFoodCrossRef>
    @Query("SELECT * FROM consumption_entries ORDER BY consumedAt DESC, id DESC") suspend fun entries(): List<ConsumptionEntryEntity>
    @Query("SELECT COUNT(*) FROM seed_state WHERE id = 1") suspend fun seedCount(): Int
    @Query("SELECT COUNT(*) FROM seed_state WHERE id = 2") suspend fun catalogCount(): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun markSeeded(state: SeedStateEntity = SeedStateEntity())
    @Upsert suspend fun saveCategory(category: CategoryEntity): Long
    @Upsert suspend fun saveFood(food: FoodEntity): Long
    @Upsert suspend fun saveMeal(meal: MealEntity): Long
    @Insert suspend fun insertEntries(entries: List<ConsumptionEntryEntity>): List<Long>
    @Insert suspend fun insertMealFoods(refs: List<MealFoodCrossRef>)
    @Query("DELETE FROM meal_foods WHERE mealId = :id") suspend fun clearMealFoods(id: Long)
    @Query("DELETE FROM consumption_entries WHERE id = :id") suspend fun deleteEntry(id: Long)
    @Query("DELETE FROM consumption_entries WHERE entryGroupId = :groupId") suspend fun deleteEntryGroup(groupId: String)
    @Query("UPDATE foods SET imageUrl = :url WHERE id = :id AND imageUrl IS NULL AND COALESCE(imageQuery, name) = :query")
    suspend fun assignFoodImage(id: Long, url: String, query: String)
    @Query("UPDATE meals SET imageUrl = :url WHERE id = :id AND imageUrl IS NULL AND COALESCE(imageQuery, name) = :query")
    suspend fun assignMealImage(id: Long, url: String, query: String)
    @Query("UPDATE categories SET imageUrl = :url WHERE id = :id AND imageUrl IS NULL AND COALESCE(imageQuery, name) = :query")
    suspend fun assignCategoryImage(id: Long, url: String, query: String)
    @Query("UPDATE foods SET imageQuery = :query WHERE id = :id AND name = :name AND (imageQuery IS NULL OR TRIM(imageQuery) = '') AND imageUrl IS NULL")
    suspend fun assignFoodImageQuery(id: Long, name: String, query: String): Int
    @Query("UPDATE meals SET imageQuery = :query WHERE id = :id AND name = :name AND (imageQuery IS NULL OR TRIM(imageQuery) = '') AND imageUrl IS NULL")
    suspend fun assignMealImageQuery(id: Long, name: String, query: String): Int
    @Query("UPDATE categories SET imageQuery = :query WHERE id = :id AND name = :name AND (imageQuery IS NULL OR TRIM(imageQuery) = '') AND imageUrl IS NULL")
    suspend fun assignCategoryImageQuery(id: Long, name: String, query: String): Int
    @Query("UPDATE foods SET imageUrl = NULL WHERE id = :id AND imageUrl = :url")
    suspend fun invalidateFoodImage(id: Long, url: String)
    @Query("UPDATE meals SET imageUrl = NULL WHERE id = :id AND imageUrl = :url")
    suspend fun invalidateMealImage(id: Long, url: String)
    @Query("UPDATE categories SET imageUrl = NULL WHERE id = :id AND imageUrl = :url")
    suspend fun invalidateCategoryImage(id: Long, url: String)
    @Query("UPDATE foods SET imageUrl = :url, imageQuery = :query WHERE id = :id AND name = :name AND imageUrl IS :previous AND imageQuery IS :previousQuery")
    suspend fun replaceFoodImage(id: Long, name: String, previous: String?, previousQuery: String?, url: String, query: String?): Int
    @Query("UPDATE meals SET imageUrl = :url, imageQuery = :query WHERE id = :id AND name = :name AND imageUrl IS :previous AND imageQuery IS :previousQuery")
    suspend fun replaceMealImage(id: Long, name: String, previous: String?, previousQuery: String?, url: String, query: String?): Int
    @Query("UPDATE categories SET imageUrl = :url, imageQuery = :query WHERE id = :id AND name = :name AND imageUrl IS :previous AND imageQuery IS :previousQuery")
    suspend fun replaceCategoryImage(id: Long, name: String, previous: String?, previousQuery: String?, url: String, query: String?): Int
    @Query("DELETE FROM consumption_entries") suspend fun clearEntries()
    @Query("DELETE FROM meal_foods") suspend fun clearMealFoods()
    @Query("DELETE FROM meals") suspend fun clearMeals()
    @Query("DELETE FROM foods") suspend fun clearFoods()
    @Query("DELETE FROM categories") suspend fun clearCategories()
}
