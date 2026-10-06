package hr.zdravalucija.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRepositoryTest {
    private lateinit var db: LucijaDatabase
    private lateinit var repo: RoomFoodRepository
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, LucijaDatabase::class.java).build()
        repo = RoomFoodRepository(db,context) { Clock.fixed(Instant.parse("2026-10-04T22:30:00Z"),ZoneId.of("Europe/Zagreb")) }
        repo.initialize()
    }
    @After fun cleanup() { db.close() }
    @Test fun seedAndLocalDate() = runBlocking {
        assertEquals(84,db.dao().foods().size)
        repo.addFoodConsumption(1)
        assertEquals("2026-10-05",db.dao().entries().single().localDate)
    }
    @Test fun mealUndoPreservesSeparateManualBroccoli() = runBlocking {
        val direct = repo.addFoodConsumption(4)
        val firstMeal = repo.addMealConsumption(1)
        val secondMeal = repo.addMealConsumption(1)
        val mealEntries = db.dao().entries().filter { it.entryGroupId == firstMeal }
        assertEquals(setOf(4L,5L,6L),mealEntries.map { it.foodId }.toSet())
        assertTrue(mealEntries.all { it.sourceMealId == 1L })
        repo.deleteEntryGroup(firstMeal)
        assertEquals(4,db.dao().entries().size)
        assertEquals(1,db.dao().entries().count { it.entryGroupId == direct && it.sourceMealId == null })
        assertEquals(3,db.dao().entries().count { it.entryGroupId == secondMeal })
    }
    @Test fun juiceIsOneOrdinaryOrangeEntryWithMealSource() = runBlocking {
        repo.addMealConsumption(3)
        val entry = db.dao().entries().single()
        assertEquals(3L,entry.foodId)
        assertEquals(3L,entry.sourceMealId)
    }
    @Test fun roomRollsBackWholeBatchWhenAnyFoodDoesNotExist() = runBlocking {
        try {
            db.withTransaction {
                db.dao().insertEntries(listOf(ConsumptionEntryEntity(foodId=1,consumedAt=1,localDate="2026-10-05",entryGroupId="bad"),
                    ConsumptionEntryEntity(foodId=999,consumedAt=1,localDate="2026-10-05",entryGroupId="bad")))
            }
            fail("Expected foreign-key failure")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertTrue(db.dao().entries().isEmpty())
    }
    @Test fun individualDeletionLeavesOtherMealMembers() = runBlocking {
        repo.addMealConsumption(1)
        repo.deleteEntry(db.dao().entries().first().id)
        assertEquals(2,db.dao().entries().size)
    }
    @Test fun archivePreservesHistoryAndMealMembership() = runBlocking {
        repo.addMealConsumption(1)
        repo.updateFood(db.dao().food(4)!!.copy(active=false))
        assertEquals(3,db.dao().entries().size)
        assertEquals(3,db.dao().foodsForMeal(1).size)
    }
    @Test fun backupRestoresAllRowsAndSettings() = runBlocking {
        val prefs = PreferencesRepository(context)
        val previous = prefs.preferences.first()
        try {
            val backup = BackupRepository(db,prefs)
            repo.addMealConsumption(1)
            prefs.save(AppPreferences(7,ThemeMode.DARK))
            val data = backup.codec.decode(backup.export())
            repo.deleteEntryGroup(db.dao().entries().first().entryGroupId)
            backup.restore(data)
            assertEquals(3,db.dao().entries().size)
            assertEquals(AppPreferences(7,ThemeMode.DARK), prefs.preferences.first())
        } finally { prefs.save(previous) }
    }
}
