package hr.zdravalucija.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CategoryMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        LucijaDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())
    @Test fun versionOneMigratesPreservingFoodIdsMealSourcesAndHistory() {
        helper.createDatabase("category-migration",1).apply {
            execSQL("INSERT INTO categories VALUES (1, 'Voće', NULL, NULL, 1, 0)")
            execSQL("INSERT INTO foods VALUES (1, 'Jabuka', 1, 7, NULL, NULL, 1, 0)")
            execSQL("INSERT INTO foods VALUES (2, 'Banana', 1, 2, NULL, NULL, 1, 1)")
            execSQL("INSERT INTO meals VALUES (1, 'Sok', NULL, NULL, 1, 0)")
            execSQL("INSERT INTO meal_foods VALUES (1, 1)")
            execSQL("INSERT INTO consumption_entries VALUES (1, 1, 1, '2026-10-05', 1, 'group')")
            close()
        }
        helper.runMigrationsAndValidate("category-migration",2,true,LucijaDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT weeklyTarget, countingMode FROM categories WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(9,cursor.getInt(0)); assertEquals("ENTRIES",cursor.getString(1))
            }
            db.query("SELECT weeklyTarget FROM foods WHERE id = 1").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0,cursor.getInt(0)) }
            db.query("SELECT sourceMealId, entryGroupId FROM consumption_entries").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(1,cursor.getLong(0)); assertEquals("group",cursor.getString(1)) }
        }
    }
    @Test fun planMergeIsRepeatableAndPreservesHistoryMealsAndImages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context,LucijaDatabase::class.java).build()
        try {
            val repo = RoomFoodRepository(db,context)
            repo.initialize()
            val dao = db.dao()
            dao.saveCategory(CategoryEntity(3,"Meso"))
            dao.saveFood(dao.food(7)!!.copy(categoryId=3))
            dao.saveFood(dao.food(8)!!.copy(categoryId=3))
            dao.saveFood(dao.food(1)!!.copy(imageUrl="https://example.org/apple.jpg"))
            repo.addMealConsumption(1)
            repo.addFoodConsumption(1)
            val entries = dao.entries(); val meals = dao.meals(); val refs = dao.mealFoods()
            val planRepo = DietPlanRepository(db,context)
            val plan = planRepo.readBundled()
            planRepo.apply(plan)
            val categories = dao.categories(); val foods = dao.foods()
            planRepo.apply(plan)
            assertEquals(categories,dao.categories()); assertEquals(foods,dao.foods())
            assertEquals(entries,dao.entries()); assertEquals(meals,dao.meals()); assertEquals(refs,dao.mealFoods())
            assertEquals("https://example.org/apple.jpg",dao.food(1)!!.imageUrl)
            assertEquals(14,dao.categories().first { it.name == "Voće" }.weeklyTarget)
            assertEquals("Piletina / puretina",dao.category(dao.food(7)!!.categoryId)!!.name)
            assertEquals("Crveno meso",dao.category(dao.food(8)!!.categoryId)!!.name)
            assertFalse(dao.categories().first { it.name == "Meso" }.active)
        } finally { db.close() }
    }
    @Test fun automaticCatalogUpgradePreservesAliasesGoalsHistoryAndArchivedChoices() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context,LucijaDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.saveCategory(CategoryEntity(1,"Voće",weeklyTarget=21))
            dao.saveCategory(CategoryEntity(11,"Orašasti plodovi",weeklyTarget=5,countingMode="DAYS"))
            dao.saveCategory(CategoryEntity(8,"Piletina/puretina",weeklyTarget=3))
            dao.saveFood(FoodEntity(1,"Jabuka",1,imageQuery="apple fruit",active=false))
            dao.saveFood(FoodEntity(2,"Orah",11,imageUrl="https://example.org/walnut.jpg",imageQuery="walnuts nuts"))
            dao.saveMeal(MealEntity(1,"Moje jelo"))
            dao.insertMealFoods(listOf(MealFoodCrossRef(1,2)))
            dao.insertEntries(listOf(ConsumptionEntryEntity(1,2,1,"2026-10-05",1,"original")))
            dao.markSeeded()
            val repo = RoomFoodRepository(db,context)
            repo.initialize()
            assertEquals(84,dao.foods().size)
            assertEquals("Orasi",dao.food(2)!!.name)
            assertEquals("https://example.org/walnut.jpg",dao.food(2)!!.imageUrl)
            assertEquals(21,dao.category(1)!!.weeklyTarget)
            assertEquals(5,dao.category(11)!!.weeklyTarget)
            assertEquals(3,dao.category(8)!!.weeklyTarget)
            assertFalse(dao.food(1)!!.active)
            assertEquals(listOf(MealFoodCrossRef(1,2)),dao.mealFoods())
            assertEquals("original",dao.entries().single().entryGroupId)
            assertEquals(1,dao.catalogCount())
            dao.saveCategory(dao.category(1)!!.copy(weeklyTarget=17))
            RoomFoodRepository(db,context).initialize()
            assertEquals(84,dao.foods().size)
            assertEquals(17,dao.category(1)!!.weeklyTarget)
        } finally { db.close() }
    }
}
