package hr.zdravalucija.domain

import hr.zdravalucija.data.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class WeekCycle(val start: LocalDate, val end: LocalDate) {
    companion object {
        fun containing(date: LocalDate, firstDay: DayOfWeek = DayOfWeek.MONDAY): WeekCycle {
            val start = date.with(TemporalAdjusters.previousOrSame(firstDay))
            return WeekCycle(start, start.plusDays(6))
        }
    }
}
data class WeeklyProgress(val category: CategoryEntity, val weeklyConsumed: Int) {
    val weeklyTarget: Int get() = category.weeklyTarget
    val remaining: Int get() = (weeklyTarget - weeklyConsumed).coerceAtLeast(0)
}

data class TodayRemaining(val category: CategoryEntity, val remaining: Int)

/** Daily checklist is derived solely from weeklyTarget: 7 -> 1, 14 -> 2, etc. */
fun dailyChecklistCount(category: CategoryEntity): Int =
    if (category.weeklyTarget > 0 && category.weeklyTarget % 7 == 0) {
        if (category.countingMode == "DAYS") 1 else category.weeklyTarget / 7
    } else 0

private fun count(category: CategoryEntity, rows: List<ConsumptionEntryEntity>): Int = when (category.countingMode) {
    "DAYS" -> rows.map { it.localDate }.distinct().size
    else -> rows.size
}

class GetTodayRemainingCategoriesUseCase {
    operator fun invoke(categories: List<CategoryEntity>, foods: List<FoodEntity>, entries: List<ConsumptionEntryEntity>, date: LocalDate): List<TodayRemaining> {
        val foodCategories = foods.associate { it.id to it.categoryId }
        val rows = entries.filter { it.localDate == date.toString() }.groupBy { foodCategories[it.foodId] }
        return categories.filter { it.active && dailyChecklistCount(it) > 0 }.mapNotNull { category ->
            val remaining = (dailyChecklistCount(category) - count(category, rows[category.id].orEmpty())).coerceAtLeast(0)
            if (remaining > 0) TodayRemaining(category, remaining) else null
        }
    }
}
class GetWeeklyProgressUseCase {
    operator fun invoke(categories: List<CategoryEntity>, foods: List<FoodEntity>, entries: List<ConsumptionEntryEntity>, cycle: WeekCycle): List<WeeklyProgress> {
        val start = cycle.start.toString()
        val end = cycle.end.toString()
        val foodCategories = foods.associate { it.id to it.categoryId }
        val rows = entries.filter { it.localDate in start..end }.groupBy { foodCategories[it.foodId] }
        return categories.filter { it.active && it.weeklyTarget > 0 }.map { WeeklyProgress(it, count(it, rows[it.id].orEmpty())) }
    }
}
class GetWeeklyRemainingCategoriesUseCase {
    operator fun invoke(progress: List<WeeklyProgress>) = progress.filter { it.remaining > 0 }
}
class AddFoodConsumptionUseCase(private val repository: FoodRepository) {
    suspend operator fun invoke(foodId: Long) = repository.addFoodConsumption(foodId)
}
class AddMealConsumptionUseCase(private val repository: FoodRepository) {
    suspend operator fun invoke(mealId: Long) = repository.addMealConsumption(mealId)
}
class DeleteConsumptionUseCase(private val repository: FoodRepository) {
    suspend operator fun invoke(entryId: Long) = repository.deleteEntry(entryId)
}
class DeleteEntryGroupUseCase(private val repository: FoodRepository) {
    suspend operator fun invoke(entryGroupId: String) = repository.deleteEntryGroup(entryGroupId)
}
