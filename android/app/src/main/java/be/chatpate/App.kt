@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package be.chatpate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.text.NumberFormat
import java.util.Locale
import kotlin.concurrent.thread

@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(20_000)
        value = System.currentTimeMillis()
    }
}

@Composable
fun App(openEntry: MutableState<String?>) {
    val ctx = LocalContext.current
    val state by Store.state.collectAsState()
    val syncStatus by Sync.status.collectAsState()
    val updateStatus by AppUpdater.state.collectAsState()
    var serverEnabled by remember { mutableStateOf(Prefs.serverEnabled(ctx)) }
    val cats = state.cats.filter { !it.deleted }

    var tab by remember { mutableIntStateOf(0) }
    var stockDraft by remember { mutableStateOf<List<FoodProduct>?>(null) }
    var managingStocks by remember { mutableStateOf(false) }
    var showDiscardStockConfirmation by remember { mutableStateOf(false) }
    var pendingStockExitAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val hasUnsavedStockChanges = stockDraft?.let { draft ->
        draft.map { it.copy(updatedAt = 0L) } != state.products.map { it.copy(updatedAt = 0L) }
    } == true
    val requestStockExit: (() -> Unit) -> Unit = { onExit ->
        if (managingStocks && hasUnsavedStockChanges) {
            pendingStockExitAction = onExit
            showDiscardStockConfirmation = true
        } else {
            managingStocks = false
            stockDraft = null
            onExit()
        }
    }
    var showSettings by remember { mutableStateOf(false) }
    var dismissedUpdateTag by remember { mutableStateOf<String?>(null) }
    var showNewPlan by remember { mutableStateOf(false) }
    var editPlanId by remember { mutableStateOf<String?>(null) }
    var encodeId by remember { mutableStateOf<String?>(null) }
    var showNewCat by remember { mutableStateOf(false) }
    var editCatId by remember { mutableStateOf<String?>(null) }
    var selectedCatId by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = tab == 2 && cats.any { it.id == selectedCatId }) {
        selectedCatId = null
    }

    // Revérifie les autorisations quand on revient dans l'appli.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val exactOk = remember(tick) { Alarms.canScheduleExact(ctx) }
    val notifOk = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }

    LaunchedEffect(Unit) { AppUpdater.check(ctx) }

    // Ouverture depuis une notification.
    LaunchedEffect(openEntry.value) {
        val requested = openEntry.value
        if (requested != null) {
            encodeId = requested
            openEntry.value = null
        }
    }

    val skip: (String) -> Unit = {
        Store.markSkipped(it)
        Notifier.cancel(ctx, it)
        Planner.afterChange(ctx)
    }

    Scaffold(
        topBar = {
            AppHeader(
                serverEnabled = serverEnabled,
                syncStatus = syncStatus,
                onSync = { thread { Sync.run(ctx) } },
                onSettings = { showSettings = true },
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BottomNavItem(tab == 0, {
                        if (tab == 3) requestStockExit { tab = 0 } else tab = 0
                    }, R.drawable.ic_nav_home, "Accueil")
                    BottomNavItem(tab == 1, {
                        if (tab == 3) requestStockExit { tab = 1 } else tab = 1
                    }, R.drawable.ic_nav_programs, "Programmes")
                    BottomNavItem(tab == 2, {
                        if (tab == 3) requestStockExit { tab = 2 } else tab = 2
                    }, R.drawable.ic_nav_cats, "Chats")
                    BottomNavItem(tab == 3, { tab = 3 }, R.drawable.ic_nav_food, "Alimentation")
                }
            }
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showNewPlan = true },
                    icon = { Icon(painterResource(R.drawable.ic_action_plus), contentDescription = null) },
                    text = { Text("Programme") },
                )
            } else if (tab == 2 && cats.none { it.id == selectedCatId }) {
                ExtendedFloatingActionButton(
                    onClick = { showNewCat = true },
                    icon = { Icon(painterResource(R.drawable.ic_action_plus), contentDescription = null) },
                    text = { Text("Chat") },
                )
            }
        },
    ) { padding ->
        val mod = Modifier
            .fillMaxSize()
            .padding(padding)
        when (tab) {
            0 -> HomeScreen(
                state = state,
                cats = cats,
                products = state.products,
                exactOk = exactOk,
                notifOk = notifOk,
                modifier = mod,
                onOpen = { encodeId = it },
                onSkip = skip,
            )

            1 -> PlansScreen(
                plans = state.plans.filter { !it.deleted }.sortedBy { it.timeMin },
                cats = cats,
                products = state.products.filter { !it.deleted },
                foodPatterns = state.foodPatterns,
                modifier = mod,
                onEdit = { editPlanId = it },
                onToggle = {
                    Store.upsertPlan(it.copy(active = !it.active))
                    Planner.afterChange(ctx)
                },
            )

            2 -> CatsScreen(
                cats = cats,
                plans = state.plans.filter { !it.deleted },
                entries = state.entries.filter { !it.deleted },
                products = state.products,
                foodPatterns = state.foodPatterns,
                kibblePackages = state.kibblePackages.filterNot { it.deleted },
                selectedCatId = selectedCatId,
                modifier = mod,
                onSelect = { selectedCatId = it },
                onBack = { selectedCatId = null },
                onOpenEntry = { encodeId = it },
                onEdit = { editCatId = it },
            )

            else -> FoodManagementScreen(
                products = state.products.filter { !it.deleted },
                kibblePackages = state.kibblePackages.filterNot { it.deleted },
                cats = cats,
                stockSettings = state.stockSettings,
                modifier = mod,
                stockDraft = stockDraft,
                managingStocks = managingStocks,
                onManagingStocksChange = { managingStocks = it },
                onRequestStockExit = requestStockExit,
                onStockDraftChange = { stockDraft = it },
                onConfirmStockUpdate = { updatedProducts ->
                    val currentProducts = state.products.associateBy { it.id }
                    updatedProducts.forEach { product ->
                        if (currentProducts[product.id] != product) {
                            if (product.deleted) Store.deleteProduct(product.id)
                            else Store.upsertProduct(product)
                        }
                    }
                    stockDraft = null
                    Planner.afterChange(ctx)
                },
                onSaveKibblePackage = {
                    Store.upsertKibblePackage(it)
                    Planner.afterChange(ctx)
                },
                onFinishKibblePackage = { pack ->
                    Store.upsertKibblePackage(pack.copy(finishedOn = LocalDate.now().toString()))
                    Planner.afterChange(ctx)
                },
                onDeleteKibblePackage = {
                    Store.deleteKibblePackage(it)
                    Planner.afterChange(ctx)
                },
            )
        }
    }

    if (showDiscardStockConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showDiscardStockConfirmation = false
                pendingStockExitAction = null
            },
            title = { Text("Modifications non enregistrées") },
            text = { Text("Le stock a été modifié. Quitter sans enregistrer ces changements ?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        stockDraft = null
                        managingStocks = false
                        showDiscardStockConfirmation = false
                        val onExit = pendingStockExitAction
                        pendingStockExitAction = null
                        onExit?.invoke()
                    },
                ) { Text("Quitter sans enregistrer") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardStockConfirmation = false
                        pendingStockExitAction = null
                    },
                ) { Text("Continuer") }
            },
        )
    }

    // ----- Fenêtres -----

    if (showNewPlan) {
        PlanDialog(
            cats = cats,
            products = state.products.filter { !it.deleted },
            foodPatterns = state.foodPatterns,
            stockSettings = state.stockSettings,
            initial = null,
            onSave = {
                Store.upsertPlan(it)
                Planner.afterChange(ctx)
                showNewPlan = false
            },
            onDelete = null,
            onDismiss = { showNewPlan = false },
        )
    }

    val planToEdit = state.plans.firstOrNull { it.id == editPlanId && !it.deleted }
    if (planToEdit != null) {
        PlanDialog(
            cats = cats,
            products = state.products.filter { !it.deleted },
            foodPatterns = state.foodPatterns,
            stockSettings = state.stockSettings,
            initial = planToEdit,
            onSave = {
                Store.upsertPlan(it)
                Planner.afterChange(ctx)
                editPlanId = null
            },
            onDelete = {
                Store.deletePlan(planToEdit.id)
                Planner.afterChange(ctx)
                editPlanId = null
            },
            onDismiss = { editPlanId = null },
        )
    }

    val entryToEncode = state.entries.firstOrNull { it.id == encodeId }
    if (entryToEncode != null) {
        EncodeDialog(
            entry = entryToEncode,
            cats = cats,
            products = state.products,
            foodTypes = state.foodTypes,
            onSave = { pate, qty, note, doneAt, catIds, photo, productId, foodTypeId ->
                Store.markDone(entryToEncode.id, pate, qty, note, doneAt, catIds, photo, productId, foodTypeId)
                Notifier.cancel(ctx, entryToEncode.id)
                Planner.afterChange(ctx)
                encodeId = null
            },
            onSkip = {
                Store.markSkipped(entryToEncode.id)
                Notifier.cancel(ctx, entryToEncode.id)
                Planner.afterChange(ctx)
                encodeId = null
            },
            onDelete = {
                Store.deleteEntry(entryToEncode.id)
                Notifier.cancel(ctx, entryToEncode.id)
                Planner.afterChange(ctx)
                encodeId = null
            },
            onDeletePermanently = {
                Store.permanentlyDeleteEntry(entryToEncode.id)
                Notifier.cancel(ctx, entryToEncode.id)
                Planner.afterChange(ctx)
                encodeId = null
            },
            onDismiss = { encodeId = null },
        )
    }

    if (showNewCat) {
        CatDialog(
            initial = null,
            onSave = {
                Store.upsertCat(it)
                Planner.afterChange(ctx)
                showNewCat = false
            },
            onDelete = null,
            onDismiss = { showNewCat = false },
        )
    }

    val catToEdit = cats.firstOrNull { it.id == editCatId }
    if (catToEdit != null) {
        CatDialog(
            initial = catToEdit,
            onSave = {
                Store.upsertCat(it)
                Planner.afterChange(ctx)
                editCatId = null
            },
            onDelete = if (cats.size > 1) {
                {
                    Store.deleteCat(catToEdit.id)
                    Planner.afterChange(ctx)
                    editCatId = null
                }
            } else null,
            onDismiss = { editCatId = null },
        )
    }

    if (showSettings) {
        SettingsDialog(
            syncStatus = syncStatus,
            updateMessage = updateStatus.message,
            checkingUpdates = updateStatus.checking,
            serverEnabled = serverEnabled,
            stockSettings = state.stockSettings,
            onCheckUpdates = { AppUpdater.check(ctx) },
            onSave = { url, token, enabled, stockSettings ->
                serverEnabled = enabled
                Prefs.saveServer(ctx, url, token, enabled)
                Store.upsertStockSettings(stockSettings)
                Planner.afterChange(ctx)
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    }
    val availableUpdate = updateStatus.release
    if (!showSettings && availableUpdate != null && dismissedUpdateTag != availableUpdate.tagName) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { dismissedUpdateTag = availableUpdate.tagName },
            title = { Text("Mise à jour disponible") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${availableUpdate.title} (v${availableUpdate.versionName})", fontWeight = FontWeight.Bold)
                    if (availableUpdate.notes.isNotBlank()) {
                        Text(availableUpdate.notes)
                    }
                    if (updateStatus.message.isNotBlank()) {
                        Text(updateStatus.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !updateStatus.downloading,
                    onClick = { AppUpdater.downloadAndInstall(ctx, availableUpdate) },
                ) {
                    Text(
                        when {
                            updateStatus.downloading -> "Téléchargement…"
                            updateStatus.downloaded -> "Installer la mise à jour"
                            else -> "Télécharger et installer"
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissedUpdateTag = availableUpdate.tagName }) { Text("Plus tard") }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// En-tête et petits éléments communs
// ---------------------------------------------------------------------------------------------

@Composable
fun AppHeader(serverEnabled: Boolean, syncStatus: String, onSync: () -> Unit, onSettings: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val isDisconnected = serverEnabled && syncStatus == "Serveur injoignable"
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.primaryContainer, c.background)))
    ) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(c.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_logo_mark),
                    contentDescription = null,
                    tint = c.onPrimary,
                    modifier = Modifier.size(29.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.onPrimaryContainer,
                )
                if (serverEnabled && syncStatus.isNotEmpty()) {
                    Text(
                        syncStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDisconnected) c.error else c.onSurfaceVariant,
                    )
                }
            }
            if (serverEnabled) {
                IconButton(onClick = onSync) {
                    Icon(
                        painterResource(if (isDisconnected) R.drawable.ic_action_wifi_off else R.drawable.ic_action_refresh),
                        contentDescription = if (isDisconnected) "Serveur déconnecté" else "Synchroniser",
                        tint = if (isDisconnected) c.error else c.onPrimaryContainer,
                    )
                }
            }
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_action_settings), contentDescription = "Réglages", tint = c.onPrimaryContainer)
            }
        }
    }
}

/** Un onglet de la barre du bas : icône + libellé, sans risque de débordement. */
@Composable
private fun RowScope.BottomNavItem(selected: Boolean, onClick: () -> Unit, icon: Int, label: String) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Icône + texte à l'intérieur d'un bouton. */
@Composable
private fun ButtonLabel(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

@Composable
private fun FoodCategoryLabel(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
fun EmptyState(icon: Int, text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun WarningCard(text: String, button: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
fun CatAvatar(cat: Cat, size: Dp) {
    val bitmap = remember(cat.photo) { cat.photo.takeIf { it.isNotEmpty() }?.let(::decodePhoto) }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(catColor(cat.color), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Text(
                cat.name.trim().take(1).uppercase().ifEmpty { "?" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp,
            )
        } else {
            PhotoImage(cat.photo, Modifier.fillMaxSize())
        }
    }
}

@Composable
fun CatsLine(cats: List<Cat>) {
    if (cats.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            cats.take(5).forEach { CatAvatar(it, 22.dp) }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            cats.joinToString(", ") { it.name },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun FoodIcon(
    kind: String,
    iconColor: String,
    fallback: String = pateEmoji(kind),
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resourceName = when (kind) {
        "poulet" -> "ic_food_chicken"
        "boeuf" -> "ic_food_beef"
        "saumon" -> "ic_food_salmon"
        else -> null
    }
    val iconId = remember(resourceName) {
        resourceName?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
    }
    val tint = foodIconColor(iconColor, MaterialTheme.colorScheme.primary)
    if (iconId != 0) {
        Icon(
            painterResource(iconId),
            contentDescription = null,
            modifier = modifier.size(size),
            tint = tint,
        )
    } else {
        Text(fallback, modifier = modifier, fontSize = (size.value * 0.9f).sp)
    }
}

fun foodIconColor(value: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)

@Composable
fun PateAvatar(key: String, emoji: String? = null, iconColor: String? = null) {
    val tint = foodIconColor(iconColor ?: defaultFoodIconColor(key), MaterialTheme.colorScheme.primary)
    Box(
        Modifier
            .size(46.dp)
            .background(tint.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        FoodIcon(key, iconColor ?: defaultFoodIconColor(key), emoji ?: pateEmoji(key), 24.dp)
    }
}

/** Carte d'un repas, utilisée partout (accueil, jour du calendrier, historique). */
@Composable
fun MealCard(
    occ: Occ,
    cats: List<Cat>,
    products: List<FoodProduct>,
    now: Long,
    onOpen: ((String) -> Unit)?,
    onSkip: ((String) -> Unit)?,
    compact: Boolean = false,
) {
    val e = occ.entry
    val pending = e != null && e.status == Status.PENDING
    val overdue = pending && occ.at <= now
    val done = e?.status == Status.DONE
    val skipped = e?.status == Status.SKIPPED
    val shownPate = if (done && e != null) e.givenPate.ifEmpty { occ.pate } else occ.pate
    val shownQty = if (done && e != null) e.givenQty else occ.qty
    val product = products.firstOrNull { it.id == occ.productId }
    val displayedQty = if (done && product != null &&
        Regex("""^\s*\d+(?:[.,]\d+)?\s*$""").matches(shownQty)
    ) {
        val quantityValue = shownQty.trim().replace(',', '.').toBigDecimalOrNull()
        val productWeight = product.weight.trim().replace(',', '.').toBigDecimalOrNull()
        if (quantityValue != null && productWeight != null && quantityValue.compareTo(productWeight) == 0) {
            "${shownQty.trim()} g"
        } else {
            shownQty
        }
    } else {
        shownQty
    }
    val shownFoodName = if (done && e != null) e.givenFoodTypeName.ifEmpty { occ.foodTypeName }
        else e?.foodTypeName?.ifEmpty { occ.foodTypeName } ?: occ.foodTypeName
    val shownFoodEmoji = if (done && e != null) e.givenFoodTypeEmoji.ifEmpty { occ.foodTypeEmoji }
        else e?.foodTypeEmoji?.ifEmpty { occ.foodTypeEmoji } ?: occ.foodTypeEmoji
    val displayedProductWeight = product?.weight?.takeIf { weight ->
        !done && weight.isNotBlank()
    }
    val who = cats.filter { it.id in occ.catIds }
    val allocationWarning = e?.foodAllocations?.let { allocations ->
        when {
            allocations.any { !it.inStock } -> "Stock insuffisant pour un ou plusieurs chats."
            allocations.any { it.lowStock } -> "Stock bas pour un ou plusieurs chats."
            allocations.any { it.stockWarning } -> "Stock à surveiller pour un ou plusieurs chats."
            else -> null
        }
    }

    val clickable = onOpen != null && e != null && !pending
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (overdue) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .then(if (clickable) Modifier.clickable { onOpen?.invoke(occ.id) } else Modifier),
    ) {
        if (compact) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    PateAvatar(
                        product?.pateKey ?: shownPate,
                        shownFoodEmoji.ifEmpty { null },
                        product?.iconColor,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1.1f)) {
                        Text(
                            "${Fmt.date(occ.at)} · ${Fmt.time(occ.at)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (who.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            CatsLine(who)
                        }
                    }
                }
                Text(
                    (product?.let { "${it.name} · ${it.packagingLabel.lowercase()}" }
                        ?: shownFoodName.takeIf { it.isNotBlank() }?.let { name ->
                            "${shownFoodEmoji.ifEmpty { pateEmoji(shownPate) }} $name"
                        }
                        ?: pateLabel(shownPate)) +
                        if (displayedQty.isNotBlank()) " · $displayedQty" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (e != null && e.note.isNotBlank()) {
                    Text(e.note, style = MaterialTheme.typography.bodyMedium)
                }
                if (e != null && e.photo.isNotEmpty()) {
                    PhotoImage(
                        e.photo,
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                }
                if (pending && e != null && onOpen != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (onSkip != null) {
                            TextButton(onClick = { onSkip(e.id) }, modifier = Modifier.height(48.dp)) {
                                Text("Passer")
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { onOpen(e.id) }, modifier = Modifier.height(48.dp)) {
                            Text(if (e.deleted) "Encoder à nouveau" else if (overdue) "Encoder" else "Donné")
                        }
                    }
                }
            }
        } else {
            Row(Modifier.padding(14.dp)) {
                PateAvatar(
                    product?.pateKey ?: shownPate,
                    shownFoodEmoji.ifEmpty { null },
                    product?.iconColor,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${Fmt.date(occ.at)} · ${Fmt.time(occ.at)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        (product?.let {
                            "${it.name} · ${it.brand} · ${it.packPrice} €/${it.packagingLabel.lowercase()}" +
                                displayedProductWeight?.let { weight -> " · $weight g" }.orEmpty()
                        }
                            ?: shownFoodName.takeIf { it.isNotBlank() }?.let { name ->
                                "${shownFoodEmoji.ifEmpty { pateEmoji(shownPate) }} $name"
                            }
                            ?: pateLabel(shownPate)) + if (displayedQty.isNotBlank()) " · $displayedQty" else "",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (who.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        CatsLine(who)
                    }
                    if (pending && allocationWarning != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            allocationWarning,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (allocationWarning.startsWith("Stock à surveiller")) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                    if (done && e != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "✓ Donné à ${Fmt.time(e.doneAt)}" +
                                if (e.givenPate.isNotEmpty() && e.givenPate != e.pate)
                                    " (prévu : ${e.foodTypeName.ifEmpty { pateLabel(e.pate) }})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = DoneColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (skipped) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "⏭ Repas passé (rien donné)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (e?.deleted == true) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Encodage supprimé · tu peux le saisir à nouveau",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (e != null && e.note.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(e.note, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (e != null && e.photo.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        PhotoImage(
                            e.photo,
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(MaterialTheme.shapes.medium),
                        )
                    }
                    if (pending && e != null && onOpen != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onOpen(e.id) }, modifier = Modifier.height(48.dp)) {
                                Text(if (e.deleted) "Encoder à nouveau" else if (overdue) "Encoder" else "Donné")
                            }
                            if (onSkip != null) {
                                TextButton(onClick = { onSkip(e.id) }, modifier = Modifier.height(48.dp)) {
                                    Text("Passer")
                                }
                            }
                        }
                        }
                    }
            }
        }
}
}

// ---------------------------------------------------------------------------------------------
// Accueil : calendrier + totaux du mois + prochains repas
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(
    state: AppState,
    cats: List<Cat>,
    products: List<FoodProduct>,
    exactOk: Boolean,
    notifOk: Boolean,
    modifier: Modifier,
    onOpen: (String) -> Unit,
    onSkip: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val now by rememberNow()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var selectedCatId by remember { mutableStateOf<String?>(null) }
    val activeSelectedCatId = selectedCatId?.takeIf { id -> cats.any { it.id == id } }

    val minute = now / 60_000
    val monthOccs = remember(state, month, minute) {
        Planner.occurrences(state, month.atDay(1), month.atEndOfMonth(), now)
    }
    val calendarOccs = remember(monthOccs, activeSelectedCatId) {
        monthOccs.filter { activeSelectedCatId == null || activeSelectedCatId in it.catIds }
    }
    val byDay = remember(calendarOccs) {
        calendarOccs.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
    }
    val noFoodDates = remember(state, month, activeSelectedCatId, minute) {
        val depletionDates = Planner.stockDepletionDates(
            state,
            today,
            month.atEndOfMonth(),
            now,
        )
        val catsWithPlans = state.plans
            .filter { !it.deleted && it.active }
            .flatMap { it.catIds }
            .distinct()
            .filter { catId -> cats.any { it.id == catId } }
            .filter { activeSelectedCatId == null || it == activeSelectedCatId }
        val depletionDatesToConsider = catsWithPlans.mapNotNull { depletionDates[it] }
        if (catsWithPlans.isEmpty() || depletionDatesToConsider.size != catsWithPlans.size) {
            emptySet()
        } else {
            (1..month.lengthOfMonth())
                .map { month.atDay(it) }
                .filter { date -> depletionDatesToConsider.all { !date.isBefore(it) } }
                .toSet()
        }
    }
    val overdue = remember(state, minute) {
        state.entries
            .filter { !it.deleted && it.status == Status.PENDING && it.at <= now }
            .sortedBy { it.at }
            .map { it.toOcc() }
    }
    val upcoming = remember(state, minute) {
        Planner.occurrences(state, today, today.plusDays(30), now)
            .filter {
                it.at > now && (it.entry == null || it.entry.status == Status.PENDING) &&
                    (activeSelectedCatId == null || activeSelectedCatId in it.catIds)
            }
            .take(8)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!notifOk) {
            item {
                WarningCard(
                    text = "Les notifications sont désactivées : tu ne seras pas prévenu à l'heure des repas.",
                    button = "Les activer",
                    onClick = { openNotificationSettings(ctx) },
                )
            }
        }
        if (!exactOk) {
            item {
                WarningCard(
                    text = "Pour que l'alarme sonne pile à l'heure, autorise les « alarmes et rappels ».",
                    button = "Autoriser",
                    onClick = { openExactAlarmSettings(ctx) },
                )
            }
        }

        if (overdue.isNotEmpty()) {
            item { SectionTitle("À encoder") }
            items(overdue, key = { "o_" + it.id }) { o ->
                MealCard(o, cats, products, now, onOpen, onSkip)
            }
        }

        item {
            CalendarCard(
                month = month,
                today = today,
                byDay = byDay,
                products = products,
                noFoodDates = noFoodDates,
                selected = selected,
                cats = cats,
                selectedCatId = activeSelectedCatId,
                onCatSelected = { selectedCatId = it },
                now = now,
                onPrev = { month = month.minusMonths(1); selected = null },
                onNext = { month = month.plusMonths(1); selected = null },
                onToday = { month = YearMonth.now(); selected = today },
                onSelect = { d -> selected = if (selected == d) null else d },
            )
        }

        item {
            val total = calendarOccs.size
            val passed = calendarOccs.count { it.at <= now }
            val given = calendarOccs.count { it.entry?.status == Status.DONE }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Prévus", total.toString(), "ce mois-ci", Modifier.weight(1f))
                StatTile("Passés", passed.toString(), "dont $given donnés", Modifier.weight(1f))
                StatTile("À venir", (total - passed).toString(), "à prévoir", Modifier.weight(1f))
            }
        }

        val day = selected
        if (day != null) {
            item { SectionTitle(Fmt.dayFull(day)) }
            val list = byDay[day].orEmpty()
            if (list.isEmpty()) {
                item { EmptyState(R.drawable.ic_nav_cats, "Aucun repas ce jour-là.") }
            } else {
                items(list, key = { "d_" + it.id }) { o ->
                    MealCard(o, cats, products, now, onOpen, onSkip)
                }
            }
        } else {
            item { SectionTitle("Prochains repas") }
            if (upcoming.isEmpty()) {
                item {
                    EmptyState(
                        R.drawable.ic_nav_programs,
                        "Aucun repas programmé pour le moment.\nVa dans « Programmes » pour en créer un."
                    )
                }
            } else {
                items(upcoming, key = { "u_" + it.id }) { o ->
                    MealCard(o, cats, products, now, onOpen, onSkip, compact = true)
                }
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, sub: String, modifier: Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.height(96.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val WeekDays = listOf("L", "M", "M", "J", "V", "S", "D")

@Composable
fun CalendarCard(
    month: YearMonth,
    today: LocalDate,
    byDay: Map<LocalDate, List<Occ>>,
    products: List<FoodProduct>,
    noFoodDates: Set<LocalDate>,
    selected: LocalDate?,
    cats: List<Cat>,
    selectedCatId: String?,
    onCatSelected: (String?) -> Unit,
    now: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) {
                    Icon(painterResource(R.drawable.ic_action_chevron_left), contentDescription = "Mois précédent")
                }
                Text(
                    Fmt.monthTitle(month),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToday() },
                )
                IconButton(onClick = onNext) {
                    Icon(painterResource(R.drawable.ic_action_chevron_right), contentDescription = "Mois suivant")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                WeekDays.forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = c.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (cats.size > 1) ChatFilterDropdown(cats, selectedCatId, onCatSelected)
                Spacer(Modifier.weight(1f))
                if (month != YearMonth.from(today) || selected != today) {
                    TextButton(onClick = onToday) { Text("Aujourd'hui") }
                }
            }
            Spacer(Modifier.height(4.dp))

            val first = month.atDay(1)
            val offset = first.dayOfWeek.value - 1
            val days = month.lengthOfMonth()
            val rows = (offset + days + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val n = r * 7 + col - offset + 1
                        Box(
                            Modifier
                                .weight(1f)
                                .height(54.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (n in 1..days) {
                                val date = month.atDay(n)
                                DayCell(
                                    date,
                                    today,
                                    selected,
                                    byDay[date].orEmpty(),
                                    products,
                                    date in noFoodDates,
                                    now,
                                    onSelect,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendDot(DoneColor, "donné")
                Spacer(Modifier.width(14.dp))
                LegendDot(c.error, "à encoder")
                Spacer(Modifier.width(14.dp))
                LegendDot(c.primary.copy(alpha = 0.55f), "prévu")
                Spacer(Modifier.width(14.dp))
                LegendDot(c.outlineVariant, "stock épuisé")
            }
        }
    }
}

@Composable
private fun ChatFilterDropdown(
    cats: List<Cat>,
    selectedCatId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = cats.firstOrNull { it.id == selectedCatId }?.name ?: "Tous les chats"
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Afficher : $selectedName")
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.ic_action_chevron_down), contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Tous les chats") },
                onClick = { onSelect(null); expanded = false },
            )
            cats.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    onClick = { onSelect(cat.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    today: LocalDate,
    selected: LocalDate?,
    occs: List<Occ>,
    products: List<FoodProduct>,
    noFood: Boolean,
    now: Long,
    onSelect: (LocalDate) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val isToday = date == today
    val isSel = date == selected
    var m = Modifier
        .size(width = 42.dp, height = 52.dp)
        .clip(RoundedCornerShape(14.dp))
    if (noFood) m = m.background(c.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
    else if (isSel) m = m.background(c.primaryContainer)
    if (noFood && isSel) m = m.border(1.5.dp, c.primary, RoundedCornerShape(14.dp))
    if (isToday) m = m.border(1.5.dp, c.primary, RoundedCornerShape(14.dp))
    m = m.clickable { onSelect(date) }

    Column(
        m,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSel -> c.onPrimaryContainer
                noFood -> c.onSurfaceVariant.copy(alpha = 0.65f)
                else -> c.onSurface
            },
        )
        Spacer(Modifier.height(3.dp))
        if (occs.isNotEmpty()) {
            val color = when {
                noFood -> c.outlineVariant
                occs.any { it.entry?.status == Status.PENDING && it.at <= now } -> c.error
                occs.all { it.entry?.status == Status.DONE } -> DoneColor
                occs.all { it.entry?.status == Status.SKIPPED } -> c.onSurfaceVariant.copy(alpha = 0.35f)
                else -> c.primary.copy(alpha = 0.65f)
            }
            Box(
                Modifier
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (occs.size == 1) {
                    val meal = occs.first()
                    val emoji = meal.entry?.takeIf { it.status == Status.DONE }
                        ?.givenFoodTypeEmoji?.ifEmpty { meal.foodTypeEmoji } ?: meal.foodTypeEmoji
                    val productId = meal.entry?.productId?.takeIf { it.isNotBlank() }
                        ?: meal.entry?.foodAllocations?.firstOrNull()?.productId?.takeIf { it.isNotBlank() }
                        ?: meal.productId
                    val product = products.firstOrNull { it.id == productId }
                    Box(
                        Modifier
                            .size(16.dp)
                            .background(c.surface, CircleShape)
                            .border(1.dp, c.onSurface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        FoodIcon(
                            product?.pateKey ?: meal.pate,
                            product?.iconColor ?: defaultFoodIconColor(meal.pate),
                            emoji.ifEmpty { pateEmoji(meal.pate) },
                            11.dp,
                        )
                    }
                } else {
                    Text(
                        occs.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
        } else Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Onglet "Programmes"
// ---------------------------------------------------------------------------------------------

@Composable
fun PlansScreen(
    plans: List<Plan>,
    cats: List<Cat>,
    products: List<FoodProduct>,
    foodPatterns: List<FoodPattern>,
    modifier: Modifier,
    onEdit: (String) -> Unit,
    onToggle: (Plan) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (plans.isEmpty()) {
            item {
                EmptyState(
                    R.drawable.ic_nav_programs,
                    "Aucun programme.\nAppuie sur « Programme » pour créer par exemple :\n« tous les jours à 8h00, pâtée au poulet »."
                )
            }
        }
        items(plans, key = { it.id }) { p ->
            Card(
                onClick = { onEdit(p.id) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val effectivePatternId = p.patternId.ifEmpty {
                        if (!p.alternatePate) "" else if (p.pate == Pate.BOEUF.key) "boeuf-poulet" else "poulet-boeuf"
                    }
                    val pattern = foodPatterns.firstOrNull { it.id == effectivePatternId && !it.deleted }
                    val firstFood = pattern?.foodIds?.firstNotNullOfOrNull { id ->
                        products.firstOrNull { it.id == id && !it.deleted }
                    }
                    val firstTypeId = firstFood?.pateKey ?: p.pate
                    PateAvatar(firstTypeId)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${Fmt.every(p.everyDays)} à ${Fmt.timeMin(p.timeMin)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            (products.firstOrNull { it.id == p.productId }?.let { product ->
                                listOfNotNull(
                                    product.name,
                                    product.brand.takeIf { it.isNotBlank() },
                                    product.packPrice.takeIf { it.isNotBlank() }
                                        ?.let { "$it €/${product.packagingLabel.lowercase()}" },
                                    product.weight.takeIf { it.isNotBlank() }?.let { "$it g" },
                                    "${product.servingsPerPackage} portion(s)/emballage",
                                    product.catIds.takeIf { it.isNotEmpty() }?.let { ids ->
                                        "Réservé à " + ids.mapNotNull { id ->
                                            cats.firstOrNull { it.id == id }?.name
                                        }.joinToString(", ")
                                    },
                                ).joinToString(" · ")
                            } ?: pattern?.let { foodPattern ->
                                foodPattern.foodIds.mapNotNull { id ->
                                    products.firstOrNull { it.id == id && !it.deleted }?.name
                                }.joinToString(" → ").ifEmpty { foodPattern.name }
                            }
                                ?: if (p.alternatePate) {
                                val nextPate = if (p.pate == Pate.BOEUF.key) Pate.POULET.key else Pate.BOEUF.key
                                "${pateLabel(p.pate)} → ${pateLabel(nextPate)}"
                            } else pateLabel(p.pate)) +
                                if (p.qty.isNotBlank()) " · ${p.qty}" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val who = cats.filter { it.id in p.catIds }
                        if (who.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            CatsLine(who)
                        }
                        val start = runCatching { LocalDate.parse(p.start) }.getOrNull()
                        if (start != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "À partir du ${Fmt.dateLong(start)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(checked = p.active, onCheckedChange = { onToggle(p) })
                }
            }
        }
    }
}

@Composable
private fun FoodManagementScreen(
    products: List<FoodProduct>,
    kibblePackages: List<KibblePackage>,
    cats: List<Cat>,
    stockSettings: StockSettings,
    modifier: Modifier,
    stockDraft: List<FoodProduct>?,
    managingStocks: Boolean,
    onManagingStocksChange: (Boolean) -> Unit,
    onRequestStockExit: (() -> Unit) -> Unit,
    onStockDraftChange: (List<FoodProduct>) -> Unit,
    onConfirmStockUpdate: (List<FoodProduct>) -> Unit,
    onSaveKibblePackage: (KibblePackage) -> Unit,
    onFinishKibblePackage: (KibblePackage) -> Unit,
    onDeleteKibblePackage: (String) -> Unit,
) {
    var category by remember { mutableIntStateOf(0) }
    var showProductEditor by remember { mutableStateOf(false) }
    var editingProductId by remember { mutableStateOf<String?>(null) }
    var showSachetBoxDialog by remember { mutableStateOf(false) }
    var showKibbleEditor by remember { mutableStateOf(false) }
    var editingKibbleId by remember { mutableStateOf<String?>(null) }
    var deleteKibbleId by remember { mutableStateOf<String?>(null) }
    val draftProducts = stockDraft ?: products
    val hasStockChanges = draftProducts.map { it.copy(updatedAt = 0L) } !=
        products.map { it.copy(updatedAt = 0L) }
    val visibleDraftProducts = draftProducts.filterNot { it.deleted }
        .sortedWith(
            compareBy<FoodProduct> { product ->
                products.firstOrNull { it.id == product.id }?.putAside ?: product.putAside
            }.thenBy { product ->
                products.indexOfFirst { it.id == product.id }.takeIf { it >= 0 } ?: Int.MAX_VALUE
            }.thenBy { it.name.lowercase() }
        )
    val orderedProducts = products.sortedWith(compareBy<FoodProduct> { it.putAside }.thenBy { it.name.lowercase() })

    val updateDraftProduct: (FoodProduct) -> Unit = { updated ->
        onStockDraftChange(
            if (draftProducts.any { it.id == updated.id }) {
                draftProducts.map { if (it.id == updated.id) updated else it }
            } else {
                draftProducts + updated
            }
        )
    }

    BackHandler(enabled = category != 0 || managingStocks) {
        if (category != 0) category = 0 else onRequestStockExit { onManagingStocksChange(false) }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Alimentation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Nourriture", "Croquettes").forEachIndexed { index, label ->
                val selected = if (index == 0) category != 2 else category == 2
                val target = if (index == 0) 0 else 2
                val icon = R.drawable.ic_nav_food
                val selectCategory = {
                    if (target != category && managingStocks) {
                        onRequestStockExit { category = target }
                    } else {
                        category = target
                    }
                }
                if (selected) {
                    Button(
                        onClick = selectCategory,
                        modifier = Modifier.weight(1f).height(52.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { FoodCategoryLabel(icon, label) }
                } else {
                    OutlinedButton(
                        onClick = selectCategory,
                        modifier = Modifier.weight(1f).height(52.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { FoodCategoryLabel(icon, label) }
                }
            }
        }
        if (category == 0) {
            Button(
                onClick = {
                    if (managingStocks) {
                        onRequestStockExit { onManagingStocksChange(false) }
                    } else {
                        onManagingStocksChange(true)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(if (managingStocks) "Voir les stocks" else "Gérer les stocks")
            }
        }

        if (category == 0 && !managingStocks) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (products.isEmpty()) {
                    item { EmptyState(R.drawable.ic_nav_food, "Aucun stock à afficher.") }
                }
                items(orderedProducts, key = { it.id }) { product ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (product.putAside) Color(0xFF424242)
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Column(
                                Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FoodIcon(product.pateKey, product.iconColor, size = 20.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        product.name + if (product.putAside) " · mis de côté" else "",
                                        fontWeight = FontWeight.Bold,
                                        color = if (product.putAside) Color(0xFFE0E0E0)
                                            else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Text(
                                    listOfNotNull(
                                        product.brand.takeIf { it.isNotBlank() },
                                        product.packPrice.takeIf { it.isNotBlank() }
                                            ?.let { "$it €/${product.packagingLabel.lowercase()}" },
                                        product.weight.takeIf { it.isNotBlank() }?.let { "$it g" },
                                    ).joinToString(" · ").ifEmpty { "Nourriture de base" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (product.putAside) Color(0xFFBDBDBD)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val stock = product.stockCount
                                val portions = product.availableServings
                                if (stock == null) {
                                    Text(
                                        "Stock non suivi",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (product.putAside) Color(0xFFBDBDBD)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    val darkTheme = isSystemInDarkTheme()
                                    val availablePortions = portions ?: 0L
                                    val stockColor = if (product.putAside) Color(0xFFBDBDBD) else when {
                                        availablePortions <= stockSettings.lowThreshold ->
                                            if (darkTheme) Color(0xFFFF5252) else Color(0xFFB71C1C)
                                        availablePortions <= stockSettings.warningThreshold -> Color(0xFFE58A24)
                                        else -> Color(0xFF438A55)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Box(
                                            Modifier
                                                .size(10.dp)
                                                .background(stockColor, CircleShape),
                                        )
                                        Text(
                                            (if (product.packaging == "sachet") {
                                                "$stock ${if (stock == 1) "sachet" else "sachets"}"
                                            } else {
                                                "$availablePortions " +
                                                    (if (availablePortions == 1L) "portion" else "portions") +
                                                    " · $stock ${product.packagingLabel.lowercase()}(s) fermé(s)"
                                            }) +
                                                (if (product.servingsPerPackage > 1 && product.openServings > 0) {
                                                    " · emballage entamé (${product.openServings} portion(s))"
                                                } else "") +
                                                " · " +
                                                if (product.putAside) "mis de côté" else when {
                                                    availablePortions <= stockSettings.lowThreshold -> "stock bas"
                                                    availablePortions <= stockSettings.warningThreshold -> "à surveiller"
                                                    else -> "stock bon"
                                                },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = stockColor,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                }
                            }
                        }
                    }
                }
            }
        } else if (category == 2) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "Enregistre un paquet ouvert ou ajoute-le en attente pour le commencer plus tard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { showKibbleEditor = true }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Ajouter un paquet")
                    }
                }
                if (kibblePackages.isEmpty()) {
                    item { EmptyState(R.drawable.ic_nav_food, "Aucun paquet de croquettes enregistré.") }
                }
                items(kibblePackages.sortedByDescending { it.addedOn }, key = { it.id }) { pack ->
                    val addedOn = runCatching { LocalDate.parse(pack.addedOn) }.getOrNull()
                    val finishedOn = runCatching { LocalDate.parse(pack.finishedOn) }.getOrNull()
                    val durationEnd = finishedOn ?: LocalDate.now()
                    val duration = addedOn?.let {
                        if (durationEnd.isBefore(it)) 0 else (ChronoUnit.DAYS.between(it, durationEnd) + 1).toInt()
                    }
                    val notStarted = addedOn == null
                    val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("fr-BE"))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    listOf(pack.brand.ifBlank { "Paquet de croquettes" }, pack.flavor, pack.specialty)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    currency.format(pack.price.replace(',', '.').toDoubleOrNull() ?: 0.0),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                "Début : ${addedOn?.let { Fmt.dateLong(it) } ?: "À commencer"} · " +
                                    "Durée : " + if (notStarted) "En attente"
                                    else "${duration ?: "?"} jour${if (duration == 1) "" else "s"}${if (finishedOn == null) " · en cours" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            CatsLine(cats.filter { it.id in pack.catIds })
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (notStarted) {
                                            onSaveKibblePackage(
                                                pack.copy(
                                                    addedOn = LocalDate.now().toString(),
                                                    updatedAt = System.currentTimeMillis(),
                                                ),
                                            )
                                        } else if (finishedOn == null) {
                                            onFinishKibblePackage(pack)
                                        }
                                    },
                                    enabled = notStarted || finishedOn == null,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                ) {
                                    Text(
                                        when {
                                            notStarted -> "Commencer"
                                            finishedOn == null -> "Terminer"
                                            else -> "Terminé"
                                        },
                                        maxLines = 1,
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        editingKibbleId = pack.id
                                        showKibbleEditor = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                ) { Text("Modifier", maxLines = 1) }
                                OutlinedButton(
                                    onClick = { deleteKibbleId = pack.id },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_action_delete),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Supprimer", maxLines = 1, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        } else {
                LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                item {
                                    Text(
                                        "Ajuste les quantités, ajoute une nourriture ou mets-en de côté. Confirme pour recalculer une seule fois les prochains repas.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Button(
                                            onClick = {
                                                editingProductId = null
                                                showProductEditor = true
                                            },
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                        ) { Text("Ajouter nourriture", maxLines = 1) }
                                        OutlinedButton(
                                            onClick = { showSachetBoxDialog = true },
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                        ) { Text("Boîte de sachets", maxLines = 1) }
                                    }
                                    Button(
                                        onClick = {
                                            onConfirmStockUpdate(draftProducts)
                                            onManagingStocksChange(false)
                                        },
                                        enabled = hasStockChanges,
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(40.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                    ) { Text("Actualiser le stock", maxLines = 1) }
                                }
                                if (visibleDraftProducts.isEmpty()) {
                                    item { EmptyState(R.drawable.ic_nav_food, "Aucune nourriture enregistrée.") }
                                }
                                items(visibleDraftProducts, key = { it.id }) { product ->
                                    val available = product.availableServings ?: 0L
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (product.putAside) Color(0xFF424242)
                                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        ),
                                        shape = MaterialTheme.shapes.large,
                                    ) {
                                        Column(
                                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(
                                                    Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        FoodIcon(product.pateKey, product.iconColor, size = 20.dp)
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            product.name,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (product.putAside) Color(0xFFE0E0E0)
                                                                else MaterialTheme.colorScheme.onSurface,
                                                        )
                                                    }
                                                    Text(
                                                        if (product.stockCount == null) "Stock non suivi"
                                                        else (if (product.packaging == "sachet") {
                                                            "${product.stockCount} " +
                                                                if (product.stockCount == 1) "sachet" else "sachets"
                                                        } else {
                                                            "$available portion(s) · ${product.stockCount} emballage(s) fermé(s)"
                                                        }) + if (product.servingsPerPackage > 1 && product.openServings > 0) {
                                                            " · emballage entamé (${product.openServings} portion(s))"
                                                        } else "",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (product.putAside) Color(0xFFBDBDBD)
                                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(0.dp),
                                                ) {
                                                    TextButton(
                                                        onClick = {
                                                            updateDraftProduct(
                                                                product.withStockPortionDelta(1, System.currentTimeMillis())
                                                            )
                                                        },
                                                        enabled = product.stockCount != null &&
                                                            available < Int.MAX_VALUE.toLong() * product.servingsPerPackage,
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(32.dp),
                                                    ) { Text("+", fontSize = 20.sp) }
                                                    TextButton(
                                                        onClick = {
                                                            updateDraftProduct(
                                                                product.withStockPortionDelta(-1, System.currentTimeMillis())
                                                            )
                                                        },
                                                        enabled = product.stockCount != null && available > 0,
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(32.dp),
                                                    ) { Text("−", fontSize = 20.sp) }
                                                }
                                            }
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        editingProductId = product.id
                                                        showProductEditor = true
                                                    },
                                                    modifier = Modifier.size(36.dp),
                                                ) {
                                                    Icon(
                                                        painterResource(R.drawable.ic_action_settings),
                                                        contentDescription = "Modifier ${product.name}",
                                                        tint = if (product.putAside) Color(0xFFBDBDBD)
                                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                                if (product.stockCount == null) {
                                                    TextButton(
                                                        onClick = {
                                                            editingProductId = product.id
                                                            showProductEditor = true
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(36.dp),
                                                    ) {
                                                        Text("Configurer le stock", maxLines = 1)
                                                    }
                                                }
                                                Spacer(Modifier.weight(1f))
                                                Text(
                                                    "Mettre de côté",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (product.putAside) Color(0xFFBDBDBD)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Switch(
                                                    checked = product.putAside,
                                                    onCheckedChange = { putAside ->
                                                        updateDraftProduct(
                                                            product.copy(
                                                                putAside = putAside,
                                                                updatedAt = System.currentTimeMillis(),
                                                            )
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .width(38.dp)
                                                        .height(30.dp)
                                                        .graphicsLayer(scaleX = 0.72f, scaleY = 0.72f),
                                                )
                                            }
                                            if (product.putAside &&
                                                products.firstOrNull { it.id == product.id }?.putAside != true
                                            ) {
                                                Text(
                                                    "Sera grisé et déplacé en bas après confirmation.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFBDBDBD),
                                                )
                                            }
                                        }
                    }
                }
            }
        }
    }

    if (showProductEditor) {
        ProductCatalogDialog(
            products = draftProducts,
            cats = cats,
            stockSettings = stockSettings,
            initialProductId = editingProductId,
            onSave = updateDraftProduct,
            onDelete = { id ->
                draftProducts.firstOrNull { it.id == id }?.let {
                    updateDraftProduct(it.copy(deleted = true, updatedAt = System.currentTimeMillis()))
                }
            },
            onDismiss = {
                showProductEditor = false
                editingProductId = null
            },
        )
    }
    if (showSachetBoxDialog) {
        AddSachetBoxDialog(
            products = draftProducts,
            onAdd = { updates ->
                onStockDraftChange(
                    draftProducts.map { current ->
                        updates.firstOrNull { it.id == current.id } ?: current
                    } + updates.filter { added -> draftProducts.none { it.id == added.id } }
                )
            },
            onDismiss = { showSachetBoxDialog = false },
        )
    }
    if (showKibbleEditor) {
        val editingPackage = kibblePackages.firstOrNull { it.id == editingKibbleId }
        KibblePackageDialog(
            cats = cats,
            initial = editingPackage,
            onSave = onSaveKibblePackage,
            onDismiss = {
                showKibbleEditor = false
                editingKibbleId = null
            },
        )
    }
    val packageToDelete = kibblePackages.firstOrNull { it.id == deleteKibbleId }
    if (packageToDelete != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteKibbleId = null },
            title = { Text("Supprimer ce paquet ?") },
            text = { Text("Son coût ne sera plus compté dans les statistiques des chats.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteKibblePackage(packageToDelete.id)
                    deleteKibbleId = null
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteKibbleId = null }) { Text("Annuler") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Onglet "Chats" et détail d'un profil
// ---------------------------------------------------------------------------------------------

private data class FoodQuantity(val amount: Double, val isGrams: Boolean, val isPortions: Boolean = false)
private data class MonthlyFoodCost(
    val mealAmount: Double,
    val kibbleAmount: Double,
    val unpricedMeals: Int,
    val planDetails: List<PlanCostDetail>,
) {
    val amount: Double get() = mealAmount + kibbleAmount
}

private data class PlanCostDetail(
    val timeMin: Int,
    val everyDays: Int,
    val quantity: String,
    val meals: Int,
    val amount: Double,
)

private fun parseFoodQuantity(value: String, product: FoodProduct?): FoodQuantity? {
    val match = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(g|gr|grammes?|sachets?|portions?)?\b""", RegexOption.IGNORE_CASE)
        .find(value) ?: return null
    val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 } ?: return null
    val unit = match.groupValues[2].lowercase(Locale.ROOT)
    val productWeight = product?.weight?.replace(',', '.')?.toDoubleOrNull()
    return when {
        unit.startsWith("g") -> FoodQuantity(amount, true)
        unit.startsWith("portion") -> FoodQuantity(amount, false, true)
        unit.isNotEmpty() -> FoodQuantity(amount, false)
        productWeight != null && kotlin.math.abs(amount - productWeight) < 0.0001 -> FoodQuantity(amount, true)
        else -> FoodQuantity(amount, false)
    }
}

private fun gramsFor(quantity: String, product: FoodProduct?): Double? {
    val parsed = parseFoodQuantity(quantity, product) ?: return null
    if (parsed.isGrams) return parsed.amount
    val weight = product?.weight?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
    val portionWeight = if (parsed.isPortions) {
        weight / product.servingsPerPackage.coerceAtLeast(1)
    } else {
        weight
    }
    return parsed.amount * portionWeight
}

private fun costFor(quantity: String, product: FoodProduct?): Double? {
    val parsed = parseFoodQuantity(quantity, product) ?: return null
    val price = product?.packPrice?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it >= 0 } ?: return null
    if (parsed.isPortions) {
        val servings = product?.servingsPerPackage?.coerceAtLeast(1) ?: return null
        return parsed.amount * price / servings
    }
    if (!parsed.isGrams) return parsed.amount * price
    val weight = product.weight.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return null
    return parsed.amount / weight * price
}

private fun monthlyFoodCost(
    catId: String,
    plans: List<Plan>,
    patterns: List<FoodPattern>,
    products: List<FoodProduct>,
    kibblePackages: List<KibblePackage>,
    month: YearMonth,
): MonthlyFoodCost {
    var mealAmount = 0.0
    var kibbleAmount = 0.0
    var unpriced = 0
    val planDetails = mutableListOf<PlanCostDetail>()
    val firstDay = month.atDay(1)
    val lastDay = month.atEndOfMonth()
    for (plan in plans) {
        if (!plan.active || plan.deleted || catId !in plan.catIds) continue
        val start = runCatching { LocalDate.parse(plan.start) }.getOrNull() ?: continue
        var date = if (start.isAfter(firstDay)) start else firstDay
        var mealCount = 0
        var planAmount = 0.0
        while (!date.isAfter(lastDay)) {
            if (!date.isBefore(start) && ChronoUnit.DAYS.between(start, date) % plan.everyDays.coerceAtLeast(1) == 0L) {
                mealCount++
                val product = if (plan.autoAllocate) {
                    val restricted = products.filter { !it.deleted && !it.putAside && catId in it.catIds }
                    val eligible = if (restricted.isNotEmpty()) restricted else {
                        products.filter { !it.deleted && !it.putAside && it.catIds.isEmpty() }
                    }
                    eligible.filter { it.availableServings != null }
                        .maxByOrNull { it.availableServings ?: 0L }
                } else {
                    plan.foodProductOn(date, patterns, products)
                }
                val mealCost = if (plan.autoAllocate) {
                    product?.let { item ->
                        item.packPrice.replace(',', '.').toDoubleOrNull()
                            ?.div(item.servingsPerPackage.coerceAtLeast(1))
                    }
                } else {
                    costFor(plan.qty, product)
                }
                if (mealCost == null) {
                    unpriced++
                } else {
                    val catShare = if (plan.autoAllocate) mealCost else {
                        mealCost / plan.catIds.distinct().size.coerceAtLeast(1)
                    }
                    mealAmount += catShare
                    planAmount += catShare
                }
            }
            date = date.plusDays(1)
        }
        if (mealCount > 0) {
            planDetails += PlanCostDetail(plan.timeMin, plan.everyDays, plan.qty, mealCount, planAmount)
        }
    }
    val today = LocalDate.now()
    for (pack in kibblePackages) {
        val sharedBy = pack.catIds.distinct().size
        if (catId !in pack.catIds || sharedBy == 0) continue
        val price = pack.price.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 } ?: continue
        val start = runCatching { LocalDate.parse(pack.addedOn) }.getOrNull() ?: continue
        val finish = runCatching { LocalDate.parse(pack.finishedOn) }.getOrNull() ?: today
        val end = if (finish.isAfter(today)) today else finish
        if (end.isBefore(start)) continue
        val duration = ChronoUnit.DAYS.between(start, end) + 1
        val overlapStart = if (start.isAfter(firstDay)) start else firstDay
        val overlapEnd = if (end.isBefore(lastDay)) end else lastDay
        if (!overlapEnd.isBefore(overlapStart)) {
            val daysInMonth = ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1
            kibbleAmount += price * daysInMonth / duration / sharedBy
        }
    }
    return MonthlyFoodCost(mealAmount, kibbleAmount, unpriced, planDetails)
}

private fun formatGrams(grams: Double): String = if (grams >= 1000) {
    String.format(Locale.FRANCE, "%.2f kg", grams / 1000).replace(",00", "")
} else {
    "${grams.toLong()} g"
}

@Composable
fun CatsScreen(
    cats: List<Cat>,
    plans: List<Plan>,
    entries: List<Entry>,
    products: List<FoodProduct>,
    foodPatterns: List<FoodPattern>,
    kibblePackages: List<KibblePackage>,
    selectedCatId: String?,
    modifier: Modifier,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val selectedCat = cats.firstOrNull { it.id == selectedCatId }
    if (selectedCat == null) {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cats, key = { it.id }) { cat ->
                val count = plans.count { cat.id in it.catIds }
                Card(
                    onClick = { onSelect(cat.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CatAvatar(cat, 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(cat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (count == 0) "Aucun programme" else if (count == 1) "1 programme" else "$count programmes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("Voir statistiques et historique ›", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Text(
                    "Un repas partagé entre plusieurs chats est réparti équitablement dans les statistiques.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    } else {
        val now by rememberNow()
        val history = entries.filter {
            selectedCat.id in it.catIds && it.status != Status.PENDING
        }.sortedByDescending { if (it.doneAt > 0) it.doneAt else it.at }
        val completed = history.filter { it.status == Status.DONE }
        val totalGrams = completed.sumOf { entry ->
            val quantity = entry.givenQty.ifBlank { entry.qty }
            val product = products.firstOrNull { it.id == entry.productId }
            (gramsFor(quantity, product) ?: 0.0) / entry.catIds.distinct().size.coerceAtLeast(1)
        }
        val cost = monthlyFoodCost(
            selectedCat.id, plans, foodPatterns, products, kibblePackages, YearMonth.now(),
        )
        val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("fr-BE"))
        val monthLabel = YearMonth.now().month.getDisplayName(java.time.format.TextStyle.FULL, Locale.FRENCH)
            .replaceFirstChar { it.titlecase(Locale.FRENCH) }
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹ Chats") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onEdit(selectedCat.id) }) { Text("Modifier le profil") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatAvatar(selectedCat, 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(selectedCat.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Mangé", formatGrams(totalGrams), "quantités enregistrées", Modifier.weight(1f))
                    StatTile(
                        "Coût estimé",
                        when {
                            cost.unpricedMeals > 0 && cost.amount > 0 -> "≥ ${currency.format(cost.amount)}"
                            cost.unpricedMeals > 0 -> "—"
                            else -> currency.format(cost.amount)
                        },
                        if (cost.unpricedMeals > 0) "$monthLabel · ${cost.unpricedMeals} repas incomplets" else "Mois de $monthLabel",
                        Modifier.weight(1f),
                    )
                }
            }
            item {
                Text(
                    "Détail · pâtées ${currency.format(cost.mealAmount)} · croquettes ${currency.format(cost.kibbleAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                cost.planDetails.forEach { detail ->
                    Text(
                        "${Fmt.timeMin(detail.timeMin)} · ${Fmt.every(detail.everyDays)} · " +
                            "${detail.quantity.ifBlank { "quantité non indiquée" }} : " +
                            "${detail.meals} repas · ${currency.format(detail.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Le coût des repas est estimé sur le mois calendrier. Le prix des croquettes est réparti sur la durée du paquet (durée écoulée pour un paquet en cours) et partagé entre les chats concernés.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    "Historique · ${completed.size} repas donnés",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (history.isEmpty()) {
                item { EmptyState(R.drawable.ic_nav_cats, "Rien dans l'historique pour ce chat pour l'instant.") }
            } else {
                items(history, key = { it.id }) { entry ->
                    MealCard(entry.toOcc(), cats, products, now, onOpenEntry, null)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Raccourcis vers les réglages Android
// ---------------------------------------------------------------------------------------------

private fun openNotificationSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        )
    }
}

private fun openExactAlarmSettings(ctx: Context) {
    runCatching {
        if (Build.VERSION.SDK_INT >= 31) {
            ctx.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
            )
        }
    }
}
