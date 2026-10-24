package be.chatpate

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Petites préférences (adresse du serveur, code secret...). */
object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    /** Ancien réglage "nom du chat" : sert seulement à nommer le premier chat à la migration. */
    fun legacyCatName(ctx: Context): String = p(ctx).getString("catName", "Mon chat") ?: "Mon chat"
    fun serverUrl(ctx: Context): String = p(ctx).getString("serverUrl", "") ?: ""
    fun token(ctx: Context): String = p(ctx).getString("token", "") ?: ""

    fun saveServer(ctx: Context, serverUrl: String, token: String) {
        p(ctx).edit()
            .putString("serverUrl", serverUrl.trim())
            .putString("token", token.trim())
            .apply()
    }
}

/**
 * Stockage local : un simple fichier JSON dans la mémoire privée de l'appli.
 * Tout passe par ici ; l'écran se met à jour tout seul via [state].
 */
object Store {
    const val DEFAULT_CAT_ID = "default"

    private val lock = Any()
    private var file: File? = null
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    fun init(ctx: Context) {
        synchronized(lock) {
            if (file != null) return
            val f = File(ctx.applicationContext.filesDir, "state.json")
            file = f
            if (f.exists()) {
                runCatching {
                    val o = JSONObject(f.readText())
                    _state.value = AppState(
                        plans = parsePlans(o.optJSONArray("plans")),
                        entries = parseEntries(o.optJSONArray("entries")),
                        cats = parseCats(o.optJSONArray("cats")),
                    )
                }
            }
        }
    }

    fun snapshot(): AppState = _state.value

    fun get(entryId: String): Entry? = _state.value.entries.firstOrNull { it.id == entryId }

    /**
     * Premier lancement (ou mise à jour depuis l'ancienne version) : crée un premier chat
     * et lui rattache les programmes et repas existants.
     */
    fun ensureDefaultCat(name: String) {
        mutate { s ->
            if (s.cats.isNotEmpty()) return@mutate s
            val now = System.currentTimeMillis()
            // updatedAt = 1 : si le serveur connaît déjà ce chat (réinstallation), sa version gagne.
            val cat = Cat(DEFAULT_CAT_ID, name.trim().ifEmpty { "Mon chat" }, 0, false, 1L)
            val ids = listOf(DEFAULT_CAT_ID)
            s.copy(
                cats = listOf(cat),
                plans = s.plans.map { if (it.catIds.isEmpty()) it.copy(catIds = ids, updatedAt = now) else it },
                entries = s.entries.map { if (it.catIds.isEmpty()) it.copy(catIds = ids, updatedAt = now) else it },
            )
        }
    }

    fun upsertCat(c: Cat) {
        val nc = c.copy(updatedAt = System.currentTimeMillis())
        mutate { s ->
            val exists = s.cats.any { it.id == nc.id }
            s.copy(cats = if (exists) s.cats.map { if (it.id == nc.id) nc else it } else s.cats + nc)
        }
    }

    fun deleteCat(id: String) {
        mutate { s ->
            s.copy(cats = s.cats.map {
                if (it.id == id) it.copy(deleted = true, updatedAt = System.currentTimeMillis()) else it
            })
        }
    }

    fun upsertPlan(p: Plan) {
        val np = p.copy(updatedAt = System.currentTimeMillis())
        mutate { s ->
            val exists = s.plans.any { it.id == np.id }
            s.copy(plans = if (exists) s.plans.map { if (it.id == np.id) np else it } else s.plans + np)
        }
    }

    fun deletePlan(id: String) {
        mutate { s ->
            s.copy(plans = s.plans.map {
                if (it.id == id) it.copy(deleted = true, updatedAt = System.currentTimeMillis()) else it
            })
        }
    }

    /** Remplace/ajoute les repas donnés (leur updatedAt doit déjà être à jour). */
    fun upsertEntries(list: List<Entry>) {
        if (list.isEmpty()) return
        mutate { s ->
            val m = LinkedHashMap<String, Entry>()
            s.entries.forEach { m[it.id] = it }
            list.forEach { m[it.id] = it }
            s.copy(entries = m.values.toList())
        }
    }

    fun markDone(
        id: String,
        givenPate: String,
        givenQty: String,
        note: String,
        doneAt: Long,
        catIds: List<String>? = null,
    ) {
        val e = get(id) ?: return
        upsertEntries(
            listOf(
                e.copy(
                    status = Status.DONE,
                    givenPate = givenPate,
                    givenQty = givenQty,
                    note = note,
                    doneAt = doneAt,
                    catIds = catIds ?: e.catIds,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        )
    }

    fun markSkipped(id: String) {
        val e = get(id) ?: return
        upsertEntries(
            listOf(
                e.copy(
                    status = Status.SKIPPED,
                    givenPate = "",
                    givenQty = "",
                    doneAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        )
    }

    /** Fusionne ce que renvoie le serveur : le plus récent (updatedAt) gagne. */
    fun mergeRemote(remotePlans: List<Plan>, remoteEntries: List<Entry>, remoteCats: List<Cat>): Boolean {
        var changed = false
        mutate { s ->
            val pm = LinkedHashMap<String, Plan>()
            s.plans.forEach { pm[it.id] = it }
            for (p in remotePlans) {
                val l = pm[p.id]
                if (l == null || p.updatedAt > l.updatedAt) {
                    pm[p.id] = p
                    changed = true
                }
            }
            val em = LinkedHashMap<String, Entry>()
            s.entries.forEach { em[it.id] = it }
            for (e in remoteEntries) {
                val l = em[e.id]
                if (l == null || e.updatedAt > l.updatedAt) {
                    em[e.id] = e
                    changed = true
                }
            }
            val cm = LinkedHashMap<String, Cat>()
            s.cats.forEach { cm[it.id] = it }
            for (c in remoteCats) {
                val l = cm[c.id]
                if (l == null || c.updatedAt > l.updatedAt) {
                    cm[c.id] = c
                    changed = true
                }
            }
            if (changed) AppState(pm.values.toList(), em.values.toList(), cm.values.toList()) else s
        }
        return changed
    }

    private fun mutate(f: (AppState) -> AppState) {
        synchronized(lock) {
            val old = _state.value
            val n = f(old)
            if (n !== old) {
                _state.value = n
                save(n)
            }
        }
    }

    private fun save(s: AppState) {
        val f = file ?: return
        runCatching {
            val plans = JSONArray()
            s.plans.forEach { plans.put(it.toJson()) }
            val entries = JSONArray()
            s.entries.forEach { entries.put(it.toJson()) }
            val cats = JSONArray()
            s.cats.forEach { cats.put(it.toJson()) }
            val txt = JSONObject().put("plans", plans).put("entries", entries).put("cats", cats).toString()
            val tmp = File(f.parentFile, "state.json.tmp")
            tmp.writeText(txt)
            if (!tmp.renameTo(f)) {
                f.writeText(txt)
                tmp.delete()
            }
        }
    }
}
