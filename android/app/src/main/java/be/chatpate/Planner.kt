package be.chatpate

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.concurrent.thread

/**
 * Une occurrence de repas : soit un vrai repas déjà créé ([entry] non nul),
 * soit un repas simplement "calculé" à partir d'un programme (plus loin dans le futur).
 */
data class Occ(
    val id: String,
    val at: Long,
    val planId: String,
    val pate: String,
    val qty: String,
    val catIds: List<String>,
    val entry: Entry?,
    val productId: String = "",
    val foodTypeId: String = "",
    val foodTypeName: String = "",
    val foodTypeEmoji: String = "",
)

fun Entry.toOcc() = Occ(id, at, planId, pate, qty, catIds, this, productId, foodTypeId, foodTypeName, foodTypeEmoji)

/**
 * Transforme les programmes en repas concrets jusqu'à l'épuisement prévisible du stock.
 * Appelé à l'ouverture de l'appli, quand on modifie un programme, et quand une alarme sonne.
 */
object Planner {
    private const val MIN_HORIZON_DAYS = 14L
    private const val MAX_HORIZON_DAYS = 3650L

    private fun horizonDays(state: AppState): Long {
        var estimatedDays = 0L
        val activeProducts = state.products.filterNot { it.deleted || it.putAside }
        state.plans.filter { !it.deleted && it.active && it.catIds.isNotEmpty() }.forEach { plan ->
            val candidates = if (plan.autoAllocate) {
                plan.catIds.distinct().flatMap { catId ->
                    val restricted = activeProducts.filter { catId in it.catIds }
                    if (restricted.isNotEmpty()) restricted else activeProducts.filter { it.catIds.isEmpty() }
                }.distinctBy { it.id }
            } else {
                val effectivePatternId = plan.patternId.ifEmpty {
                    if (!plan.alternatePate) "" else if (plan.pate == Pate.BOEUF.key) {
                        "boeuf-poulet"
                    } else {
                        "poulet-boeuf"
                    }
                }
                val pattern = state.foodPatterns.firstOrNull {
                    it.id == effectivePatternId && !it.deleted
                }
                val directProduct = activeProducts.firstOrNull { it.id == plan.productId }
                val patternProductIds = if (directProduct == null) pattern?.foodIds.orEmpty() else emptyList()
                activeProducts.filter { product ->
                    if (directProduct != null) product.id == directProduct.id
                    else if (patternProductIds.isNotEmpty()) product.id in patternProductIds
                    else product.kind == plan.pate
                }
            }
            val servings = candidates.sumOf { it.availableServings ?: 0L }
            if (servings > 0L) {
                val days = (servings.coerceAtMost(MAX_HORIZON_DAYS) *
                    plan.everyDays.coerceAtLeast(1).toLong()).coerceAtMost(MAX_HORIZON_DAYS)
                estimatedDays = (estimatedDays + days).coerceAtMost(MAX_HORIZON_DAYS)
            }
        }
        return maxOf(MIN_HORIZON_DAYS, estimatedDays)
    }

    private fun firstOccurrenceOnOrAfter(start: LocalDate, date: LocalDate, everyDays: Long): LocalDate {
        val candidate = if (date.isAfter(start)) date else start
        val remainder = ChronoUnit.DAYS.between(start, candidate) % everyDays
        return if (remainder == 0L) candidate else candidate.plusDays(everyDays - remainder)
    }

    fun reconcile() {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val st = Store.snapshot()
        val today = LocalDate.now(zone)
        val horizonDays = horizonDays(st)
        val byId = st.entries.associateBy { it.id }
        val wanted = HashSet<String>()
        val changed = ArrayList<Entry>()
        val automaticAllocations = allocationsForHorizon(st, today, now, horizonDays)

        for (p in st.plans) {
            if (p.deleted || !p.active) continue
            val start = runCatching { LocalDate.parse(p.start) }.getOrNull() ?: continue
            val every = p.everyDays.coerceAtLeast(1).toLong()
            var d = firstOccurrenceOnOrAfter(start, today, every)
            val lastDate = today.plusDays(horizonDays)
            while (!d.isAfter(lastDate)) {
                val at = d.atTime(p.timeMin / 60, p.timeMin % 60)
                    .atZone(zone).toInstant().toEpochMilli()
                val id = p.id + "_" + d.toString().replace("-", "")
                val foodAllocations = if (p.autoAllocate) automaticAllocations[id].orEmpty() else emptyList()
                val allocatedProductIds = foodAllocations.map { it.productId }.distinct()
                val product = if (p.autoAllocate && allocatedProductIds.size == 1 &&
                    foodAllocations.all { it.productId.isNotEmpty() }
                ) st.products.firstOrNull { it.id == allocatedProductIds.single() && !it.deleted }
                else if (p.autoAllocate) null else p.foodProductOn(d, st.foodPatterns, st.products)
                val productId = product?.id.orEmpty()
                val typeId = if (p.autoAllocate) product?.pateKey ?: "auto"
                    else product?.pateKey ?: p.foodTypeOn(d, st.foodPatterns)
                val foodType = st.foodTypes.firstOrNull { it.id == typeId && !it.deleted }
                val pate = if (typeId.isNotEmpty()) typeId else p.pateOn(d)
                val foodName = when {
                    !p.autoAllocate -> foodType?.name.orEmpty()
                    foodAllocations.any { it.productId.isBlank() } -> "Nourriture à configurer"
                    product != null -> product.name
                    else -> "Nourriture attribuée par chat"
                }
                wanted.add(id)

                val ex = byId[id]
                if (ex?.permanentlyDeleted != true) {
                    if (ex == null) {
                        // On ne crée pas un repas "déjà en retard" juste parce qu'on vient de créer le programme.
                        if (at > now || p.updatedAt < at) {
                            changed.add(
                                Entry(
                                    id = id, planId = p.id, at = at, pate = pate, qty = p.qty,
                                    updatedAt = now, catIds = p.catIds, productId = productId,
                                    plannedProductId = productId,
                                    foodTypeId = typeId,
                                    foodTypeName = foodName,
                                    foodTypeEmoji = foodType?.emoji.orEmpty(),
                                    foodAllocations = foodAllocations,
                                )
                            )
                        }
                    } else if (ex.status == Status.PENDING && at > now &&
                        (ex.deleted || ex.at != at || ex.pate != pate || ex.qty != p.qty ||
                            ex.catIds != p.catIds || ex.productId != productId ||
                            ex.plannedProductId != productId ||
                            ex.foodTypeId != typeId || ex.foodTypeName != foodName ||
                            ex.foodTypeEmoji != foodType?.emoji.orEmpty() ||
                            ex.foodAllocations != foodAllocations)
                    ) {
                        changed.add(
                            ex.copy(
                                at = at, pate = pate, qty = p.qty, catIds = p.catIds, productId = productId,
                                plannedProductId = productId,
                                foodTypeId = typeId,
                                foodTypeName = foodName,
                                foodTypeEmoji = foodType?.emoji.orEmpty(),
                                foodAllocations = foodAllocations,
                                deleted = false, updatedAt = now,
                            )
                        )
                    }
                }
                d = d.plusDays(every)
            }
        }

        // Supprime aussi les repas en attente déjà passés d'un programme supprimé.
        for (e in st.entries) {
            if (e.status == Status.PENDING && e.id !in wanted) {
                val planDeleted = st.plans.firstOrNull { it.id == e.planId }?.deleted == true
                if (planDeleted && !e.permanentlyDeleted) {
                    changed.add(e.copy(deleted = true, permanentlyDeleted = true, updatedAt = now))
                } else if (!e.deleted && e.at > now) {
                    changed.add(e.copy(deleted = true, updatedAt = now))
                }
            }
        }

        Store.upsertEntries(changed)
    }

    private data class ScheduledOccurrence(
        val plan: Plan,
        val date: LocalDate,
        val at: Long,
        val id: String,
    )

    private fun allocationsForHorizon(
        state: AppState,
        today: LocalDate,
        now: Long,
        horizonDays: Long,
    ): Map<String, List<FoodAllocation>> {
        val zone = ZoneId.systemDefault()
        val scheduled = buildList {
            state.plans.filter { !it.deleted && it.active }.forEach { plan ->
                val start = runCatching { LocalDate.parse(plan.start) }.getOrNull() ?: return@forEach
                val every = plan.everyDays.coerceAtLeast(1).toLong()
                var date = firstOccurrenceOnOrAfter(start, today, every)
                val lastDate = today.plusDays(horizonDays)
                while (!date.isAfter(lastDate)) {
                    val at = date.atTime(plan.timeMin / 60, plan.timeMin % 60)
                        .atZone(zone).toInstant().toEpochMilli()
                    val id = plan.id + "_" + date.toString().replace("-", "")
                    if (at > now && state.entries.firstOrNull { it.id == id }?.permanentlyDeleted != true) {
                        add(ScheduledOccurrence(plan, date, at, id))
                    }
                    date = date.plusDays(every)
                }
            }
        }.sortedBy { it.at }

        val remaining = FoodAllocator.initialStock(state.products)
        val rotation = FoodRotationState()
        state.entries
            .filter {
                !it.deleted && !it.permanentlyDeleted && it.status != Status.SKIPPED &&
                    (it.at <= now || it.status == Status.DONE)
            }
            .sortedBy { it.at }
            .forEach { entry ->
                val allocatedByCat = entry.foodAllocations.associate { it.catId to it.productId }
                entry.catIds.distinct().forEach { catId ->
                    val productId = if (entry.status == Status.DONE && entry.productId.isNotBlank()) {
                        entry.productId
                    } else {
                        allocatedByCat[catId].orEmpty()
                            .ifBlank { entry.productId.ifBlank { entry.plannedProductId } }
                    }
                    rotation.record(catId, productId)
                }
            }
        val allocations = LinkedHashMap<String, List<FoodAllocation>>()
        scheduled.forEach { occurrence ->
            val plan = occurrence.plan
            if (plan.autoAllocate) {
                allocations[occurrence.id] = FoodAllocator.allocate(
                    plan.catIds,
                    state.products,
                    remaining,
                    state.stockSettings.warningThreshold,
                    state.stockSettings.lowThreshold,
                    rotation,
                )
            } else {
                val product = plan.foodProductOn(occurrence.date, state.foodPatterns, state.products)
                if (product != null) {
                    if (product.id in remaining) {
                        val cats = plan.catIds.distinct().size
                        remaining[product.id] = ((remaining[product.id] ?: 0L) - cats).coerceAtLeast(0L)
                    }
                    plan.catIds.distinct().forEach { catId -> rotation.record(catId, product.id) }
                }
            }
        }
        return allocations
    }

    fun stockDepletionDates(
        state: AppState,
        today: LocalDate,
        through: LocalDate,
        now: Long,
    ): Map<String, LocalDate> {
        if (through.isBefore(today)) return emptyMap()
        val zone = ZoneId.systemDefault()
        val scheduled = buildList {
            state.plans.filter { !it.deleted && it.active }.forEach { plan ->
                val start = runCatching { LocalDate.parse(plan.start) }.getOrNull() ?: return@forEach
                val every = plan.everyDays.coerceAtLeast(1).toLong()
                var date = firstOccurrenceOnOrAfter(start, today, every)
                while (!date.isAfter(through)) {
                    val at = date.atTime(plan.timeMin / 60, plan.timeMin % 60)
                        .atZone(zone).toInstant().toEpochMilli()
                    val id = plan.id + "_" + date.toString().replace("-", "")
                    if (at > now && state.entries.firstOrNull { it.id == id }?.let {
                            it.permanentlyDeleted || it.deleted || it.status == Status.SKIPPED
                        } != true
                    ) {
                        add(ScheduledOccurrence(plan, date, at, id))
                    }
                    date = date.plusDays(every)
                }
            }
        }.sortedBy { it.at }

        val remaining = FoodAllocator.initialStock(state.products)
        val rotation = FoodRotationState()
        val plannedCats = state.plans
            .filter { !it.deleted && it.active }
            .flatMap { it.catIds }
            .distinct()
        state.entries
            .filter { !it.deleted && !it.permanentlyDeleted && it.status != Status.SKIPPED && it.at <= now }
            .sortedBy { it.at }
            .forEach { entry ->
                val allocations = entry.foodAllocations.associate { it.catId to it.productId }
                entry.catIds.distinct().forEach { catId ->
                    val productId = if (entry.status == Status.DONE && entry.productId.isNotBlank()) {
                        entry.productId
                    } else {
                        allocations[catId].orEmpty().ifBlank { entry.productId.ifBlank { entry.plannedProductId } }
                    }
                    rotation.record(catId, productId)
                }
            }

        val depleted = mutableMapOf<String, LocalDate>()
        plannedCats.forEach { catId ->
            if (FoodAllocator.knownStockEmpty(catId, state.products, remaining) == true) {
                depleted[catId] = today
            }
        }
        fun recordDepleted(catId: String, date: LocalDate) {
            val previous = depleted[catId]
            if (previous == null || date.isBefore(previous)) depleted[catId] = date
        }
        scheduled.forEach { occurrence ->
            val plan = occurrence.plan
            if (plan.autoAllocate) {
                val allocations = FoodAllocator.allocate(
                    plan.catIds,
                    state.products,
                    remaining,
                    state.stockSettings.warningThreshold,
                    state.stockSettings.lowThreshold,
                    rotation,
                )
                allocations.filterNot { it.inStock }.forEach { allocation ->
                    val knownEmpty = FoodAllocator.knownStockEmpty(
                        allocation.catId,
                        state.products,
                        remaining,
                    )
                    if (knownEmpty == true) recordDepleted(allocation.catId, occurrence.date)
                }
                allocations.filter { it.inStock }.forEach { allocation ->
                    if (FoodAllocator.knownStockEmpty(allocation.catId, state.products, remaining) == true) {
                        recordDepleted(allocation.catId, occurrence.date.plusDays(1))
                    }
                }
            } else {
                val product = plan.foodProductOn(occurrence.date, state.foodPatterns, state.products)
                if (product != null && product.id in remaining) {
                    val cats = plan.catIds.distinct()
                    val catHadStock = mutableMapOf<String, Boolean>()
                    cats.forEach { catId ->
                        val hadStock = (remaining[product.id] ?: 0L) > 0L
                        catHadStock[catId] = hadStock
                        if (hadStock) {
                            remaining[product.id] = (remaining[product.id] ?: 0L) - 1L
                        }
                    }
                    cats.forEach { catId ->
                        if (FoodAllocator.knownStockEmpty(catId, state.products, remaining) == true) {
                            val date = if (catHadStock[catId] == true) {
                                occurrence.date.plusDays(1)
                            } else {
                                occurrence.date
                            }
                            recordDepleted(catId, date)
                        }
                        rotation.record(catId, product.id)
                    }
                }
            }
        }
        return depleted
    }

    /**
     * Tous les repas entre deux dates (incluses) : les vrais repas, plus ceux qui seront créés
     * plus tard par les programmes (pour que le calendrier et les totaux du mois soient complets).
     */
    fun occurrences(st: AppState, from: LocalDate, to: LocalDate, now: Long): List<Occ> {
        val zone = ZoneId.systemDefault()
        val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val out = ArrayList<Occ>()
        val seen = HashSet<String>()

        for (e in st.entries) {
            seen.add(e.id)
            if (e.permanentlyDeleted) continue
            if (e.at < fromMs || e.at >= toMs) continue
            if (e.deleted) {
                if (e.status == Status.PENDING && e.at <= now) out.add(e.toOcc())
                continue
            }
            out.add(e.toOcc())
        }

        for (p in st.plans) {
            if (p.deleted || !p.active) continue
            val start = runCatching { LocalDate.parse(p.start) }.getOrNull() ?: continue
            val every = p.everyDays.coerceAtLeast(1).toLong()
            var d = if (from.isAfter(start)) from else start
            while (!d.isAfter(to)) {
                if (ChronoUnit.DAYS.between(start, d) % every == 0L) {
                    val at = d.atTime(p.timeMin / 60, p.timeMin % 60)
                        .atZone(zone).toInstant().toEpochMilli()
                    val id = p.id + "_" + d.toString().replace("-", "")
                    // Dans le passé, seuls les vrais repas comptent.
                    if (id !in seen && at > now) {
                        val product = if (p.autoAllocate) null else p.foodProductOn(d, st.foodPatterns, st.products)
                        val typeId = if (p.autoAllocate) "auto"
                            else product?.pateKey ?: p.foodTypeOn(d, st.foodPatterns)
                        val foodType = st.foodTypes.firstOrNull { it.id == typeId && !it.deleted }
                        val pate = if (typeId.isNotEmpty()) typeId else p.pateOn(d)
                        val foodName = if (p.autoAllocate) "Choisie selon le stock" else foodType?.name.orEmpty()
                        out.add(Occ(id, at, p.id, pate, p.qty, p.catIds, null, product?.id.orEmpty(), typeId, foodName, foodType?.emoji.orEmpty()))
                    }
                }
                d = d.plusDays(1)
            }
        }
        return out.sortedBy { it.at }
    }

    /** À appeler après n'importe quelle modification : recalcule, reprogramme les alarmes, synchronise. */
    fun afterChange(ctx: Context) {
        val app = ctx.applicationContext
        thread {
            reconcile()
            Alarms.reschedule(app)
            Sync.run(app)
        }
    }
}
