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
 * Transforme les programmes en repas concrets pour les 2 prochaines semaines.
 * Appelé à l'ouverture de l'appli, quand on modifie un programme, et quand une alarme sonne.
 */
object Planner {
    private const val HORIZON_DAYS = 14

    fun reconcile() {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val st = Store.snapshot()
        val today = LocalDate.now(zone)
        val byId = st.entries.associateBy { it.id }
        val wanted = HashSet<String>()
        val changed = ArrayList<Entry>()

        for (p in st.plans) {
            if (p.deleted || !p.active) continue
            val start = runCatching { LocalDate.parse(p.start) }.getOrNull() ?: continue
            val every = p.everyDays.coerceAtLeast(1).toLong()
            for (i in 0..HORIZON_DAYS) {
                val d = today.plusDays(i.toLong())
                if (d.isBefore(start)) continue
                if (ChronoUnit.DAYS.between(start, d) % every != 0L) continue

                val at = d.atTime(p.timeMin / 60, p.timeMin % 60)
                    .atZone(zone).toInstant().toEpochMilli()
                val id = p.id + "_" + d.toString().replace("-", "")
                val product = p.foodProductOn(d, st.foodPatterns, st.products)
                val productId = product?.id.orEmpty()
                val typeId = product?.pateKey ?: p.foodTypeOn(d, st.foodPatterns)
                val foodType = st.foodTypes.firstOrNull { it.id == typeId && !it.deleted }
                val pate = if (typeId.isNotEmpty()) typeId else p.pateOn(d)
                wanted.add(id)

                val ex = byId[id]
                if (ex?.permanentlyDeleted == true) continue
                if (ex == null) {
                    // On ne crée pas un repas "déjà en retard" juste parce qu'on vient de créer le programme.
                    if (at > now || p.updatedAt < at) {
                        changed.add(
                            Entry(
                                id = id, planId = p.id, at = at, pate = pate, qty = p.qty,
                                updatedAt = now, catIds = p.catIds, productId = productId,
                                plannedProductId = productId,
                                foodTypeId = typeId,
                                foodTypeName = foodType?.name.orEmpty(),
                                foodTypeEmoji = foodType?.emoji.orEmpty(),
                            )
                        )
                    }
                } else if (ex.status == Status.PENDING && at > now &&
                    (ex.deleted || ex.at != at || ex.pate != pate || ex.qty != p.qty ||
                        ex.catIds != p.catIds || ex.productId != productId ||
                        ex.plannedProductId != productId ||
                        ex.foodTypeId != typeId || ex.foodTypeName != foodType?.name.orEmpty() ||
                        ex.foodTypeEmoji != foodType?.emoji.orEmpty())
                ) {
                    changed.add(
                        ex.copy(
                            at = at, pate = pate, qty = p.qty, catIds = p.catIds, productId = productId,
                            plannedProductId = productId,
                            foodTypeId = typeId,
                            foodTypeName = foodType?.name.orEmpty(),
                            foodTypeEmoji = foodType?.emoji.orEmpty(),
                            deleted = false, updatedAt = now,
                        )
                    )
                }
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
                        val product = p.foodProductOn(d, st.foodPatterns, st.products)
                        val typeId = product?.pateKey ?: p.foodTypeOn(d, st.foodPatterns)
                        val foodType = st.foodTypes.firstOrNull { it.id == typeId && !it.deleted }
                        val pate = if (typeId.isNotEmpty()) typeId else p.pateOn(d)
                        out.add(Occ(id, at, p.id, pate, p.qty, p.catIds, null, product?.id.orEmpty(), typeId, foodType?.name.orEmpty(), foodType?.emoji.orEmpty()))
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
