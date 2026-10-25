@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package be.chatpate

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.concurrent.thread

@Composable
private fun FoodTypeChoice(types: List<FoodType>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.filter { !it.deleted || it.id == selected }.forEach { type ->
            FilterChip(
                selected = selected == type.id,
                onClick = { onSelect(type.id) },
                label = { Text("${type.emoji} ${type.name}") },
            )
        }
    }
}

/** Choix d'un ou plusieurs chats. */
@Composable
private fun CatPicker(cats: List<Cat>, selected: List<String>, onChange: (List<String>) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cats.forEach { cat ->
            val on = cat.id in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - cat.id else selected + cat.id) },
                label = { Text(cat.name) },
                leadingIcon = { CatAvatar(cat, 22.dp) },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Créer / modifier un programme
// ---------------------------------------------------------------------------------------------

@Composable
fun PlanDialog(
    cats: List<Cat>,
    products: List<FoodProduct>,
    foodPatterns: List<FoodPattern>,
    initial: Plan?,
    onSave: (Plan) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var timeMin by remember { mutableIntStateOf(initial?.timeMin ?: (8 * 60)) }
    var every by remember { mutableIntStateOf(initial?.everyDays ?: 1) }
    var patternId by remember {
        mutableStateOf(initial?.patternId?.takeIf { id -> foodPatterns.any { it.id == id && !it.deleted } }
            ?: if (initial?.alternatePate == true) {
                if (initial.pate == Pate.BOEUF.key) "boeuf-poulet" else "poulet-boeuf"
            } else "")
    }
    var productId by remember {
        mutableStateOf(
            initial?.productId?.takeIf { id -> products.any { it.id == id && !it.deleted } }
                ?: if (patternId.isEmpty()) {
                    val legacyFoodId = initial?.pate
                    products.firstOrNull { !it.deleted && it.id == legacyFoodId }?.id
                        ?: products.firstOrNull {
                            !it.deleted && it.pateKey == (legacyFoodId ?: Pate.POULET.key)
                        }?.id.orEmpty()
                } else "",
        )
    }
    var start by remember {
        mutableStateOf(
            initial?.start?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now(ZoneId.systemDefault())
        )
    }
    var pate by remember { mutableStateOf(initial?.pate ?: Pate.POULET.key) }
    var qty by remember { mutableStateOf(initial?.qty ?: "") }
    var catIds by remember {
        mutableStateOf(
            if (initial != null) {
                initial.catIds.filter { id -> cats.any { it.id == id } }.ifEmpty { cats.map { it.id } }
            } else cats.map { it.id }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouveau programme" else "Modifier le programme") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Heure du repas", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = {
                    TimePickerDialog(
                        ctx,
                        { _, h, m -> timeMin = h * 60 + m },
                        timeMin / 60, timeMin % 60, true
                    ).show()
                }) { Text(Fmt.timeMin(timeMin)) }

                Text("Fréquence", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { if (every > 1) every -= 1 }) { Text("−") }
                    Text(
                        Fmt.every(every),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    FilledTonalIconButton(onClick = { if (every < 30) every += 1 }) { Text("+") }
                }

                Text("Premier repas le", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = {
                    DatePickerDialog(
                        ctx,
                        { _, y, m, d -> start = LocalDate.of(y, m + 1, d) },
                        start.year, start.monthValue - 1, start.dayOfMonth
                    ).show()
                }) { Text(Fmt.dateLong(start)) }

                if (products.isNotEmpty()) {
                    Text("Nourriture seule", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        products.filterNot { it.deleted }.forEach { product ->
                            FilterChip(
                                selected = productId == product.id,
                                onClick = {
                                    productId = product.id
                                    pate = product.pateKey
                                    patternId = ""
                                },
                                label = {
                                    Text(
                                        "${pateEmoji(product.pateKey)} ${product.name}"
                                    )
                                },
                            )
                        }
                    }
                    Text("Combinaisons", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        foodPatterns.filterNot { it.deleted }.forEach { pattern ->
                            FilterChip(
                                selected = patternId == pattern.id,
                                onClick = {
                                    patternId = pattern.id
                                    productId = ""
                                    pate = pattern.foodIds.firstOrNull()?.let { foodId ->
                                        products.firstOrNull { it.id == foodId }?.pateKey
                                    }.orEmpty()
                                },
                                label = { Text(pattern.name) },
                            )
                        }
                    }
                    if (patternId.isNotEmpty()) {
                        val pattern = foodPatterns.firstOrNull { it.id == patternId }
                        val names = pattern?.foodIds.orEmpty().mapNotNull { id ->
                            products.firstOrNull { it.id == id && !it.deleted }?.name
                        }
                        Text(
                            "Ordre : " + names.joinToString(" → "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = { Text("Quantité (facultatif)") },
                    placeholder = { Text("ex : 1 sachet, 80 g") },
                    singleLine = true,
                )

                Text("Pour quel(s) chat(s) ?", fontWeight = FontWeight.Bold)
                CatPicker(cats, catIds) { catIds = it }

                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                    ) { Text("Supprimer ce programme") }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = catIds.isNotEmpty() || cats.isEmpty(),
                onClick = {
                    onSave(
                        Plan(
                            id = initial?.id ?: UUID.randomUUID().toString().take(8),
                            timeMin = timeMin,
                            everyDays = every,
                            start = start.toString(),
                            pate = pate,
                            qty = qty.trim(),
                            active = initial?.active ?: true,
                            catIds = catIds,
                            alternatePate = false,
                            productId = productId,
                            patternId = patternId,
                        )
                    )
                },
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun ProductCatalogDialog(
    products: List<FoodProduct>,
    initialProductId: String? = null,
    onSave: (FoodProduct) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by remember(initialProductId) { mutableStateOf(initialProductId) }
    val initialProduct = products.firstOrNull { it.id == initialProductId }
    var name by remember(initialProductId) { mutableStateOf(initialProduct?.name.orEmpty()) }
    var kind by remember(initialProductId) { mutableStateOf(initialProduct?.kind ?: "sachet") }
    var brand by remember(initialProductId) { mutableStateOf(initialProduct?.brand.orEmpty()) }
    var price by remember(initialProductId) { mutableStateOf(initialProduct?.packPrice.orEmpty()) }
    var weight by remember(initialProductId) { mutableStateOf(initialProduct?.weight.orEmpty()) }
    val selected = products.firstOrNull { it.id == selectedId && !it.deleted }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catalogue de nourriture") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (products.none { !it.deleted }) {
                    Text("Ajoute un produit pour le réutiliser dans tes programmes.")
                }
                products.filterNot { it.deleted }.forEach { product ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${pateEmoji(product.pateKey)} ${product.name}", fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(
                                    when (product.kind) {
                                        "poulet" -> "Poulet"
                                        "boeuf" -> "Bœuf"
                                        else -> "Autre"
                                    },
                                    product.brand,
                                    "${product.packPrice} €/sachet",
                                    product.weight.takeIf { it.isNotBlank() }?.let { "$it g" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = {
                            selectedId = product.id
                            name = product.name
                            kind = product.kind
                            brand = product.brand
                            price = product.packPrice
                            weight = product.weight
                        }) { Text("Modifier") }
                    }
                }
                Text(if (selected == null) "Nouveau produit" else "Modifier le produit", fontWeight = FontWeight.Bold)
                OutlinedTextField(name, { name = it }, label = { Text("Nom de la nourriture") }, singleLine = true)
                OutlinedTextField(brand, { brand = it }, label = { Text("Marque") }, singleLine = true)
                OutlinedTextField(
                    price,
                    { price = it },
                    label = { Text("Prix du sachet / portion (€)") },
                    placeholder = { Text("ex. 1,49") },
                    singleLine = true,
                )
                OutlinedTextField(
                    weight,
                    { weight = it.filter { char -> char.isDigit() || char == ',' || char == '.' } },
                    label = { Text("Poids du sachet / portion (g)") },
                    placeholder = { Text("ex. 85") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected != null && selected.id !in setOf("poulet", "boeuf")) {
                    TextButton(onClick = {
                        onDelete(selected.id)
                        selectedId = null
                        name = ""
                        brand = ""
                        price = ""
                        weight = ""
                    }) { Text("Supprimer") }
                }
                Button(
                    enabled = name.isNotBlank() && brand.isNotBlank() &&
                        (price.replace(',', '.').toDoubleOrNull()?.let { it >= 0 } == true) &&
                        (weight.isBlank() || weight.replace(',', '.').toDoubleOrNull()?.let { it > 0 } == true),
                    onClick = {
                        onSave(
                            FoodProduct(
                                id = selectedId ?: UUID.randomUUID().toString().take(8),
                                name = name.trim(),
                                kind = kind,
                                brand = brand.trim(),
                                packPrice = price.trim().replace(',', '.'),
                                weight = weight.trim().replace(',', '.'),
                            )
                        )
                        selectedId = null
                        name = ""
                        brand = ""
                        price = ""
                        weight = ""
                    },
                ) { Text(if (selected == null) "Ajouter" else "Enregistrer") }
            }
        },
        dismissButton = {
            Row {
                if (selectedId != null) {
                    TextButton(onClick = {
                        selectedId = null
                        name = ""
                        brand = ""
                        price = ""
                        weight = ""
                    }) { Text("Nouveau") }
                }
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        },
    )
}

@Composable
fun FoodSettingsScreen(
    patterns: List<FoodPattern>,
    modifier: Modifier,
    products: List<FoodProduct>,
    onSavePattern: (FoodPattern) -> Unit,
    onDeletePattern: (String) -> Unit,
) {
    var patternId by remember { mutableStateOf<String?>(null) }
    var patternName by remember { mutableStateOf("") }
    val sequence = remember { mutableStateListOf<String>() }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
            Text("Combinaisons de nourriture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Choisis les nourritures du catalogue et leur ordre.", style = MaterialTheme.typography.bodySmall)
            patterns.filterNot { it.deleted }.forEach { pattern ->
                val summary = pattern.foodIds.mapNotNull { id ->
                    products.firstOrNull { it.id == id && !it.deleted }?.let { "${pateEmoji(it.pateKey)} ${it.name}" }
                }.joinToString(" → ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pattern.name, fontWeight = FontWeight.Medium)
                        Text(summary, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        patternId = pattern.id
                        patternName = pattern.name
                        sequence.clear()
                        sequence.addAll(pattern.foodIds.filter { id -> products.any { it.id == id && !it.deleted } })
                    }) { Text("Modifier") }
                    TextButton(onClick = { onDeletePattern(pattern.id) }) { Text("Supprimer") }
                }
            }
            OutlinedTextField(patternName, { patternName = it }, label = { Text("Nom de la combinaison") }, singleLine = true)
            Text("Ajoute les nourritures dans l'ordre voulu :", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                products.filterNot { it.deleted }.forEach { food ->
                    FilterChip(
                        selected = false,
                        onClick = { sequence.add(food.id) },
                        label = { Text("+ ${pateEmoji(food.pateKey)} ${food.name}") },
                    )
                }
            }
            sequence.toList().forEachIndexed { index, id ->
                val food = products.firstOrNull { it.id == id }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}. ${food?.let { pateEmoji(it.pateKey) }.orEmpty()} ${food?.name.orEmpty()}",
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(
                        onClick = { if (index > 0) {
                            val item = sequence.removeAt(index)
                            sequence.add(index - 1, item)
                        } },
                        enabled = index > 0,
                    ) { Text("↑") }
                    FilledTonalIconButton(
                        onClick = { if (index < sequence.lastIndex) {
                            val item = sequence.removeAt(index)
                            sequence.add(index + 1, item)
                        } },
                        enabled = index < sequence.lastIndex,
                    ) { Text("↓") }
                    TextButton(onClick = { sequence.removeAt(index) }) { Text("Retirer") }
                }
            }
            Row {
                Button(
                    enabled = patternName.isNotBlank() && sequence.size >= 2,
                    onClick = {
                        onSavePattern(
                            FoodPattern(
                                id = patternId ?: UUID.randomUUID().toString().take(8),
                                name = patternName.trim(),
                                foodIds = sequence.toList(),
                            )
                        )
                        patternId = null
                        patternName = ""
                        sequence.clear()
                    },
                ) { Text(if (patternId == null) "Ajouter la combinaison" else "Enregistrer") }
                if (patternId != null) {
                    TextButton(onClick = {
                        patternId = null
                        patternName = ""
                        sequence.clear()
                    }) { Text("Nouvelle") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Encoder ce qui a été donné (ou corriger un repas de l'historique)
// ---------------------------------------------------------------------------------------------

@Composable
fun EncodeDialog(
    entry: Entry,
    cats: List<Cat>,
    products: List<FoodProduct>,
    foodTypes: List<FoodType>,
    onSave: (pate: String, qty: String, note: String, doneAt: Long, catIds: List<String>, photo: String, productId: String, foodTypeId: String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val wasDone = entry.status == Status.DONE

    var photo by remember(entry.id) { mutableStateOf(entry.photo) }
    var productId by remember(entry.id) {
        mutableStateOf(
            entry.productId.takeIf { id -> products.any { it.id == id && !it.deleted } }
                ?: products.firstOrNull { !it.deleted && it.pateKey == entry.pate }?.id.orEmpty(),
        )
    }
    var foodTypeId by remember(entry.id) {
        mutableStateOf(
            (if (wasDone) entry.givenFoodTypeId else entry.foodTypeId).ifEmpty { entry.pate }
        )
    }
    var pate by remember(entry.id) {
        mutableStateOf(if (wasDone && entry.givenPate.isNotEmpty()) entry.givenPate else entry.pate)
    }
    var qty by remember(entry.id) { mutableStateOf(if (wasDone) entry.givenQty else entry.qty) }
    var note by remember(entry.id) { mutableStateOf(entry.note) }
    var catIds by remember(entry.id) {
        mutableStateOf(
            entry.catIds.filter { id -> cats.any { it.id == id } }.ifEmpty { cats.map { it.id } }
        )
    }
    var doneAt by remember(entry.id) {
        mutableLongStateOf(
            when {
                wasDone && entry.doneAt > 0 -> entry.doneAt
                // Repas d'un jour précédent : on propose l'heure prévue plutôt que "maintenant".
                Instant.ofEpochMilli(entry.at).atZone(zone).toLocalDate()
                    .isBefore(LocalDate.now(zone)) -> entry.at
                else -> System.currentTimeMillis()
            }
        )
    }

    val doneDt = Instant.ofEpochMilli(doneAt).atZone(zone)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Qu'as-tu donné ?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Prévu : ${Fmt.date(entry.at)} à ${Fmt.time(entry.at)} — " +
                        entry.foodTypeName.ifEmpty { pateLabel(entry.pate) } +
                        if (entry.qty.isNotBlank()) " · ${entry.qty}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (cats.size > 1) {
                    Text("Quel(s) chat(s) ?", fontWeight = FontWeight.Bold)
                    CatPicker(cats, catIds) { catIds = it }
                }

                Text("Pâtée donnée", fontWeight = FontWeight.Bold)
                if (products.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        products.filter { !it.deleted || it.id == entry.productId }.forEach { product ->
                            FilterChip(
                                selected = productId == product.id,
                                onClick = {
                                    productId = product.id
                                    pate = product.pateKey
                                    foodTypeId = product.pateKey
                                },
                                label = {
                                    Text(
                                        "${pateEmoji(product.pateKey)} ${product.name}" +
                                            product.weight.takeIf { it.isNotBlank() }?.let { " · $it g" }.orEmpty()
                                    )
                                },
                            )
                        }
                    }
                }
                if (products.isEmpty()) {
                    FoodTypeChoice(foodTypes, foodTypeId) {
                        foodTypeId = it
                        pate = it
                    }
                } else {
                    products.firstOrNull { it.id == productId }?.let { product ->
                        Text(
                            "${product.brand} · ${product.packPrice} €/sachet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = { Text("Quantité") },
                    placeholder = { Text("ex : 1 sachet, 80 g") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Remarque (facultatif)") },
                    placeholder = { Text("ex : a laissé la moitié") },
                )
                Text("Photo du repas (facultatif)", fontWeight = FontWeight.Bold)
                PhotoEditor(photo) { photo = it }

                Text("Donné le", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val d = doneDt.toLocalDate()
                        DatePickerDialog(
                            ctx,
                            { _, y, m, day ->
                                doneAt = doneDt.withYear(y).withMonth(m + 1).withDayOfMonth(day)
                                    .toInstant().toEpochMilli()
                            },
                            d.year, d.monthValue - 1, d.dayOfMonth
                        ).show()
                    }) { Text(Fmt.dayName(doneDt.toLocalDate())) }
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            ctx,
                            { _, h, m ->
                                doneAt = doneDt.withHour(h).withMinute(m).withSecond(0)
                                    .toInstant().toEpochMilli()
                            },
                            doneDt.hour, doneDt.minute, true
                        ).show()
                    }) { Text(Fmt.time(doneAt)) }
                }

                if (entry.status != Status.SKIPPED) {
                    TextButton(onClick = onSkip) { Text("Je n'ai rien donné à ce repas") }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = catIds.isNotEmpty() || cats.isEmpty(),
                onClick = { onSave(pate, qty.trim(), note.trim(), doneAt, catIds, photo, productId, foodTypeId) },
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Ajouter / modifier un chat
// ---------------------------------------------------------------------------------------------

@Composable
fun CatDialog(
    initial: Cat?,
    onSave: (Cat) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableIntStateOf(initial?.color ?: 0) }
    var photo by remember { mutableStateOf(initial?.photo ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouveau chat" else "Modifier le chat") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                )
                Text("Photo de profil (facultatif)", fontWeight = FontWeight.Bold)
                PhotoEditor(photo) { photo = it }
                Text("Couleur", fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (i in 0 until CatColorCount) {
                        var m = Modifier
                            .size(38.dp)
                            .background(catColor(i), CircleShape)
                        if (i == color) m = m.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        Box(m.clickable { color = i })
                    }
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                    ) { Text("Supprimer ce chat") }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        Cat(
                            id = initial?.id ?: UUID.randomUUID().toString().take(8),
                            name = name.trim(),
                            color = color,
                            photo = photo,
                        )
                    )
                },
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Réglages
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsDialog(
    syncStatus: String,
    onSave: (serverUrl: String, token: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf(Prefs.serverUrl(ctx)) }
    var token by remember { mutableStateOf(Prefs.token(ctx)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réglages") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Serveur (facultatif)", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Adresse du serveur") },
                    placeholder = { Text("http://192.168.1.50:8765") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Code secret (si défini)") },
                    singleLine = true,
                )
                if (syncStatus.isNotEmpty()) {
                    Text(
                        syncStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = {
                    Prefs.saveServer(ctx, url, token)
                    thread { Sync.run(ctx) }
                }) { Text("Tester la synchronisation") }
                TextButton(onClick = { Notifier.showTest(ctx) }) { Text("Envoyer une notification de test") }
            }
        },
        confirmButton = { Button(onClick = { onSave(url, token) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
