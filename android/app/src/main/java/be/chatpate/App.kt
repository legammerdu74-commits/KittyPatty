@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package be.chatpate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Accueil") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    label = { Text("Programmes") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                    label = { Text("Historique") },
                )
                NavigationBarItem(
                    selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = { Text("Chats") },
                )
            }
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showNewPlan = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Programme") },
                )
            } else if (tab == 3) {
                ExtendedFloatingActionButton(
                    onClick = { showNewCat = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
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
                exactOk = exactOk,
                notifOk = notifOk,
                modifier = mod,
                onOpen = { encodeId = it },
                onSkip = skip,
            )

            1 -> PlansScreen(
                plans = state.plans.filter { !it.deleted }.sortedBy { it.timeMin },
                cats = cats,
                modifier = mod,
                onEdit = { editPlanId = it },
                onToggle = {
                    Store.upsertPlan(it.copy(active = !it.active))
                    Planner.afterChange(ctx)
                },
            )

            2 -> HistoryScreen(
                entries = state.entries
                    .filter { !it.deleted && it.status != Status.PENDING }
                    .sortedByDescending { it.at }
                    .take(200),
                cats = cats,
                modifier = mod,
                onOpen = { encodeId = it },
            )

            else -> CatsScreen(
                cats = cats,
                plans = state.plans.filter { !it.deleted },
                modifier = mod,
                onEdit = { editCatId = it },
            )
        }
    }

    // ----- Fenêtres -----

    if (showNewPlan) {
        PlanDialog(
            cats = cats,
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

    val entryToEncode = state.entries.firstOrNull { it.id == encodeId && !it.deleted }
    if (entryToEncode != null) {
        EncodeDialog(
            entry = entryToEncode,
            cats = cats,
            onSave = { pate, qty, note, doneAt, catIds ->
                Store.markDone(entryToEncode.id, pate, qty, note, doneAt, catIds)
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
                    painterResource(R.drawable.ic_paw),
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
                Icon(Icons.Default.Refresh, contentDescription = "Synchroniser", tint = c.onPrimaryContainer)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Réglages", tint = c.onPrimaryContainer)
            }
        }
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
fun EmptyState(emoji: String, text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
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
    Box(
        Modifier
            .size(size)
            .background(catColor(cat.color), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            cat.name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp,
        )
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
fun PateAvatar(key: String) {
    Box(
        Modifier
            .size(46.dp)
            .background(pateColor(key).copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(Pate.from(key).emoji, fontSize = 22.sp)
    }
}

/** Carte d'un repas, utilisée partout (accueil, jour du calendrier, historique). */
@Composable
fun MealCard(
    occ: Occ,
    cats: List<Cat>,
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
            PateAvatar(shownPate)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${Fmt.date(occ.at)} · ${Fmt.time(occ.at)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    Pate.from(shownPate).label + if (shownQty.isNotBlank()) " · $shownQty" else "",
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
                                " (prévu : ${Pate.from(e.pate).label})" else "",
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
                if (e != null && e.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(e.note, style = MaterialTheme.typography.bodyMedium)
                }
                if (pending && e != null && onOpen != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpen(e.id) }) { Text(if (overdue) "Encoder" else "Donné") }
                        if (onSkip != null) {
                            TextButton(onClick = { onSkip(e.id) }) { Text("Passer") }
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

    val minute = now / 60_000
    val monthOccs = remember(state, month, minute) {
        Planner.occurrences(state, month.atDay(1), month.atEndOfMonth(), now)
    }
    val byDay = remember(monthOccs) {
        monthOccs.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
    }
    val overdue = remember(state, minute) {
        state.entries
            .filter { !it.deleted && it.status == Status.PENDING && it.at <= now }
            .sortedBy { it.at }
            .map { it.toOcc() }
    }
    val upcoming = remember(state, minute) {
        Planner.occurrences(state, today, today.plusDays(30), now)
            .filter { it.at > now && (it.entry == null || it.entry.status == Status.PENDING) }
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
                MealCard(o, cats, now, onOpen, onSkip)
            }
        }

        item {
            CalendarCard(
                month = month,
                today = today,
                byDay = byDay,
                selected = selected,
                now = now,
                onPrev = { month = month.minusMonths(1); selected = null },
                onNext = { month = month.plusMonths(1); selected = null },
                onToday = { month = YearMonth.now(); selected = today },
                onSelect = { d -> selected = if (selected == d) null else d },
            )
        }

        item {
            val total = monthOccs.size
            val passed = monthOccs.count { it.at <= now }
            val given = monthOccs.count { it.entry?.status == Status.DONE }
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
                item { EmptyState("😺", "Aucun repas ce jour-là.") }
            } else {
                items(list, key = { "d_" + it.id }) { o ->
                    MealCard(o, cats, now, onOpen, onSkip)
                }
            }
        } else {
            item { SectionTitle("Prochains repas") }
            if (upcoming.isEmpty()) {
                item {
                    EmptyState(
                        "🐈",
                        "Aucun repas programmé pour le moment.\nVa dans « Programmes » pour en créer un."
                    )
                }
            } else {
                items(upcoming, key = { "u_" + it.id }) { o ->
                    MealCard(o, cats, now, onOpen, onSkip)
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
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
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
                TextButton(onClick = onPrev) { Text("‹", fontSize = 22.sp) }
                Text(
                    Fmt.monthTitle(month),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToday() },
                )
                TextButton(onClick = onNext) { Text("›", fontSize = 22.sp) }
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
                                .height(48.dp),
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
        .size(width = 42.dp, height = 46.dp)
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.height(6.dp),
        ) {
            occs.take(4).forEach { o ->
                val st = o.entry?.status
                val color = when {
                    st == Status.DONE -> DoneColor
                    st == Status.SKIPPED -> c.onSurfaceVariant.copy(alpha = 0.35f)
                    st == Status.PENDING && o.at <= now -> c.error
                    else -> c.primary.copy(alpha = 0.55f)
                }
                Box(
                    Modifier
                        .size(6.dp)
                        .background(color, CircleShape)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Onglet "Programmes"
// ---------------------------------------------------------------------------------------------

@Composable
fun PlansScreen(
    plans: List<Plan>,
    cats: List<Cat>,
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
                    "🐟",
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
                    PateAvatar(p.pate)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${Fmt.every(p.everyDays)} à ${Fmt.timeMin(p.timeMin)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            Pate.from(p.pate).label + if (p.qty.isNotBlank()) " · ${p.qty}" else "",
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

// ---------------------------------------------------------------------------------------------
// Onglet "Historique"
// ---------------------------------------------------------------------------------------------

@Composable
fun HistoryScreen(entries: List<Entry>, cats: List<Cat>, modifier: Modifier, onOpen: (String) -> Unit) {
    val now by rememberNow()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (entries.isEmpty()) {
            item {
                EmptyState(
                    "😸",
                    "Rien dans l'historique pour l'instant.\nLes repas que tu encodes apparaîtront ici."
                )
            }
        }
        items(entries, key = { it.id }) { e ->
            MealCard(e.toOcc(), cats, now, onOpen, null)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Onglet "Chats"
// ---------------------------------------------------------------------------------------------

@Composable
fun CatsScreen(cats: List<Cat>, plans: List<Plan>, modifier: Modifier, onEdit: (String) -> Unit) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cats, key = { it.id }) { cat ->
            val n = plans.count { cat.id in it.catIds }
            Card(
                onClick = { onEdit(cat.id) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CatAvatar(cat, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            cat.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (n == 0) "Aucun programme" else if (n == 1) "1 programme" else "$n programmes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Un repas peut concerner un ou plusieurs chats : tu les choisis quand tu crées un programme ou quand tu encodes un repas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
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
