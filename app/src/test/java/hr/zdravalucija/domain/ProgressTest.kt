package hr.zdravalucija.domain

import hr.zdravalucija.data.*
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ProgressTest {
    private val date = LocalDate.of(2026, 10, 5)
    private val foods = listOf(FoodEntity(1,"Jabuka",1), FoodEntity(2,"Banana",1), FoodEntity(3,"Brokula",2), FoodEntity(4,"Mrkva",2))
    private fun category(id: Long, target: Int, mode: String = "ENTRIES", active: Boolean = true) =
        CategoryEntity(id, "Category $id", weeklyTarget = target, countingMode = mode, active = active)
    private fun entry(food: Long, day: LocalDate = date, group: String = "g") =
        ConsumptionEntryEntity(foodId = food, consumedAt = 0, localDate = day.toString(), entryGroupId = group)
    private fun progress(categories: List<CategoryEntity>, rows: List<ConsumptionEntryEntity>) =
        GetWeeklyProgressUseCase()(categories, foods, rows, WeekCycle.containing(date))

    @Test fun mixedFoodsShareCategoryGoalAndRepeatedEntriesCount() {
        val result = progress(listOf(category(1,14)), listOf(entry(1),entry(2),entry(1))).single()
        assertEquals(3,result.weeklyConsumed)
        assertEquals(11,result.remaining)
    }
    @Test fun mealIngredientsCountIndividuallyWithinOneCategory() {
        val result = progress(listOf(category(2,7)), listOf(entry(3),entry(4))).single()
        assertEquals(2,result.weeklyConsumed)
    }
    @Test fun fruitTargetFourteenRequiresTwoEntriesToday() {
        val categories = listOf(category(1,14),category(2,2))
        val useCase = GetTodayRemainingCategoriesUseCase()
        assertEquals(2,useCase(categories,foods,emptyList(),date).single().remaining)
        assertEquals(1,useCase(categories,foods,listOf(entry(1)),date).single().remaining)
        assertTrue(useCase(categories,foods,listOf(entry(1),entry(2)),date).isEmpty())
    }
    @Test fun distinctDayGoalDoesNotIncreaseForRepeatedSameDayEntries() {
        val result = progress(listOf(category(1,4,"DAYS")), listOf(entry(1),entry(2),entry(1,date.plusDays(1)))).single()
        assertEquals(2,result.weeklyConsumed)
        assertEquals(2,result.remaining)
        assertTrue(GetTodayRemainingCategoriesUseCase()(listOf(category(1,4,"DAYS")),foods,emptyList(),date).isEmpty())
    }
    @Test fun dailyChecklistUsesTodayEvenWhenWeeklyCountAlreadyReached() {
        val rows = List(14) { entry(1,date.minusDays(1)) }
        assertEquals(2,GetTodayRemainingCategoriesUseCase()(listOf(category(1,14)),foods,rows,date).single().remaining)
    }
    @Test fun zeroAndInactiveGoalsExcludedAndRemainingNeverNegative() {
        val result = progress(listOf(category(1,1),category(2,0),category(3,7,active=false)), listOf(entry(1),entry(2)))
        assertEquals(1,result.size)
        assertEquals(0,result.single().remaining)
        assertTrue(GetWeeklyRemainingCategoriesUseCase()(result).isEmpty())
    }
    @Test fun mondayCycleIncludesBothBoundariesAndYearTransition() {
        val cycle = WeekCycle.containing(LocalDate.of(2027,1,1))
        assertEquals(LocalDate.of(2026,12,28),cycle.start)
        assertEquals(LocalDate.of(2027,1,3),cycle.end)
        val rows = listOf(entry(1,cycle.start),entry(1,cycle.end),entry(1,cycle.end.plusDays(1)))
        assertEquals(2,GetWeeklyProgressUseCase()(listOf(category(1,7)),foods,rows,cycle).single().weeklyConsumed)
    }
    @Test fun customFirstDayRecomputesCycle() {
        val cycle = WeekCycle.containing(date,DayOfWeek.WEDNESDAY)
        assertEquals(LocalDate.of(2026,9,30),cycle.start)
        assertEquals(LocalDate.of(2026,10,6),cycle.end)
    }
    @Test fun historicalDailyOverviewShowsMetAndUnmetGoalsWithoutCountingOtherDates() {
        val past = date.minusDays(2)
        val result = GetDailyProgressUseCase()(listOf(category(1,14),category(2,7),category(3,2)), foods,
            listOf(entry(1,past),entry(2,past),entry(3,date)), past)
        assertEquals(2,result.size)
        assertEquals(2,result[0].consumed)
        assertEquals(0,result[0].remaining)
        assertEquals(0,result[1].consumed)
        assertEquals(1,result[1].remaining)
    }
    @Test fun historicalDayCountingUsesOneDayAndWeekRespectsCustomBoundary() {
        val past = date.minusWeeks(1)
        val categories = listOf(category(1,7,"DAYS"))
        val rows = listOf(entry(1,past),entry(2,past),entry(1,past.minusDays(1)),entry(1,date))
        assertEquals(1,GetDailyProgressUseCase()(categories,foods,rows,past).single().consumed)
        assertEquals(2,GetWeeklyProgressUseCase()(categories,foods,rows,WeekCycle.containing(past,DayOfWeek.SUNDAY)).single().weeklyConsumed)
    }
}
