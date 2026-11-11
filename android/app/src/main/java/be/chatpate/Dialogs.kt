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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
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

@Composable
fun KibblePackageDialog(
    cats: List<Cat>,
    initial: KibblePackage? = null,
    onSave: (KibblePackage) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var price by remember(initial?.id) { mutableStateOf(initial?.price.orEmpty()) }
    var brand by remember(initial?.id) { mutableStateOf(initial?.brand.orEmpty()) }
    var flavor by remember(initial?.id) { mutableStateOf(initial?.flavor.orEmpty()) }
    var specialty by remember(initial?.id) { mutableStateOf(initial?.specialty.orEmpty()) }
    var addedOn by remember(initial?.id) {
        mutableStateOf(
            initial?.addedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now(ZoneId.systemDefault())
        )
    }
    var finishedOn by remember(initial?.id) {
        mutableStateOf(
            if (initial?.addedOn.isNullOrBlank()) null
            else initial?.finishedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }
    var startLater by remember(initial?.id) { mutableStateOf(initial != null && initial.addedOn.isBlank()) }
    var catIds by remember(initial?.id) {
        mutableStateOf(initial?.catIds.orEmpty().filter { id -> cats.any { it.id == id } })
    }
    val parsedPrice = price.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Ajouter un paquet de croquettes" else "Modifier le paquet") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Marque (facultatif)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = flavor,
                    onValueChange = { flavor = it },
                    label = { Text("Goût (facultatif)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = specialty,
                    onValueChange = { specialty = it },
                    label = { Text("Spécificité (facultatif)") },
                    placeholder = { Text("ex. Urinary, Senior") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { char -> char.isDigit() || char == ',' || char == '.' } },
                    label = { Text("Prix du paquet (€)") },
                    placeholder = { Text("ex. 18,90") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Commencer plus tard", fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = startLater,
                        onCheckedChange = {
                            startLater = it
                            finishedOn = null
                        },
                    )
                }
                if (startLater) {
                    Text(
                        "Le paquet restera en attente jusqu’à ce que tu appuies sur « Commencer ».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Date de début", fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = {
                        DatePickerDialog(
                            ctx,
                            { _, year, month, day -> addedOn = LocalDate.of(year, month + 1, day) },
                            addedOn.year, addedOn.monthValue - 1, addedOn.dayOfMonth,
                        ).show()
                    }) { Text(Fmt.dateLong(addedOn)) }
                    Text("Date de fin (facultative)", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            val date = finishedOn ?: LocalDate.now(ZoneId.systemDefault())
                            DatePickerDialog(
                                ctx,
                                { _, year, month, day -> finishedOn = LocalDate.of(year, month + 1, day) },
                                date.year, date.monthValue - 1, date.dayOfMonth,
                            ).show()
                        }) {
                            Text(finishedOn?.let { Fmt.dateLong(it) } ?: "Paquet en cours")
                        }
                        if (finishedOn != null) {
                            TextButton(onClick = { finishedOn = null }) { Text("En cours") }
                        }
                    }
                }
                Text("Chats qui mangent ce paquet", fontWeight = FontWeight.Bold)
                CatPicker(cats, catIds) { catIds = it }
            }
        },
        confirmButton = {
            Button(
                enabled = parsedPrice != null && parsedPrice >= 0 && catIds.isNotEmpty() &&
                    (startLater || finishedOn == null || !finishedOn!!.isBefore(addedOn)),
                onClick = {
                    onSave(
                        KibblePackage(
                            id = initial?.id ?: UUID.randomUUID().toString().take(8),
                            price = parsedPrice!!.toString(),
                            addedOn = if (startLater) "" else addedOn.toString(),
                            finishedOn = finishedOn?.toString().orEmpty(),
                            catIds = catIds.distinct(),
                            brand = brand.trim(),
                            flavor = flavor.trim(),
                            specialty = specialty.trim(),
                            deleted = initial?.deleted ?: false,
                            updatedAt = initial?.updatedAt ?: System.currentTimeMillis(),
                        ),
                    )
                    onDismiss()
                },
            ) { Text(if (initial == null) "Enregistrer" else "Enregistrer les modifications") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Créer / modifier un programme
// ---------------------------------------------------------------------------------------------

@Composable
fun PlanDialog(
    cats: List<Cat>,
    products: List<FoodProduct>,
    foodPatterns: List<FoodPattern>,
    stockSettings: StockSettings,
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
    var autoAllocate by remember(initial?.id) { mutableStateOf(initial?.autoAllocate ?: false) }
    var productId by remember {
        mutableStateOf(
            initial?.productId?.takeIf { id -> products.any { it.id == id && !it.deleted && !it.putAside } }
                ?: if (patternId.isEmpty()) {
                    val legacyFoodId = initial?.pate
                    products.firstOrNull { !it.deleted && !it.putAside && it.id == legacyFoodId }?.id
                        ?: products.firstOrNull {
                            !it.deleted && !it.putAside && it.pateKey == (legacyFoodId ?: Pate.POULET.key)
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
    val forecastShortages = if (!autoAllocate || catIds.isEmpty()) 0 else {
        val today = LocalDate.now(ZoneId.systemDefault())
        val forecastStock = FoodAllocator.initialStock(products)
        var shortages = 0
        for (offset in 0..14) {
            val date = today.plusDays(offset.toLong())
            val daysFromStart = java.time.temporal.ChronoUnit.DAYS.between(start, date)
            if (daysFromStart < 0 || daysFromStart % every.coerceAtLeast(1) != 0L) continue
            val at = date.atTime(timeMin / 60, timeMin % 60)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (at <= System.currentTimeMillis()) continue
            shortages += FoodAllocator.allocate(
                catIds,
                products,
                forecastStock,
                stockSettings.warningThreshold,
                stockSettings.lowThreshold,
            ).count { !it.inStock }
        }
        shortages
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Répartir équitablement les nourritures", fontWeight = FontWeight.Bold)
                        Text(
                            "L'application répartit les aliments disponibles selon le stock pour éviter d'en épuiser un avant les autres.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoAllocate, onCheckedChange = { autoAllocate = it })
                }

                if (autoAllocate) {
                    if (catIds.isEmpty()) {
                        Text(
                            "Sélectionne au moins un chat pour préparer l’attribution.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    cats.filter { it.id in catIds }.forEach { cat ->
                        val applicable = products.filterNot { it.deleted || it.putAside }
                        val restricted = applicable.filter { cat.id in it.catIds }
                        val eligible = if (restricted.isNotEmpty()) restricted else {
                            applicable.filter { it.catIds.isEmpty() }
                        }
                        val tracked = eligible.filter { it.availableServings != null }
                        val best = tracked.maxByOrNull { it.availableServings ?: 0L }
                        val summary = when {
                            eligible.isEmpty() ->
                                "Aucune nourriture configurée pour ${cat.name}."
                            best == null ->
                                "Stock non encodé pour ${cat.name} : ajoute les quantités dans Alimentation."
                            best.availableServings!! <= 0L ->
                                "Stock épuisé pour ${cat.name} (${best.name}) : vérifie la quantité avant de continuer."
                            best.availableServings!! <= stockSettings.lowThreshold ->
                                "Stock bas pour ${cat.name} : ${best.name}, ${best.availableServings} portion(s)."
                            best.availableServings!! <= stockSettings.warningThreshold ->
                                "À surveiller pour ${cat.name} : ${best.name}, ${best.availableServings} portion(s)."
                            else -> "${cat.name} : ${best.name}, ${best.availableServings} portion(s) disponibles."
                        }
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (best == null || best.availableServings!! <= stockSettings.lowThreshold) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        "Chaque chat reçoit une portion. Une nourriture spécifique n’est jamais remplacée automatiquement par le stock commun. Tu peux continuer malgré un avertissement.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (forecastShortages > 0) {
                        Text(
                            "Stock encodé insuffisant pour $forecastShortages portion(s) sur les 14 prochains jours pour ce programme. Les autres programmes peuvent encore utiliser ce stock.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "Ajoute ou actualise les nourritures dans Alimentation > Actualisation des stocks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!autoAllocate && products.isNotEmpty()) {
                    Text("Nourriture seule", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        products.filterNot { it.deleted || it.putAside }.forEach { product ->
                            FilterChip(
                                selected = productId == product.id,
                                onClick = {
                                    productId = product.id
                                    pate = product.pateKey
                                    patternId = ""
                                },
                                leadingIcon = {
                                    FoodIcon(product.pateKey, product.iconColor, size = 18.dp)
                                },
                                label = {
                                    Text(product.name)
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
                if (autoAllocate) {
                    Text("Quantité prévue : 1 portion par chat sélectionné.")
                } else {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Quantité (facultatif)") },
                        placeholder = { Text("ex : 1 sachet, 80 g") },
                        singleLine = true,
                    )
                }

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
                            qty = if (autoAllocate) "1 portion / chat" else qty.trim(),
                            active = initial?.active ?: true,
                            catIds = catIds,
                            alternatePate = false,
                            productId = if (autoAllocate) "" else productId,
                            patternId = if (autoAllocate) "" else patternId,
                            autoAllocate = autoAllocate,
                        )
                    )
                },
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun AddSachetBoxDialog(
    products: List<FoodProduct>,
    onAdd: (List<FoodProduct>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sachets = products.filter { !it.deleted && it.packaging == "sachet" }
    val lines = remember {
        mutableStateListOf(SachetBoxLine())
    }
    var totalPriceText by remember { mutableStateOf("") }
    val totalPrice = totalPriceText.replace(',', '.').toBigDecimalOrNull()
    val totalQuantity = lines.sumOf { it.quantity.toIntOrNull()?.toLong() ?: 0L }
    val unitPrice = if (totalQuantity > 0L && totalPrice != null && totalPrice >= BigDecimal.ZERO) {
        totalPrice.divide(BigDecimal.valueOf(totalQuantity), 4, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()
    } else null
    val validLines = lines.isNotEmpty() && lines.all { line ->
        val count = line.quantity.toIntOrNull()
        count != null && count > 0 && (
            if (line.createNew) {
                line.name.isNotBlank() &&
                    products.none { !it.deleted && it.name.trim().equals(line.name.trim(), true) } &&
                    lines.count { other ->
                        other.createNew && other.name.trim().equals(line.name.trim(), true)
                    } == 1
            } else {
                sachets.any { it.id == line.productId }
            }
        )
    } && lines.filterNot { it.createNew }.groupBy { it.productId }.all { (productId, sameProduct) ->
        val product = sachets.firstOrNull { it.id == productId } ?: return@all false
        val added = sameProduct.sumOf { it.quantity.toInt().toLong() }
        (product.stockCount ?: 0).toLong() + added <= Int.MAX_VALUE.toLong()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une boîte de sachets") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Ajoute chaque goût de la boîte et son nombre de sachets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lines.forEachIndexed { index, line ->
                    var menuExpanded by remember(index) { mutableStateOf(false) }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    val selectedName = if (line.createNew) {
                                        line.name.ifBlank { "Nouveau goût…" }
                                    } else {
                                        sachets.firstOrNull { it.id == line.productId }?.name
                                            ?: "Choisir un goût"
                                    }
                                    Text(selectedName, maxLines = 1)
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    sachets.forEach { product ->
                                        DropdownMenuItem(
                                            text = { Text(product.name) },
                                            onClick = {
                                                lines[index] = line.copy(
                                                    productId = product.id,
                                                    createNew = false,
                                                    name = "",
                                                )
                                                menuExpanded = false
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Créer un nouveau goût…") },
                                        onClick = {
                                            lines[index] = line.copy(productId = "", createNew = true)
                                            menuExpanded = false
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedTextField(
                                value = line.quantity,
                                onValueChange = { value ->
                                    lines[index] = line.copy(quantity = value.filter(Char::isDigit))
                                },
                                label = { Text("Qté") },
                                singleLine = true,
                                modifier = Modifier.width(78.dp),
                            )
                            if (lines.size > 1) {
                                TextButton(onClick = { lines.removeAt(index) }) { Text("×") }
                            }
                        }
                        if (line.createNew) {
                            OutlinedTextField(
                                value = line.name,
                                onValueChange = { value -> lines[index] = line.copy(name = value) },
                                label = { Text("Nom du nouveau goût") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (Pate.values().map { it.key to it.label } + ("sachet" to "Autre"))
                                    .forEach { (key, label) ->
                                        FilterChip(
                                            selected = line.kind == key,
                                            onClick = { lines[index] = line.copy(kind = key) },
                                            label = { Text(label) },
                                        )
                                    }
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { lines.add(SachetBoxLine()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("+ Ajouter un goût") }
                OutlinedTextField(
                    value = totalPriceText,
                    onValueChange = { value ->
                        totalPriceText = value.filter { it.isDigit() || it == ',' || it == '.' }
                    },
                    label = { Text("Prix total de la boîte (€)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (unitPrice != null) {
                    Text(
                        "Total : $totalQuantity sachet(s) · $unitPrice € par sachet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = validLines && totalQuantity > 0L &&
                    totalPrice != null && totalPrice > BigDecimal.ZERO,
                onClick = {
                    val count = totalQuantity
                    val pricePerSachet = totalPrice!!
                        .divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()
                    val updates = lines.filter { it.createNew }.map { line ->
                        val quantity = line.quantity.toInt()
                        FoodProduct(
                            id = UUID.randomUUID().toString().take(8),
                            name = line.name.trim(),
                            kind = line.kind,
                            brand = "",
                            packPrice = pricePerSachet,
                            stockCount = quantity,
                            packaging = "sachet",
                            iconColor = defaultFoodIconColor(line.kind),
                        )
                    } + lines.filterNot { it.createNew }.groupBy { it.productId }.map { (productId, sameProduct) ->
                        val product = sachets.first { it.id == productId }
                        val quantity = sameProduct.sumOf { it.quantity.toInt().toLong() }
                        product.copy(
                            packPrice = pricePerSachet,
                            stockCount = ((product.stockCount ?: 0).toLong() + quantity).toInt(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                    onAdd(updates)
                    onDismiss()
                },
            ) { Text("Ajouter au stock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private data class SachetBoxLine(
    val productId: String = "",
    val createNew: Boolean = false,
    val name: String = "",
    val kind: String = "sachet",
    val quantity: String = "",
)

@Composable
fun ProductCatalogDialog(
    products: List<FoodProduct>,
    cats: List<Cat>,
    stockSettings: StockSettings,
    initialProductId: String? = null,
    onSave: (FoodProduct) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by remember(initialProductId) { mutableStateOf(initialProductId) }
    val initialProduct = products.firstOrNull { it.id == initialProductId }
    var name by remember(initialProductId) { mutableStateOf(initialProduct?.name.orEmpty()) }
    var kind by remember(initialProductId) { mutableStateOf(initialProduct?.kind ?: "poulet") }
    var iconColor by remember(initialProductId) {
        mutableStateOf(initialProduct?.iconColor ?: defaultFoodIconColor(initialProduct?.kind ?: "poulet"))
    }
    var brand by remember(initialProductId) { mutableStateOf(initialProduct?.brand.orEmpty()) }
    var price by remember(initialProductId) { mutableStateOf(initialProduct?.packPrice.orEmpty()) }
    var weight by remember(initialProductId) { mutableStateOf(initialProduct?.weight.orEmpty()) }
    var stockCount by remember(initialProductId) { mutableStateOf(initialProduct?.stockCount?.toString().orEmpty()) }
    var packaging by remember(initialProductId) { mutableStateOf(initialProduct?.packaging ?: "sachet") }
    var servingsPerPackage by remember(initialProductId) {
        mutableIntStateOf(initialProduct?.servingsPerPackage ?: 1)
    }
    var openServings by remember(initialProductId) {
        mutableStateOf(initialProduct?.openServings?.toString().orEmpty())
    }
    var hasOpenPackage by remember(initialProductId, packaging) {
        mutableStateOf(packaging != "sachet" && (initialProduct?.openServings ?: 0) > 0)
    }
    var catIds by remember(initialProductId) { mutableStateOf(initialProduct?.catIds.orEmpty()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val selected = products.firstOrNull { it.id == selectedId && !it.deleted }
    val parsedStock = stockCount.toIntOrNull()
    val packageMax = FoodProduct.maxServings(packaging, stockSettings)
    val selectedOpenConsumed = (servingsPerPackage - (openServings.toIntOrNull() ?: 0))
        .coerceIn(0, servingsPerPackage)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selected == null) "Ajouter une nourriture" else "Modifier la nourriture") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Nom de la nourriture") }, singleLine = true)
                OutlinedTextField(brand, { brand = it }, label = { Text("Marque") }, singleLine = true)
                Text("Type de nourriture", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pate.values().forEach { foodType ->
                        FilterChip(
                            selected = kind == foodType.key,
                            onClick = {
                                kind = foodType.key
                                iconColor = defaultFoodIconColor(foodType.key)
                            },
                            leadingIcon = {
                                FoodIcon(foodType.key, iconColor, foodType.emoji, 18.dp)
                            },
                            label = { Text(foodType.label) },
                        )
                    }
                    FilterChip(
                        selected = kind == "sachet",
                        onClick = {
                            kind = "sachet"
                            iconColor = defaultFoodIconColor("sachet")
                        },
                        leadingIcon = { FoodIcon("sachet", iconColor, "🥫", 18.dp) },
                        label = { Text("Autre") },
                    )
                }
                Text("Couleur de l’icône", fontWeight = FontWeight.Bold)
                val iconColors = listOf(
                    "#FF4F8FC9", "#FFB84A4A", "#FFE58A24", "#FF4F9B6A",
                    "#FF9B6BC4", "#FF3E9F9A", "#FF74808F", "#FFEC5A9D",
                    "#FF7E57C2", "#FFAB47BC", "#FF5C6BC0", "#FF26A69A",
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    iconColors.forEach { colorValue ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(foodIconColor(colorValue, MaterialTheme.colorScheme.primary), CircleShape)
                                .border(
                                    width = if (iconColor == colorValue) 3.dp else 1.dp,
                                    color = if (iconColor == colorValue) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .clickable { iconColor = colorValue },
                        )
                    }
                    FoodIcon(kind, iconColor, pateEmoji(kind), 24.dp)
                }
                OutlinedTextField(
                    price,
                    { price = it },
                    label = { Text("Prix de l'emballage (€)") },
                    placeholder = { Text("ex. 1,49") },
                    singleLine = true,
                )
                OutlinedTextField(
                    weight,
                    { weight = it.filter { char -> char.isDigit() || char == ',' || char == '.' } },
                    label = { Text("Poids de l'emballage (g)") },
                    placeholder = { Text("ex. 85") },
                    singleLine = true,
                )
                Text("Type d'emballage", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("sachet" to "Sachet", "boite" to "Boîte", "barquette" to "Barquette")
                        .forEach { (key, label) ->
                            FilterChip(
                                selected = packaging == key,
                                onClick = {
                                    packaging = key
                                    servingsPerPackage = FoodProduct.maxServings(key, stockSettings)
                                    if (key == "sachet") {
                                        openServings = "0"
                                        hasOpenPackage = false
                                    } else {
                                        val remaining = (openServings.toIntOrNull() ?: 0)
                                            .coerceIn(0, FoodProduct.maxServings(key, stockSettings))
                                        openServings = remaining.toString()
                                        hasOpenPackage = remaining > 0
                                    }
                                },
                                label = { Text(label) },
                            )
                        }
                }
                if (packaging == "sachet") {
                    Text(
                        "Un sachet correspond à une portion. Le stock est compté en sachets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        if (packaging == "boite") "Portions par boîte" else "Portions par barquette",
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..packageMax).forEach { portions ->
                            FilterChip(
                                selected = servingsPerPackage == portions,
                                onClick = {
                                    val previouslyConsumed = (servingsPerPackage -
                                        (openServings.toIntOrNull() ?: 0)).coerceAtLeast(0)
                                    servingsPerPackage = portions
                                    if (hasOpenPackage) {
                                        val consumed = previouslyConsumed.coerceAtMost(portions)
                                        openServings = (portions - consumed)
                                            .coerceIn(0, portions - 1)
                                            .toString()
                                    } else {
                                        openServings = "0"
                                    }
                                },
                                label = { Text("${portions}x") },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            if (packaging == "boite") "Boîte déjà ouverte" else "Barquette déjà ouverte",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Switch(
                            checked = hasOpenPackage,
                            onCheckedChange = {
                                hasOpenPackage = it
                                if (it) {
                                    openServings = (servingsPerPackage - 1).coerceAtLeast(0).toString()
                                } else {
                                    openServings = "0"
                                }
                            },
                        )
                    }
                    if (hasOpenPackage) {
                        Text("Nombre d'utilisations déjà consommées", fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (0..servingsPerPackage).forEach { consumed ->
                                FilterChip(
                                    selected = selectedOpenConsumed == consumed,
                                    onClick = {
                                        val remaining = if (consumed == 0) 0 else servingsPerPackage - consumed
                                        openServings = remaining.toString()
                                    },
                                    label = { Text("${consumed}x") },
                                )
                            }
                        }
                    }
                }
                Text("Pour quel(s) chat(s) ?", fontWeight = FontWeight.Bold)
                CatPicker(cats, catIds) { catIds = it }
                Text(
                    if (catIds.isEmpty()) {
                        "Stock partagé entre tous les chats."
                    } else {
                        "Nourriture réservée aux chats sélectionnés ; aucun remplacement automatique par le stock commun."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Stock disponible", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    stockCount,
                    { stockCount = it.filter(Char::isDigit) },
                    label = {
                        Text(
                            if (packaging == "sachet") "Nombre de sachets" else "Nombre d'emballages fermés"
                        )
                    },
                    placeholder = { Text("Laisser vide pour ne pas suivre le stock") },
                    singleLine = true,
                )
                if (packaging != "sachet" && hasOpenPackage) {
                    Text(
                        "Emballage ouvert actuellement : ${selectedOpenConsumed}x consommé(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedId != null) {
                    OutlinedButton(
                        onClick = {
                            selectedId = null
                            name = ""
                            kind = "poulet"
                            iconColor = defaultFoodIconColor(kind)
                            brand = ""
                            price = ""
                            weight = ""
                            stockCount = ""
                            packaging = "sachet"
                            servingsPerPackage = 1
                            openServings = ""
                            catIds = emptyList()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_action_plus),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(" Nouveau")
                    }
                }
                Spacer(Modifier.weight(1f))
                if (selected != null && selected.id !in setOf("poulet", "boeuf")) {
                    IconButton(onClick = { showDeleteConfirmation = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_action_delete),
                            contentDescription = "Supprimer cette nourriture",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.size(36.dp))
                }
                TextButton(onClick = onDismiss) { Text("Fermer") }
                Button(
                    enabled = name.isNotBlank() && brand.isNotBlank() &&
                        (price.replace(',', '.').toDoubleOrNull()?.let { it >= 0 } == true) &&
                        (weight.isBlank() || weight.replace(',', '.').toDoubleOrNull()?.let { it > 0 } == true) &&
                        (stockCount.isBlank() || (parsedStock != null && parsedStock >= 0)) &&
                        (stockCount.isNotBlank() || openServings.isBlank() || openServings.toIntOrNull() == 0) &&
                        (openServings.isBlank() || openServings.toIntOrNull()?.let {
                            it in 0 until servingsPerPackage
                        } == true),
                    onClick = {
                        onSave(
                            FoodProduct(
                                id = selectedId ?: UUID.randomUUID().toString().take(8),
                                name = name.trim(),
                                kind = kind,
                                brand = brand.trim(),
                                packPrice = price.trim().replace(',', '.'),
                                weight = weight.trim().replace(',', '.'),
                                stockCount = parsedStock,
                                putAside = selected?.putAside ?: false,
                                packaging = packaging,
                                servingsPerPackage = servingsPerPackage,
                                openServings = openServings.toIntOrNull() ?: 0,
                                catIds = catIds,
                                iconColor = iconColor,
                            )
                        )
                        onDismiss()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) { Text(if (selected == null) "Ajouter" else "Enregistrer") }
            }
        },
    )

    if (showDeleteConfirmation && selected != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Supprimer cette nourriture ?") },
            text = { Text("« ${selected.name} » sera retirée du catalogue et des combinaisons qui l’utilisent.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(selected.id)
                        selectedId = null
                        name = ""
                        kind = "poulet"
                        iconColor = defaultFoodIconColor(kind)
                        brand = ""
                        price = ""
                        weight = ""
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Annuler") }
            },
        )
    }
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
                val foods = pattern.foodIds.mapNotNull { id ->
                    products.firstOrNull { it.id == id && !it.deleted }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pattern.name, fontWeight = FontWeight.Medium)
                        FlowRow(verticalArrangement = Arrangement.Center) {
                            foods.forEachIndexed { index, food ->
                                if (index > 0) Text(" → ")
                                FoodIcon(food.pateKey, food.iconColor, size = 16.dp)
                                Text(food.name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
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
                products.filterNot { it.deleted || it.putAside }.forEach { food ->
                    FilterChip(
                        selected = false,
                        onClick = { sequence.add(food.id) },
                        leadingIcon = { FoodIcon(food.pateKey, food.iconColor, size = 18.dp) },
                        label = { Text("+ ${food.name}") },
                    )
                }
            }
            sequence.toList().forEachIndexed { index, id ->
                val food = products.firstOrNull { it.id == id }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1}. ")
                        if (food != null) {
                            FoodIcon(food.pateKey, food.iconColor, size = 18.dp)
                            Spacer(Modifier.size(4.dp))
                        }
                        Text(food?.name.orEmpty())
                    }
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
    onDelete: () -> Unit,
    onDeletePermanently: () -> Unit,
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
    var confirmDelete by remember(entry.id) { mutableStateOf(false) }
    var confirmPermanentDelete by remember(entry.id) { mutableStateOf(false) }

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
                if (entry.foodAllocations.isNotEmpty()) {
                    entry.foodAllocations.forEach { allocation ->
                        val catName = cats.firstOrNull { it.id == allocation.catId }?.name ?: "Chat"
                        val foodName = products.firstOrNull { it.id == allocation.productId }?.name
                        Text(
                            "$catName : ${foodName ?: "nourriture à configurer"}" +
                                when {
                                    !allocation.inStock -> " — stock insuffisant, vérifie avant de servir"
                                    allocation.lowStock -> " — stock bas"
                                    allocation.stockWarning -> " — stock à surveiller"
                                    else -> ""
                                },
                            color = if (!allocation.inStock) {
                                MaterialTheme.colorScheme.error
                            } else if (allocation.lowStock) {
                                MaterialTheme.colorScheme.error
                            } else if (allocation.stockWarning) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        "Tu peux choisir un autre aliment pour ce repas ; les prochains repas seront recalculés après l’enregistrement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = productId.isEmpty(),
                            onClick = {
                                productId = ""
                                pate = entry.pate
                                foodTypeId = entry.foodTypeId
                            },
                            label = { Text("Suivre la planification") },
                        )
                        products.filter { (!it.deleted && !it.putAside) || it.id == entry.productId }
                            .forEach { product ->
                                FilterChip(
                                    selected = productId == product.id,
                                    onClick = {
                                        productId = product.id
                                        pate = product.pateKey
                                        foodTypeId = product.pateKey
                                    },
                                    leadingIcon = {
                                        FoodIcon(product.pateKey, product.iconColor, size = 18.dp)
                                    },
                                    label = { Text(product.name) },
                                )
                            }
                    }
                } else if (products.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        products.filter { (!it.deleted && !it.putAside) || it.id == entry.productId }.forEach { product ->
                            FilterChip(
                                selected = productId == product.id,
                                onClick = {
                                    productId = product.id
                                    pate = product.pateKey
                                    foodTypeId = product.pateKey
                                },
                                leadingIcon = {
                                    FoodIcon(product.pateKey, product.iconColor, size = 18.dp)
                                },
                                label = {
                                    Text(
                                        product.name +
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
                            "${product.brand} · ${product.packPrice} €/${product.packagingLabel.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (entry.foodAllocations.isNotEmpty()) {
                    Text("Quantité prévue : 1 portion par chat sélectionné.")
                } else {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Quantité") },
                        placeholder = { Text("ex : 1 sachet, 80 g") },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Remarque (facultatif)") },
                    placeholder = { Text("ex : a laissé la moitié") },
                )
                Text("Photo du repas (facultatif)", fontWeight = FontWeight.Bold)
                PhotoEditor(photo, onChange = { photo = it })

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Annuler") }
                if ((entry.status != Status.PENDING || entry.deleted) && !entry.permanentlyDeleted) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_delete),
                            contentDescription = "Supprimer l'encodage",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                Button(
                    enabled = catIds.isNotEmpty() || cats.isEmpty(),
                    onClick = { onSave(pate, qty.trim(), note.trim(), doneAt, catIds, photo, productId, foodTypeId) },
                ) { Text("Enregistrer") }
            }
        },
        dismissButton = {},
    )
    if (confirmDelete) {
        val future = entry.at > System.currentTimeMillis()
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (entry.deleted) "Supprimer entièrement ce repas ?" else "Que veux-tu supprimer ?") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = when {
                            entry.deleted -> "Le repas et ses données seront retirés du calendrier et de l'historique."
                            future -> "Le repas sera rétabli comme repas à venir."
                            else -> "Tu peux effacer uniquement les données saisies ou supprimer entièrement ce repas du calendrier et de l'historique."
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (entry.deleted || !future) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = if (entry.deleted) {
                                { confirmDelete = false; confirmPermanentDelete = true }
                            } else onDelete,
                        ) {
                            Text(if (entry.deleted) "Supprimer entièrement" else "Supprimer l'encodage")
                        }
                        if (!entry.deleted) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    confirmDelete = false
                                    confirmPermanentDelete = true
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text("Supprimer entièrement")
                            }
                        }
                    } else {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onDelete,
                        ) {
                            Text("Rétablir le repas")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
    if (confirmPermanentDelete) {
        AlertDialog(
            onDismissRequest = { confirmPermanentDelete = false },
            title = { Text("Confirmer la suppression entière ?") },
            text = {
                Text("Ce repas et toutes ses données seront définitivement retirés du calendrier et de l'historique. Cette action est irréversible.")
            },
            confirmButton = {
                TextButton(onClick = onDeletePermanently) {
                    Text("Supprimer entièrement", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPermanentDelete = false }) { Text("Annuler") }
            },
        )
    }
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
                PhotoEditor(photo, onChange = { photo = it }, squareCropOnly = true)
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
    updateMessage: String,
    checkingUpdates: Boolean,
    serverEnabled: Boolean,
    stockSettings: StockSettings,
    onCheckUpdates: () -> Unit,
    onSave: (serverUrl: String, token: String, serverEnabled: Boolean, stockSettings: StockSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val appVersion = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() }
    var url by remember { mutableStateOf(Prefs.serverUrl(ctx)) }
    var token by remember { mutableStateOf(Prefs.token(ctx)) }
    var serverEnabled by remember { mutableStateOf(serverEnabled) }
    var warningThreshold by remember(stockSettings) {
        mutableStateOf(stockSettings.warningThreshold.toString())
    }
    var lowThreshold by remember(stockSettings) {
        mutableStateOf(stockSettings.lowThreshold.toString())
    }
    var boiteMaxServings by remember(stockSettings) {
        mutableStateOf(stockSettings.boiteMaxServings.toString())
    }
    var barquetteMaxServings by remember(stockSettings) {
        mutableStateOf(stockSettings.barquetteMaxServings.toString())
    }
    val warning = warningThreshold.toIntOrNull()
    val low = lowThreshold.toIntOrNull()
    val boiteMax = boiteMaxServings.toIntOrNull()
    val barquetteMax = barquetteMaxServings.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réglages") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Seuils de stock", fontWeight = FontWeight.Bold)
                Text(
                    "Les seuils sont exprimés en portions, quel que soit le type d’emballage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = warningThreshold,
                    onValueChange = { warningThreshold = it.filter(Char::isDigit) },
                    label = { Text("Seuil orange (portions à surveiller)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = lowThreshold,
                    onValueChange = { lowThreshold = it.filter(Char::isDigit) },
                    label = { Text("Seuil rouge (portions, stock bas)") },
                    singleLine = true,
                )
                Text(
                    "Ces seuils s’appliquent à toutes les nourritures suivies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Capacités des emballages multi-usage", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = boiteMaxServings,
                    onValueChange = { boiteMaxServings = it.filter(Char::isDigit) },
                    label = { Text("Boîte (portions max)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = barquetteMaxServings,
                    onValueChange = { barquetteMaxServings = it.filter(Char::isDigit) },
                    label = { Text("Barquette (portions max)") },
                    singleLine = true,
                )
                Text(
                    "Ces limites servent de base pour les nouvelles boîtes et barquettes ajoutées dans le stock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Serveur facultatif", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(if (serverEnabled) "Synchronisation activée" else "Utiliser l’application hors ligne")
                    Switch(checked = serverEnabled, onCheckedChange = { serverEnabled = it })
                }
                if (serverEnabled) {
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
                    TextButton(onClick = {
                        Prefs.saveServer(ctx, url, token, serverEnabled)
                        thread { Sync.run(ctx) }
                    }) { Text("Tester la synchronisation") }
                }
                if (serverEnabled && syncStatus.isNotEmpty()) {
                    Text(
                        syncStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Tests", fontWeight = FontWeight.Bold)
                TextButton(onClick = { Notifier.showTest(ctx) }) { Text("Envoyer une notification de test") }
                Text("Mises à jour de l’application", fontWeight = FontWeight.Bold)
                if (updateMessage.isNotBlank()) {
                    Text(updateMessage, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onCheckUpdates, enabled = !checkingUpdates) {
                    Text(if (checkingUpdates) "Vérification…" else "Vérifier sur GitHub")
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "v$appVersion",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onDismiss) { Text("Fermer") }
                Button(
                    enabled = warning != null && low != null && low <= warning &&
                        boiteMax != null && boiteMax!! > 0 && barquetteMax != null && barquetteMax!! > 0,
                    onClick = {
                        onSave(
                            url,
                            token,
                            serverEnabled,
                            StockSettings(
                                warningThreshold = warning ?: stockSettings.warningThreshold,
                                lowThreshold = low ?: stockSettings.lowThreshold,
                                boiteMaxServings = boiteMax ?: stockSettings.boiteMaxServings,
                                barquetteMaxServings = barquetteMax ?: stockSettings.barquetteMaxServings,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    },
                ) { Text("Enregistrer") }
            }
        },
        dismissButton = {},
    )
}
