package hr.zdravalucija.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import hr.zdravalucija.data.*

@Composable
fun EditScreen(type: String, id: Long, state: LucijaState, vm: LucijaViewModel, back: () -> Unit) {
    val food = state.snapshot.foods.find { it.id == id }
    val category = state.snapshot.categories.find { it.id == id }
    val meal = state.snapshot.meals.find { it.id == id }
    val originalName = when (type) { "foods" -> food?.name; "categories" -> category?.name; else -> meal?.name }.orEmpty()
    var name by rememberSaveable(type, id) { mutableStateOf(originalName) }
    var active by rememberSaveable(type, id) { mutableStateOf(when (type) { "foods" -> food?.active; "categories" -> category?.active; else -> meal?.active } ?: true) }
    var target by rememberSaveable(type, id) { mutableStateOf((category?.weeklyTarget ?: 0).toString()) }
    var countDays by rememberSaveable(type, id) { mutableStateOf(category?.countingMode == "DAYS") }
    var categoryId by rememberSaveable(type, id) { mutableStateOf(food?.categoryId ?: state.snapshot.categories.firstOrNull { it.active }?.id ?: 0) }
    var selectedFoodIds by rememberSaveable(type, id) { mutableStateOf(state.snapshot.mealFoods.filter { it.mealId == id }.map { it.foodId }) }
    var showCategories by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val changingImages by vm.changingImages.collectAsState()
    val changingImage = "$type/$id" in changingImages
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.uploadImage(type, id, uri)
    }
    val targetValue = target.toIntOrNull()
    val valid = name.isNotBlank() && when (type) {
        "foods" -> categoryId > 0
        "categories" -> targetValue != null && targetValue >= 0 && (!countDays || targetValue <= 7)
        "meals" -> selectedFoodIds.isNotEmpty()
        else -> true
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Natrag") }
            Spacer(Modifier.weight(1f))
            TextButton(enabled = valid && !saving && !changingImage, onClick = {
                saving = true
                val done = { saving = false; back() }
                when (type) {
                    "foods" -> vm.saveFood(FoodEntity(id, name, categoryId, 0,
                        imageUrl = food?.imageUrl.takeIf { name == originalName || isCustomImage(it) }, imageQuery = food?.imageQuery.takeIf { name == originalName },
                        active = active, sortOrder = food?.sortOrder ?: state.snapshot.foods.size), done, onFailure = { saving = false })
                    "categories" -> vm.saveCategory(CategoryEntity(id, name,
                        imageUrl = category?.imageUrl.takeIf { name == originalName || isCustomImage(it) }, imageQuery = category?.imageQuery.takeIf { name == originalName },
                        active = active, sortOrder = category?.sortOrder ?: state.snapshot.categories.size,
                        weeklyTarget = targetValue!!, countingMode = if (countDays) "DAYS" else "ENTRIES"), done, onFailure = { saving = false })
                    else -> vm.saveMeal(MealEntity(id, name,
                        imageUrl = meal?.imageUrl.takeIf { name == originalName || isCustomImage(it) }, imageQuery = meal?.imageQuery.takeIf { name == originalName },
                        active = active, sortOrder = meal?.sortOrder ?: state.snapshot.meals.size), selectedFoodIds.toSet(), done, onFailure = { saving = false })
                }
            }) { Text("SPREMI") }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ScreenHeading(if (id == 0L) "Dodaj ${when(type) { "foods" -> "namirnicu"; "categories" -> "kategoriju"; else -> "jelo" }}" else "Uredi")
            if (id != 0L) {
                val url = when (type) { "foods" -> food?.imageUrl; "categories" -> category?.imageUrl; else -> meal?.imageUrl }
                val query = when (type) { "foods" -> food?.imageQuery; "categories" -> category?.imageQuery; else -> meal?.imageQuery }
                FoodVisual(url, query, originalName, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)))
                OutlinedButton(onClick = { vm.replaceImage(type, id) }, enabled = !changingImage && !saving && name == originalName,
                    modifier = Modifier.fillMaxWidth()) {
                    if (changingImage) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (changingImage) "Dohvaćam sliku…" else "Dohvati drugu sliku")
                }
                if (name != originalName) Text("Spremi novi naziv prije promjene slike.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !changingImage && !saving && name == originalName, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.PhotoLibrary, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Odaberi svoju sliku")
                }
            }
            OutlinedTextField(name, onValueChange = { name = it }, label = { Text("Naziv") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (type == "foods") {
                Box {
                    OutlinedButton(onClick = { showCategories = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Kategorija: ${state.snapshot.categories.find { it.id == categoryId }?.name ?: "Odaberi"}")
                    }
                    DropdownMenu(showCategories, onDismissRequest = { showCategories = false }) {
                        state.snapshot.categories.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { categoryId = item.id; showCategories = false }) }
                    }
                }
            }
            if (type == "categories") {
                OutlinedTextField(target, onValueChange = { if (it.all(Char::isDigit) && it.length <= 9) target = it },
                    label = { Text("Koliko puta u 7 dana?") }, supportingText = { Text("0 = bez obveznog cilja · 7 = svaki dan") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Broji različite dane", Modifier.weight(1f))
                    Switch(countDays, onCheckedChange = { countDays = it })
                }
                Text(if (countDays) "Više unosa istoga dana vrijedi kao jedan dan. Cilj može biti od 0 do 7." else "Svaka namirnica vrijedi 1 unos, uključujući sastojke jela. Cilj 14 znači 2 unosa dnevno.", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Aktivno", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(active, onCheckedChange = { active = it }, modifier = Modifier.semantics { contentDescription = "Aktivno" })
            }
            if (type == "meals") {
                SectionTitle("Namirnice u jelu")
                if (state.snapshot.foods.isEmpty()) Text("Najprije dodaj namirnice u Postavkama.")
                state.snapshot.foods.forEach { item ->
                    val checked = item.id in selectedFoodIds
                    Row(Modifier.fillMaxWidth().clickable {
                        selectedFoodIds = if (checked) selectedFoodIds - item.id else selectedFoodIds + item.id
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, onCheckedChange = { selectedFoodIds = if (it) selectedFoodIds + item.id else selectedFoodIds - item.id })
                        Text(item.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
