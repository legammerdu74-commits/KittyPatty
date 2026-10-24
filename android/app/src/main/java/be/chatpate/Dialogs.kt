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
private fun PateChoice(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in Pate.values()) {
            FilterChip(
                selected = selected == p.key,
                onClick = { onSelect(p.key) },
                label = { Text("${p.emoji} ${p.label}") },
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
    initial: Plan?,
    onSave: (Plan) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var timeMin by remember { mutableIntStateOf(initial?.timeMin ?: (8 * 60)) }
    var every by remember { mutableIntStateOf(initial?.everyDays ?: 1) }
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

                Text("Type de pâtée", fontWeight = FontWeight.Bold)
                PateChoice(pate) { pate = it }

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
                        )
                    )
                },
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Encoder ce qui a été donné (ou corriger un repas de l'historique)
// ---------------------------------------------------------------------------------------------

@Composable
fun EncodeDialog(
    entry: Entry,
    cats: List<Cat>,
    onSave: (pate: String, qty: String, note: String, doneAt: Long, catIds: List<String>) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val wasDone = entry.status == Status.DONE

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
                        Pate.from(entry.pate).label +
                        if (entry.qty.isNotBlank()) " · ${entry.qty}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (cats.size > 1) {
                    Text("Quel(s) chat(s) ?", fontWeight = FontWeight.Bold)
                    CatPicker(cats, catIds) { catIds = it }
                }

                Text("Pâtée donnée", fontWeight = FontWeight.Bold)
                PateChoice(pate) { pate = it }

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
                onClick = { onSave(pate, qty.trim(), note.trim(), doneAt, catIds) },
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
