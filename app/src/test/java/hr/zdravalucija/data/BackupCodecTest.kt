package hr.zdravalucija.data

import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val codec = BackupCodec()
    private fun backup() = BackupData(categories = listOf(CategoryEntity(1,"Voće")),
        foods = listOf(FoodEntity(1,"Naranča",1,7)), meals = listOf(MealEntity(1,"Sok")),
        mealFoods = listOf(MealFoodCrossRef(1,1)),
        entries = listOf(ConsumptionEntryEntity(1,1,1,"2026-10-05",1,"meal-group")),
        preferences = AppPreferences(3,ThemeMode.DARK))
    @Test fun `round trip preserves goals sources groups dates and preferences`() {
        assertEquals(backup(), codec.decode(codec.encode(backup())))
    }
    @Test fun `v1 backup upgrades food goals without losing history`() {
        val root = com.google.gson.JsonParser.parseString(codec.encode(backup())).asJsonObject
        root.addProperty("formatVersion",1)
        root.getAsJsonArray("categories").forEach { it.asJsonObject.remove("weeklyTarget"); it.asJsonObject.remove("countingMode") }
        val restored = codec.decode(root.toString())
        assertEquals(2,restored.formatVersion)
        assertEquals(7,restored.categories.single().weeklyTarget)
        assertEquals("ENTRIES",restored.categories.single().countingMode)
        assertEquals(0,restored.foods.single().weeklyTarget)
        assertEquals(backup().entries,restored.entries)
    }
    @Test fun `missing food reference is rejected before restore`() {
        assertThrows(IllegalArgumentException::class.java) { codec.validate(backup().copy(mealFoods = listOf(MealFoodCrossRef(1,999)))) }
    }
    @Test fun `decimal targets are rejected rather than silently rounded`() {
        val json = codec.encode(backup()).replace("\"weeklyTarget\": 7", "\"weeklyTarget\": 2.5")
        assertThrows(IllegalArgumentException::class.java) { codec.decode(json) }
    }
    @Test fun `mixed source group cannot delete an unrelated direct entry`() {
        val rows = backup().entries + ConsumptionEntryEntity(2,1,1,"2026-10-05",null,"meal-group")
        assertThrows(IllegalArgumentException::class.java) { codec.validate(backup().copy(entries = rows)) }
    }
    @Test fun `empty backup is valid and does not require seed data`() {
        val empty = BackupData(categories = emptyList(), foods = emptyList(), meals = emptyList(), mealFoods = emptyList(), entries = emptyList(), preferences = AppPreferences())
        assertEquals(empty, codec.decode(codec.encode(empty)))
    }
    @Test fun `invalid local date and unknown format are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { codec.validate(backup().copy(entries = listOf(backup().entries.first().copy(localDate = "2026-02-30")))) }
        assertThrows(IllegalArgumentException::class.java) { codec.validate(backup().copy(formatVersion = 3)) }
    }
    @Test fun `older backups without custom images stay compatible`() {
        val root = com.google.gson.JsonParser.parseString(codec.encode(backup())).asJsonObject
        root.remove("customImages")
        assertEquals(backup(), codec.decode(root.toString()))
    }
    @Test fun `custom photo bytes travel with the backup`() {
        val bytes = byteArrayOf(1,2,3)
        val url = customImageUrl(bytes)
        val custom = backup().copy(foods = listOf(backup().foods.single().copy(imageUrl = url)),
            customImages = mapOf(url to java.util.Base64.getEncoder().encodeToString(bytes)))
        assertEquals(custom, codec.decode(codec.encode(custom)))
    }
    @Test fun `missing custom photo bytes and mismatched content are rejected`() {
        val url = customImageUrl(byteArrayOf(1,2,3))
        val custom = backup().copy(foods = listOf(backup().foods.single().copy(imageUrl = url)))
        assertThrows(IllegalArgumentException::class.java) { codec.validate(custom) }
        assertThrows(IllegalArgumentException::class.java) {
            codec.validate(custom.copy(customImages = mapOf(url to java.util.Base64.getEncoder().encodeToString(byteArrayOf(4,5,6)))))
        }
    }
    @Test fun `custom paths cannot point outside app owned photo storage`() {
        val custom = backup().copy(foods = listOf(backup().foods.single().copy(imageUrl = "local-image://../private.jpg")))
        assertThrows(IllegalArgumentException::class.java) { codec.validate(custom) }
    }
}
