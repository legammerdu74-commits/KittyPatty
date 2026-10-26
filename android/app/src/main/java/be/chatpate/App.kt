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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
    val cats = state.cats.filter { !it.deleted }

    var tab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
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
                syncStatus = syncStatus,
                onSync = { thread { Sync.run(ctx) } },
                onSettings = { showSettings = true },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavTab(tab == 0, { tab = 0 }, R.drawable.ic_nav_home, "Accueil")
                NavTab(tab == 1, { tab = 1 }, R.drawable.ic_nav_programs, "Programmes")
                NavTab(tab == 2, { tab = 2 }, R.drawable.ic_nav_cats, "Chats")
                NavTab(tab == 3, { tab = 3 }, R.drawable.ic_nav_food, "Alimentation")
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
                foodPatterns = state.foodPatterns.filter { !it.deleted },
                kibblePackages = state.kibblePackages.filterNot { it.deleted },
                cats = cats,
                modifier = mod,
                onSaveProduct = {
                    Store.upsertProduct(it)
                    Planner.afterChange(ctx)
                },
                onDeleteProduct = {
                    Store.deleteProduct(it)
                    Planner.afterChange(ctx)
                },
                onSavePattern = {
                    Store.upsertFoodPattern(it)
                    Planner.afterChange(ctx)
                },
                onDeletePattern = {
                    Store.deleteFoodPattern(it)
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

    // ----- Fenêtres -----

    if (showNewPlan) {
        PlanDialog(
            cats = cats,
            products = state.products.filter { !it.deleted },
            foodPatterns = state.foodPatterns,
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
            onSave = { url, token ->
                Prefs.saveServer(ctx, url, token)
                Planner.afterChange(ctx)
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// En-tête et petits éléments communs
// ---------------------------------------------------------------------------------------------

@Composable
fun AppHeader(syncStatus: String, onSync: () -> Unit, onSettings: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.primaryContainer, c.background)))
    ) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(c.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_logo_mark),
                    contentDescription = null,
                    tint = c.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = c.onPrimaryContainer,
                )
                if (syncStatus.isNotEmpty()) {
                    Text(
                        syncStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onSync) {
                Icon(painterResource(R.drawable.ic_action_refresh), contentDescription = "Synchroniser", tint = c.onPrimaryContainer)
            }
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_action_settings), contentDescription = "Réglages", tint = c.onPrimaryContainer)
            }
        }
    }
}

/** Un onglet de la barre du bas : icône de la famille + libellé sur une ligne. */
@Composable
private fun RowScope.NavTab(selected: Boolean, onClick: () -> Unit, icon: Int, label: String) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
        label = {
            Text(
                label,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        },
    )
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
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
fun PateAvatar(key: String, emoji: String? = null) {
    Box(
        Modifier
            .size(46.dp)
            .background(pateColor(key).copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji ?: pateEmoji(key), fontSize = 22.sp)
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
) {
    val e = occ.entry
    val pending = e != null && e.status == Status.PENDING
    val overdue = pending && occ.at <= now
    val done = e?.status == Status.DONE
    val skipped = e?.status == Status.SKIPPED
    val shownPate = if (done && e != null) e.givenPate.ifEmpty { occ.pate } else occ.pate
    val shownQty = if (done && e != null) e.givenQty else occ.qty
    val product = products.firstOrNull { it.id == occ.productId }
    val shownFoodName = if (done && e != null) e.givenFoodTypeName.ifEmpty { occ.foodTypeName }
        else e?.foodTypeName?.ifEmpty { occ.foodTypeName } ?: occ.foodTypeName
    val shownFoodEmoji = if (done && e != null) e.givenFoodTypeEmoji.ifEmpty { occ.foodTypeEmoji }
        else e?.foodTypeEmoji?.ifEmpty { occ.foodTypeEmoji } ?: occ.foodTypeEmoji
    val who = cats.filter { it.id in occ.catIds }

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
        Row(Modifier.padding(14.dp)) {
            PateAvatar(product?.pateKey ?: shownPate, shownFoodEmoji.ifEmpty { null })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${Fmt.date(occ.at)} · ${Fmt.time(occ.at)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    (product?.let {
                        "${pateEmoji(it.pateKey)} ${it.name} · ${it.brand} · ${it.packPrice} €/sachet" +
                            it.weight.takeIf { weight -> weight.isNotBlank() }?.let { weight -> " · $weight g" }.orEmpty()
                    }
                        ?: shownFoodName.takeIf { it.isNotBlank() }?.let { name ->
                            "${shownFoodEmoji.ifEmpty { pateEmoji(shownPate) }} $name"
                        }
                        ?: pateLabel(shownPate)) + if (shownQty.isNotBlank()) " · $shownQty" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (who.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    CatsLine(who)
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
                            TextButton(onClick = { onSkip(e.id) }, modifier = Modifier.height(48.dp)) { Text("Passer") }
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
                    MealCard(o, cats, products, now, onOpen, onSkip)
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
                                DayCell(date, today, selected, byDay[date].orEmpty(), now, onSelect)
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
    now: Long,
    onSelect: (LocalDate) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val isToday = date == today
    val isSel = date == selected
    var m = Modifier
        .size(width = 42.dp, height = 52.dp)
        .clip(RoundedCornerShape(14.dp))
    if (isSel) m = m.background(c.primaryContainer)
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
            color = if (isSel) c.onPrimaryContainer else c.onSurface,
        )
        Spacer(Modifier.height(3.dp))
        if (occs.isNotEmpty()) {
            val color = when {
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
                Text(
                    if (occs.size == 1) {
                        val meal = occs.first()
                        val emoji = meal.entry?.takeIf { it.status == Status.DONE }
                            ?.givenFoodTypeEmoji?.ifEmpty { meal.foodTypeEmoji } ?: meal.foodTypeEmoji
                        emoji.ifEmpty { pateEmoji(meal.pate) }
                    } else occs.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                )
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
                                    product.packPrice.takeIf { it.isNotBlank() }?.let { "$it €/sachet" },
                                    product.weight.takeIf { it.isNotBlank() }?.let { "$it g" },
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
    foodPatterns: List<FoodPattern>,
    kibblePackages: List<KibblePackage>,
    cats: List<Cat>,
    modifier: Modifier,
    onSaveProduct: (FoodProduct) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onSavePattern: (FoodPattern) -> Unit,
    onDeletePattern: (String) -> Unit,
    onSaveKibblePackage: (KibblePackage) -> Unit,
    onFinishKibblePackage: (KibblePackage) -> Unit,
    onDeleteKibblePackage: (String) -> Unit,
) {
    var category by remember { mutableIntStateOf(0) }
    var showProductEditor by remember { mutableStateOf(false) }
    var editingProductId by remember { mutableStateOf<String?>(null) }
    var showKibbleEditor by remember { mutableStateOf(false) }
    var editingKibbleId by remember { mutableStateOf<String?>(null) }
    var deleteKibbleId by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Alimentation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Nourriture", "Croquettes").forEachIndexed { index, label ->
                val selected = if (index == 0) category != 2 else category == 2
                val target = if (index == 0) 0 else 2
                val icon = R.drawable.ic_nav_food
                if (selected) {
                    Button(
                        onClick = { category = target },
                        modifier = Modifier.weight(1f).height(52.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { FoodCategoryLabel(icon, label) }
                } else {
                    OutlinedButton(
                        onClick = { category = target },
                        modifier = Modifier.weight(1f).height(52.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { FoodCategoryLabel(icon, label) }
                }
            }
        }

        if (category == 0) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                editingProductId = null
                                showProductEditor = true
                            },
                            modifier = Modifier.weight(1.5f),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text("Ajouter une nourriture", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { category = 1 },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            FoodCategoryLabel(R.drawable.ic_action_repeat, "Combinaisons")
                        }
                    }
                }
                if (products.isEmpty()) {
                    item { EmptyState(R.drawable.ic_nav_food, "Ajoute une nourriture pour la retrouver dans tes programmes.") }
                }
                items(products, key = { it.id }) { product ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${pateEmoji(product.pateKey)} ${product.name}", fontWeight = FontWeight.Bold)
                                Text(
                                    listOfNotNull(
                                        product.brand.takeIf { it.isNotBlank() },
                                        product.packPrice.takeIf { it.isNotBlank() }?.let { "$it €/sachet" },
                                        product.weight.takeIf { it.isNotBlank() }?.let { "$it g" },
                                    ).joinToString(" · ").ifEmpty { "Nourriture de base" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                editingProductId = product.id
                                showProductEditor = true
                            }) { Text("Modifier") }
                        }
                    }
                }
            }
        } else if (category == 1) {
            FoodSettingsScreen(
                patterns = foodPatterns,
                modifier = Modifier.weight(1f),
                products = products,
                onSavePattern = onSavePattern,
                onDeletePattern = onDeletePattern,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "Ajoute chaque paquet ouvert. Termine-le lorsqu’il est vide pour calculer sa durée réelle.",
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
                    val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("fr-BE"))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        pack.brand.ifBlank { "Paquet de croquettes" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (pack.flavor.isNotBlank()) {
                                        Text(pack.flavor, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    currency.format(pack.price.replace(',', '.').toDoubleOrNull() ?: 0.0),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (pack.specialty.isNotBlank()) {
                                Text(
                                    pack.specialty,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Ajout", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        addedOn?.let { Fmt.dateLong(it) } ?: "Date inconnue",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Durée", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${duration ?: "?"} jour${if (duration == 1) "" else "s"}${if (finishedOn == null) " · en cours" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            CatsLine(cats.filter { it.id in pack.catIds })
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { if (finishedOn == null) onFinishKibblePackage(pack) },
                                    enabled = finishedOn == null,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                ) {
                                    Text(if (finishedOn == null) "Terminer" else "Terminé", maxLines = 1)
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
        }
    }

    if (showProductEditor) {
        ProductCatalogDialog(
            products = products,
            initialProductId = editingProductId,
            onSave = onSaveProduct,
            onDelete = onDeleteProduct,
            onDismiss = {
                showProductEditor = false
                editingProductId = null
            },
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

private data class FoodQuantity(val amount: Double, val isGrams: Boolean)
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
        unit.isNotEmpty() -> FoodQuantity(amount, false)
        productWeight != null && kotlin.math.abs(amount - productWeight) < 0.0001 -> FoodQuantity(amount, true)
        else -> FoodQuantity(amount, false)
    }
}

private fun gramsFor(quantity: String, product: FoodProduct?): Double? {
    val parsed = parseFoodQuantity(quantity, product) ?: return null
    if (parsed.isGrams) return parsed.amount
    val weight = product?.weight?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
    return parsed.amount * weight
}

private fun costFor(quantity: String, product: FoodProduct?): Double? {
    val parsed = parseFoodQuantity(quantity, product) ?: return null
    val price = product?.packPrice?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it >= 0 } ?: return null
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
                val product = plan.foodProductOn(date, patterns, products)
                val mealCost = costFor(plan.qty, product)
                if (mealCost == null) {
                    unpriced++
                } else {
                    val catShare = mealCost / plan.catIds.distinct().size.coerceAtLeast(1)
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
