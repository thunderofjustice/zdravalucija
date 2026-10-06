package hr.zdravalucija.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CategoryEntity::class, FoodEntity::class, MealEntity::class,
    MealFoodCrossRef::class, ConsumptionEntryEntity::class, SeedStateEntity::class], version = 2, exportSchema = true)
abstract class LucijaDatabase : RoomDatabase() {
    abstract fun dao(): LucijaDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN weeklyTarget INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE categories ADD COLUMN countingMode TEXT NOT NULL DEFAULT 'ENTRIES'")
                // Preserve the total of previous active food targets until a new plan is applied.
                db.execSQL("UPDATE categories SET weeklyTarget = MIN(2147483647, COALESCE((SELECT SUM(weeklyTarget) FROM foods WHERE categoryId = categories.id AND active = 1), 0))")
                db.execSQL("UPDATE foods SET weeklyTarget = 0")
            }
        }
    }
}
