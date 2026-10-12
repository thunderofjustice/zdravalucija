package hr.zdravalucija.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.net.Uri
import hr.zdravalucija.AppContainer
import hr.zdravalucija.data.*
import hr.zdravalucija.domain.*
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class LucijaState(
    val loading: Boolean = true,
    val error: String? = null,
    val snapshot: FoodSnapshot = FoodSnapshot(),
    val preferences: AppPreferences = AppPreferences(),
    val today: LocalDate = LocalDate.now(),
    val remainingToday: List<TodayRemaining> = emptyList(),
    val weeklyProgress: List<WeeklyProgress> = emptyList(),
    val cycle: WeekCycle = WeekCycle.containing(LocalDate.now()),
) {
    val todayEntries get() = snapshot.entries.filter { it.localDate == today.toString() }
    val remainingWeek get() = GetWeeklyRemainingCategoriesUseCase()(weeklyProgress)
}
data class UiMessage(val text: String, val undoGroupId: String? = null, val added: Boolean = false)

class LucijaViewModel(val container: AppContainer) : ViewModel() {
    private val date = MutableStateFlow(LocalDate.now())
    private val initError = MutableStateFlow<String?>(null)
    private val messages = Channel<UiMessage>(Channel.UNLIMITED)
    val events = messages.receiveAsFlow()
    val pendingImport = MutableStateFlow<BackupData?>(null)
    val pendingPlan = MutableStateFlow<DietPlan?>(null)
    val changingImages = MutableStateFlow<Set<String>>(emptySet())
    fun uploadImage(type: String, id: Long, uri: Uri) {
        val key = "$type/$id"
        if (key in changingImages.value) return
        changingImages.value += key
        viewModelScope.launch {
            try {
                UploadImageUseCase(container.database.dao(), container.images, container.appContext)(type, id, uri)
                messages.send(UiMessage("Tvoja fotografija spremljena"))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                messages.send(UiMessage("Fotografija se nije mogla spremiti. Odaberi drugu sliku."))
            } finally { changingImages.value -= key }
        }
    }
    fun replaceImage(type: String, id: Long) {
        val key = "$type/$id"
        if (key in changingImages.value) return
        changingImages.value += key
        viewModelScope.launch {
            try {
                ReplaceImageUseCase(container.database.dao(), container.imageSearch, container.images, container.nameTranslator)(type, id)
                messages.send(UiMessage("Nova slika spremljena"))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                messages.send(UiMessage(if (error is java.io.IOException) "Slika se nije mogla dohvatiti. Provjeri internet i pokušaj ponovno." else error.message ?: "Pokušaj ponovno."))
            } finally { changingImages.value -= key }
        }
    }
    val state = combine(container.repository.observeSnapshot().map<FoodSnapshot, FoodSnapshot?> { it }.onStart { emit(null) },
        container.preferences.preferences, date, initError) { data, prefs, today, error ->
        val cycle = WeekCycle.containing(today, DayOfWeek.of(prefs.firstDayOfWeek))
        if (data == null) LucijaState(loading = error == null, error = error, preferences = prefs, today = today, cycle = cycle)
        else LucijaState(false, snapshot = data, preferences = prefs, today = today,
            remainingToday = GetTodayRemainingCategoriesUseCase()(data.categories, data.foods, data.entries, today),
            weeklyProgress = GetWeeklyProgressUseCase()(data.categories, data.foods, data.entries, cycle), cycle = cycle)
    }.catch { emit(LucijaState(loading = false, error = "Podaci se nisu mogli učitati.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LucijaState())

    init {
        viewModelScope.launch {
            try { container.repository.initialize() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                initError.value = "Baza se nije mogla otvoriti. Ponovno pokreni aplikaciju."
                messages.send(UiMessage(initError.value!!))
            }
        }
        viewModelScope.launch { while (isActive) { refreshDate(); delay(15_000) } }
    }
    fun refreshDate() { date.value = LocalDate.now() }
    private fun action(onFailure: () -> Unit = {}, block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (error: Exception) {
            if (error is CancellationException) throw error
            onFailure()
            messages.send(UiMessage(error.message ?: "Pokušaj ponovno."))
        }
    }
    fun addFood(food: FoodEntity, onDate: LocalDate? = null) = action {
        refreshDate()
        val group = AddFoodConsumptionUseCase(container.repository)(food.id, onDate)
        messages.send(UiMessage("Dodano: ${food.name}", group, true))
    }
    fun addMeal(meal: MealEntity, onDate: LocalDate? = null) = action {
        refreshDate()
        val group = AddMealConsumptionUseCase(container.repository)(meal.id, onDate)
        messages.send(UiMessage("Dodano: ${meal.name}", group, true))
    }
    fun undo(group: String) = action { DeleteEntryGroupUseCase(container.repository)(group) }
    fun deleteEntry(id: Long) = action {
        DeleteConsumptionUseCase(container.repository)(id)
        messages.send(UiMessage("Unos obrisan"))
    }
    fun deleteGroup(group: String) = action {
        DeleteEntryGroupUseCase(container.repository)(group)
        messages.send(UiMessage("Jelo obrisano"))
    }
    fun saveFood(food: FoodEntity, onSaved: () -> Unit, onFailure: () -> Unit = {}) = action(onFailure) { container.repository.updateFood(food); onSaved() }
    fun saveCategory(category: CategoryEntity, onSaved: () -> Unit, onFailure: () -> Unit = {}) = action(onFailure) { container.repository.updateCategory(category); onSaved() }
    fun saveMeal(meal: MealEntity, foodIds: Set<Long>, onSaved: () -> Unit, onFailure: () -> Unit = {}) = action(onFailure) { container.repository.updateMeal(meal, foodIds); onSaved() }
    fun savePreferences(value: AppPreferences) = action { container.preferences.save(value) }
    fun exportBackup(uri: Uri, context: android.content.Context) = action {
        withContext(Dispatchers.IO) {
            val json = container.backup.export()
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(json) }
        }
        messages.send(UiMessage("Sigurnosna kopija spremljena"))
    }
    fun prepareImport(uri: Uri, context: android.content.Context) = action {
        val json = withContext(Dispatchers.IO) {
            val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 100 * 1024 * 1024) { "Kopija je prevelika." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            bytes.toString(Charsets.UTF_8)
        }
        withContext(Dispatchers.IO) {
            val root = com.google.gson.JsonParser.parseString(json).asJsonObject
            if (root.get("kind")?.asString == "dietPlan") pendingPlan.value = container.plan.codec.decode(json)
            else pendingImport.value = container.backup.codec.decode(json)
        }
    }
    fun cancelImport() { pendingImport.value = null }
    fun preparePlan() = action { pendingPlan.value = withContext(Dispatchers.IO) { container.plan.readBundled() } }
    fun cancelPlan() { pendingPlan.value = null }
    fun confirmPlan() = action {
        val plan = pendingPlan.value ?: return@action
        pendingPlan.value = null
        withContext(Dispatchers.IO) { container.plan.apply(plan) }
        ImageWorker.enqueue(container.appContext)
        messages.send(UiMessage("Prehrambeni popis uvezen"))
    }
    fun confirmImport() = action {
        val backup = pendingImport.value ?: return@action
        pendingImport.value = null
        withContext(Dispatchers.IO) { container.backup.restore(backup) }
        ImageWorker.enqueue(container.appContext)
        messages.send(UiMessage("Podaci su vraćeni"))
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LucijaViewModel(container) as T
    }
}
