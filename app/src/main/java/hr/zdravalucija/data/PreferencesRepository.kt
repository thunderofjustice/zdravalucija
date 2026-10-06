package hr.zdravalucija.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.time.DayOfWeek
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("lucija_preferences")
enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class AppPreferences(val firstDayOfWeek: Int = DayOfWeek.MONDAY.value, val theme: ThemeMode = ThemeMode.SYSTEM)

class PreferencesRepository(private val context: Context) {
    private val firstDay = intPreferencesKey("first_day_of_week")
    private val theme = stringPreferencesKey("theme")
    val preferences = context.preferences.data.map { prefs ->
        AppPreferences(prefs[firstDay]?.takeIf { it in 1..7 } ?: 1,
            ThemeMode.entries.firstOrNull { it.name == prefs[theme] } ?: ThemeMode.SYSTEM)
    }
    suspend fun save(value: AppPreferences) {
        require(value.firstDayOfWeek in 1..7)
        context.preferences.edit { it[firstDay] = value.firstDayOfWeek; it[theme] = value.theme.name }
    }
}
