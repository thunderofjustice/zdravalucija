package hr.zdravalucija.data

import androidx.room.*

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val imageUrl: String? = null,
    val imageQuery: String? = null,
    val active: Boolean = true,
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0") val weeklyTarget: Int = 0,
    /** ENTRIES: every food row, including meal ingredients; DAYS: distinct local dates. */
    @ColumnInfo(defaultValue = "'ENTRIES'") val countingMode: String = "ENTRIES",
)

@Entity(tableName = "foods", foreignKeys = [ForeignKey(
    entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"],
    onDelete = ForeignKey.RESTRICT,
)], indices = [Index("categoryId")])
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long,
    /** Legacy v1 column retained for backup compatibility; no longer used for goals. */
    val weeklyTarget: Int = 0,
    val imageUrl: String? = null,
    val imageQuery: String? = null,
    val active: Boolean = true,
    val sortOrder: Int = 0,
)

@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val imageUrl: String? = null,
    val imageQuery: String? = null,
    val active: Boolean = true,
    val sortOrder: Int = 0,
)

@Entity(tableName = "meal_foods", primaryKeys = ["mealId", "foodId"], foreignKeys = [
    ForeignKey(entity = MealEntity::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("foodId")])
data class MealFoodCrossRef(val mealId: Long, val foodId: Long)

@Entity(tableName = "consumption_entries", foreignKeys = [
    ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = MealEntity::class, parentColumns = ["id"], childColumns = ["sourceMealId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("foodId"), Index("sourceMealId"), Index("localDate"), Index("entryGroupId")])
data class ConsumptionEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: Long,
    val consumedAt: Long,
    /** ISO yyyy-MM-dd, captured in the device's timezone at entry time. */
    val localDate: String,
    val sourceMealId: Long? = null,
    val entryGroupId: String,
)

/** id 1 = initialized; id 2 = complete default catalog applied. Empty restores set both. */
@Entity(tableName = "seed_state")
data class SeedStateEntity(@PrimaryKey val id: Int = 1)
