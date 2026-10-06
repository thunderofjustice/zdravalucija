package hr.zdravalucija.data

import org.junit.Assert.assertThrows
import org.junit.Test

class BackupValidationTest {
    private val codec = BackupCodec()
    private val source = BackupData(categories=listOf(CategoryEntity(1,"Voće")), foods=listOf(FoodEntity(1,"Jabuka",1,7)),
        meals=emptyList(), mealFoods=emptyList(), entries=listOf(ConsumptionEntryEntity(1,1,1,"2026-10-05",null,"a")),
        preferences=AppPreferences())
    @Test fun nullDateCannotBeRestored() {
        val json = codec.encode(source).replace("\"localDate\": \"2026-10-05\"", "\"localDate\": null")
        assertThrows(IllegalArgumentException::class.java) { codec.decode(json) }
    }
    @Test fun integerOverflowCannotBeRestoredAsZeroTarget() {
        val json = codec.encode(source).replace("\"weeklyTarget\": 7", "\"weeklyTarget\": 4294967296")
        assertThrows(IllegalArgumentException::class.java) { codec.decode(json) }
    }
}
