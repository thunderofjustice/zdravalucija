package hr.zdravalucija.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DietPlanCodecTest {
    private val json get() = File("src/main/assets/diet-plan.json").readText()
    private val codec = DietPlanCodec()
    @Test fun suppliedPlanHasTwelveCategoriesExactTargetsAndSeparateStarches() {
        val plan = codec.decode(json)
        assertEquals(12, plan.categories.size)
        assertEquals(listOf(7,14,2,4,2,2,1,7,4,2,7,2),plan.categories.map { it.weeklyTarget })
        assertEquals("DAYS",plan.categories.first { it.name == "Orašasti plodovi" }.countingMode)
        assertEquals("DAYS",plan.categories.first { it.name == "Žitarice" }.countingMode)
        assertEquals(11,plan.categories.first { it.name == "Žitarice" }.foods.size)
        assertEquals(84,plan.categories.sumOf { it.foods.size })
        assertEquals(listOf("Piletina","Puretina"),plan.categories.first { it.name == "Piletina / puretina" }.foods.map { it.name })
        assertEquals(listOf("Govedina","Junetina","Teletina","Svinjetina","Janjetina"),plan.categories.first { it.name == "Crveno meso" }.foods.map { it.name })
        val seedFoods = com.google.gson.Gson().fromJson(File("src/main/assets/seed/foods.json").readText(), Array<FoodEntity>::class.java)
        val seedCategories = com.google.gson.Gson().fromJson(File("src/main/assets/seed/categories.json").readText(), Array<CategoryEntity>::class.java)
        assertEquals(plan.categories.flatMap { it.foods }.map { it.name },seedFoods.sortedBy { it.sortOrder }.map { it.name })
        assertEquals(13,seedCategories.size) // Twelve diet categories plus the single Meals shortcut.
        assertTrue(seedFoods.all { !it.imageQuery.isNullOrBlank() && it.categoryId in seedCategories.map { it.id } })
        assertEquals(listOf("Krumpir","Batat"),plan.categories.last().foods.map { it.name })
        assertEquals(plan,codec.decode(File("../imports/lucijin-prehrambeni-popis.json").readText()))
    }
    @Test fun decimalAndUnreachableDayTargetsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { codec.decode(json.replaceFirst("\"weeklyTarget\": 7","\"weeklyTarget\": 1.5")) }
        assertThrows(IllegalArgumentException::class.java) { codec.decode(json.replace("\"weeklyTarget\": 4,\n      \"countingMode\": \"DAYS\"","\"weeklyTarget\": 8,\n      \"countingMode\": \"DAYS\"")) }
    }
    @Test fun duplicateFoodsAndUnknownCountingModesAreRejected() {
        val plan = codec.decode(json)
        assertThrows(IllegalArgumentException::class.java) { codec.validate(plan.copy(categories = plan.categories + plan.categories.first())) }
        assertThrows(IllegalArgumentException::class.java) { codec.validate(plan.copy(categories = listOf(plan.categories.first().copy(countingMode="OTHER")))) }
    }
}
