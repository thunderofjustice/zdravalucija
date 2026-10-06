package hr.zdravalucija.data

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.Locale

data class PlanFood(val name: String, val imageQuery: String, val aliases: List<String>? = null)
data class PlanCategory(val name: String, val weeklyTarget: Int, val countingMode: String,
    val imageQuery: String, val foods: List<PlanFood>)
data class DietPlan(val kind: String, val formatVersion: Int, val categories: List<PlanCategory>)

private fun nameKey(name: String) = name.trim().lowercase(Locale.ROOT).replace(Regex("\\s*/\\s*"), "/")

class DietPlanCodec {
    fun decode(json: String): DietPlan {
        try {
            val root = JsonParser.parseString(json).asJsonObject
            require(root.get("kind").asString == "dietPlan" && root.get("formatVersion").asString == "1")
            root.getAsJsonArray("categories").forEach { row ->
                val target = row.asJsonObject.get("weeklyTarget")
                require(target.isJsonPrimitive && target.asJsonPrimitive.isNumber)
                require(target.asString.toIntOrNull()?.let { it >= 0 } == true)
            }
            return Gson().fromJson(root, DietPlan::class.java).also(::validate)
        } catch (error: Exception) {
            throw IllegalArgumentException("Prehrambeni popis nije valjan.", error)
        }
    }
    fun validate(plan: DietPlan) {
        require(plan.kind == "dietPlan" && plan.formatVersion == 1 && plan.categories.isNotEmpty())
        require(plan.categories.map { nameKey(it.name) }.distinct().size == plan.categories.size)
        val foods = plan.categories.flatMap { it.foods }
        require(foods.map { nameKey(it.name) }.distinct().size == foods.size)
        val allNames = foods.flatMap { listOf(it.name) + it.aliases.orEmpty() }.map(::nameKey)
        require(allNames.all { it.isNotBlank() } && allNames.distinct().size == allNames.size)
        plan.categories.forEach {
            require(it.name.isNotBlank() && it.imageQuery.isNotBlank() && it.weeklyTarget >= 0)
            require(it.countingMode in listOf("ENTRIES", "DAYS"))
            require(it.countingMode != "DAYS" || it.weeklyTarget <= 7)
            require(it.foods.isNotEmpty())
            it.foods.forEach { food -> require(food.name.isNotBlank() && food.imageQuery.isNotBlank()) }
        }
    }
}

/** Additive, repeatable catalog import. No history, meal or preference deletion. */
class DietPlanRepository(private val db: LucijaDatabase, private val context: Context) {
    val codec = DietPlanCodec()
    fun readBundled(): DietPlan = context.assets.open("diet-plan.json").bufferedReader().use { codec.decode(it.readText()) }
    suspend fun apply(plan: DietPlan, preserveExistingChoices: Boolean = false) {
        codec.validate(plan)
        db.withTransaction {
            val dao = db.dao()
            val categories = dao.categories().associateBy { nameKey(it.name) }.toMutableMap()
            val foods = dao.foods().associateBy { nameKey(it.name) }.toMutableMap()
            var foodOrder = 0
            plan.categories.forEachIndexed { categoryOrder, imported ->
                val oldCategory = categories[nameKey(imported.name)]
                val category = (oldCategory ?: CategoryEntity(name = imported.name)).copy(
                    name = imported.name, sortOrder = categoryOrder,
                    active = if (preserveExistingChoices && oldCategory != null) oldCategory.active else true,
                    weeklyTarget = if (preserveExistingChoices && oldCategory != null) oldCategory.weeklyTarget else imported.weeklyTarget,
                    countingMode = if (preserveExistingChoices && oldCategory != null) oldCategory.countingMode else imported.countingMode,
                    imageQuery = oldCategory?.imageQuery ?: imported.imageQuery)
                val inserted = dao.saveCategory(category)
                val categoryId = if (category.id == 0L) inserted else category.id
                categories[nameKey(category.name)] = category.copy(id = categoryId)
                imported.foods.forEach { importedFood ->
                    val oldFood = foods[nameKey(importedFood.name)] ?: importedFood.aliases.orEmpty().firstNotNullOfOrNull { foods[nameKey(it)] }
                    val food = (oldFood ?: FoodEntity(name = importedFood.name, categoryId = categoryId)).copy(
                        name = importedFood.name, sortOrder = foodOrder++, categoryId = categoryId, weeklyTarget = 0,
                        active = if (preserveExistingChoices && oldFood != null) oldFood.active else true,
                        imageQuery = oldFood?.imageQuery ?: importedFood.imageQuery)
                    val foodId = dao.saveFood(food)
                    if (oldFood != null) foods.remove(nameKey(oldFood.name))
                    foods[nameKey(food.name)] = food.copy(id = if (food.id == 0L) foodId else food.id)
                }
            }
            // Retire the old catch-all Meat navigation only when all its foods were relocated.
            categories[nameKey("Meso")]?.let { old ->
                if (plan.categories.none { nameKey(it.name) == nameKey(old.name) } && dao.foods().none { it.categoryId == old.id }) {
                    dao.saveCategory(old.copy(active = false, weeklyTarget = 0))
                }
            }
            dao.markSeeded()
            dao.markSeeded(SeedStateEntity(2))
        }
    }
}
