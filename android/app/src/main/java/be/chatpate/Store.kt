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
                    val foodTypes = parseFoodTypes(o.optJSONArray("foodTypes")).ifEmpty { defaultFoodTypes() }
                    val products = ensureFoodProducts(
                        parseProducts(o.optJSONArray("products")),
                        foodTypes,
                    )
                    val plans = parsePlans(o.optJSONArray("plans")).map { plan ->
                        val legacyFood = products.firstOrNull {
                            it.id == plan.pate && !it.deleted
                        }
                        if (plan.productId.isEmpty() && plan.patternId.isEmpty() &&
                            !plan.alternatePate && legacyFood != null
                        ) plan.copy(productId = legacyFood.id) else plan
                    }
                    _state.value = AppState(
                        plans = plans,
                        entries = parseEntries(o.optJSONArray("entries")),
                        cats = parseCats(o.optJSONArray("cats")),
                        products = products,
                        foodTypes = foodTypes,
                        foodPatterns = ensureFoodPatterns(parseFoodPatterns(o.optJSONArray("foodPatterns"))),
                        kibblePackages = parseKibblePackages(o.optJSONArray("kibblePackages")),
                    )
                }

            }
        }
    }

    private fun ensureFoodProducts(products: List<FoodProduct>, types: List<FoodType>): List<FoodProduct> {
        val byId = LinkedHashMap<String, FoodProduct>()
        (products + defaultFoodProducts()).forEach {
            if (it.id !in byId) byId[it.id] = it
        }
        types.filterNot { it.deleted }.forEach { type ->
            if (type.id !in byId) {
                byId[type.id] = FoodProduct(
                    id = type.id,
                    name = type.name,
                    kind = when (type.id) {
                        "poulet" -> "poulet"
                        "boeuf" -> "boeuf"
                        else -> "sachet"
                    },
                    brand = "",
                    packPrice = "",
                    updatedAt = type.updatedAt,
                )
            }
        }
        return byId.values.toList()
    }

    private fun ensureFoodPatterns(patterns: List<FoodPattern>): List<FoodPattern> =
        (patterns + defaultFoodPatterns()).distinctBy { it.id }

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

    fun upsertProduct(product: FoodProduct) {
        val updated = product.copy(updatedAt = System.currentTimeMillis())
        mutate { state ->
            val exists = state.products.any { it.id == updated.id }
            state.copy(products = if (exists) {
                state.products.map { if (it.id == updated.id) updated else it }
            } else state.products + updated)
        }
    }

    fun deleteProduct(id: String) {
        mutate { state ->
            val now = System.currentTimeMillis()
            val patterns = state.foodPatterns.map { pattern ->
                if (id !in pattern.foodIds) pattern else {
                    val remainingFoods = pattern.foodIds.filterNot { it == id }
                    pattern.copy(
                        foodIds = remainingFoods,
                        deleted = remainingFoods.size < 2,
                        updatedAt = now,
                    )
                }
            }
            state.copy(products = state.products.map {
                if (it.id == id) it.copy(deleted = true, updatedAt = now) else it
            }, foodPatterns = patterns)
        }
    }

    fun upsertFoodType(type: FoodType) {
        val updated = type.copy(updatedAt = System.currentTimeMillis())
        mutate { state ->
            val exists = state.foodTypes.any { it.id == updated.id }
            state.copy(foodTypes = if (exists) {
                state.foodTypes.map { if (it.id == updated.id) updated else it }
            } else state.foodTypes + updated)
        }
    }

    fun deleteFoodType(id: String) {
        mutate { state ->
            state.copy(foodTypes = state.foodTypes.map {
                if (it.id == id) it.copy(deleted = true, updatedAt = System.currentTimeMillis()) else it
            })
        }
    }

    fun upsertFoodPattern(pattern: FoodPattern) {
        val updated = pattern.copy(updatedAt = System.currentTimeMillis())
        mutate { state ->
            val exists = state.foodPatterns.any { it.id == updated.id }
            state.copy(foodPatterns = if (exists) {
                state.foodPatterns.map { if (it.id == updated.id) updated else it }
            } else state.foodPatterns + updated)
        }
    }

    fun deleteFoodPattern(id: String) {
        mutate { state ->
            state.copy(foodPatterns = state.foodPatterns.map {
                if (it.id == id) it.copy(deleted = true, updatedAt = System.currentTimeMillis()) else it
            })
        }
    }

    fun upsertKibblePackage(pack: KibblePackage) {
        val updated = pack.copy(updatedAt = System.currentTimeMillis())
        mutate { state ->
            val exists = state.kibblePackages.any { it.id == updated.id }
            state.copy(kibblePackages = if (exists) {
                state.kibblePackages.map { if (it.id == updated.id) updated else it }
            } else state.kibblePackages + updated)
        }
    }

    fun deleteKibblePackage(id: String) {
        mutate { state ->
            val now = System.currentTimeMillis()
            state.copy(kibblePackages = state.kibblePackages.map {
                if (it.id == id) it.copy(deleted = true, updatedAt = now) else it
            })
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
        photo: String? = null,
        productId: String? = null,
        foodTypeId: String? = null,
    ) {
        val e = get(id) ?: return
        upsertEntries(
            listOf(
                e.copy(
                    status = Status.DONE,
                    deleted = false,
                    permanentlyDeleted = false,
                    givenPate = givenPate,
                    givenQty = givenQty,
                    note = note,
                    doneAt = doneAt,
                    catIds = catIds ?: e.catIds,
                    photo = photo ?: e.photo,
                    productId = productId ?: e.productId,
                    givenFoodTypeId = foodTypeId ?: e.givenFoodTypeId,
                    givenFoodTypeName = foodTypeId?.let { typeId ->
                        _state.value.foodTypes.firstOrNull { it.id == typeId }?.name
                    } ?: e.givenFoodTypeName,
                    givenFoodTypeEmoji = foodTypeId?.let { typeId ->
                        _state.value.foodTypes.firstOrNull { it.id == typeId }?.emoji
                    } ?: e.givenFoodTypeEmoji,
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
                    deleted = false,
                    permanentlyDeleted = false,
                    givenPate = "",
                    givenQty = "",
                    doneAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        )
    }

    fun deleteEntry(id: String) {
        mutate { state ->
            val entry = state.entries.firstOrNull { it.id == id } ?: return@mutate state
            if (entry.status == Status.PENDING) return@mutate state
            val now = System.currentTimeMillis()
            val plannedTypeId = entry.foodTypeId.ifEmpty { entry.pate }
            val existingProduct = state.products.firstOrNull {
                it.id == entry.productId && !it.deleted && it.pateKey == plannedTypeId
            }
            val plannedProductId = entry.plannedProductId.ifEmpty {
                existingProduct?.id ?: state.products.firstOrNull {
                    !it.deleted && (it.id == plannedTypeId || it.pateKey == plannedTypeId)
                }?.id.orEmpty()
            }
            val future = entry.at > now
            val cleared = entry.copy(
                status = Status.PENDING,
                givenPate = "",
                givenQty = "",
                note = "",
                doneAt = 0L,
                deleted = !future,
                updatedAt = now,
                photo = "",
                productId = plannedProductId,
                plannedProductId = plannedProductId,
                givenFoodTypeId = "",
                givenFoodTypeName = "",
                givenFoodTypeEmoji = "",
            )
            state.copy(entries = state.entries.map { if (it.id == id) cleared else it })
        }
    }

    fun permanentlyDeleteEntry(id: String) {
        mutate { state ->
            val entry = state.entries.firstOrNull { it.id == id } ?: return@mutate state
            val now = System.currentTimeMillis()
            val removed = entry.copy(
                status = Status.PENDING,
                givenPate = "",
                givenQty = "",
                note = "",
                doneAt = 0L,
                deleted = true,
                updatedAt = now,
                photo = "",
                givenFoodTypeId = "",
                givenFoodTypeName = "",
                givenFoodTypeEmoji = "",
                productId = entry.plannedProductId.ifEmpty { entry.foodTypeId },
                catIds = emptyList(),
                permanentlyDeleted = true,
            )
            state.copy(entries = state.entries.map { if (it.id == id) removed else it })
        }
    }

    /** Fusionne ce que renvoie le serveur : le plus récent (updatedAt) gagne. */
    fun mergeRemote(
        remotePlans: List<Plan>,
        remoteEntries: List<Entry>,
        remoteCats: List<Cat>,
        remoteProducts: List<FoodProduct> = emptyList(),
        remoteFoodTypes: List<FoodType> = emptyList(),
        remoteFoodPatterns: List<FoodPattern> = emptyList(),
        remoteKibblePackages: List<KibblePackage> = emptyList(),
    ): Boolean {
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
            val fm = LinkedHashMap<String, FoodProduct>()
            s.products.forEach { fm[it.id] = it }
            for (product in remoteProducts) {
                val local = fm[product.id]
                if (local == null || product.updatedAt > local.updatedAt) {
                    fm[product.id] = product
                    changed = true
                }
            }
            val tm = LinkedHashMap<String, FoodType>()
            s.foodTypes.forEach { tm[it.id] = it }
            for (type in remoteFoodTypes) {
                val local = tm[type.id]
                if (local == null || type.updatedAt > local.updatedAt) {
                    tm[type.id] = type
                    changed = true
                }
            }
            val patternMap = LinkedHashMap<String, FoodPattern>()
            s.foodPatterns.forEach { patternMap[it.id] = it }
            for (pattern in remoteFoodPatterns) {
                val local = patternMap[pattern.id]
                if (local == null || pattern.updatedAt > local.updatedAt) {
                    patternMap[pattern.id] = pattern
                    changed = true
                }
            }
            val packageMap = LinkedHashMap<String, KibblePackage>()
            s.kibblePackages.forEach { packageMap[it.id] = it }
            for (pack in remoteKibblePackages) {
                val local = packageMap[pack.id]
                if (local == null || pack.updatedAt > local.updatedAt) {
                    packageMap[pack.id] = pack
                    changed = true
                }
            }
            if (changed) s.copy(
                plans = pm.values.toList(),
                entries = em.values.toList(),
                cats = cm.values.toList(),
                products = fm.values.toList(),
                foodTypes = tm.values.toList(),
                foodPatterns = patternMap.values.toList(),
                kibblePackages = packageMap.values.toList(),
            ) else s
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
            val products = JSONArray()
            s.products.forEach { products.put(it.toJson()) }
            val foodTypes = JSONArray()
            s.foodTypes.forEach { foodTypes.put(it.toJson()) }
            val foodPatterns = JSONArray()
            s.foodPatterns.forEach { foodPatterns.put(it.toJson()) }
            val kibblePackages = JSONArray()
            s.kibblePackages.forEach { kibblePackages.put(it.toJson()) }
            val txt = JSONObject().put("plans", plans).put("entries", entries).put("cats", cats)
                .put("products", products).put("foodTypes", foodTypes).put("foodPatterns", foodPatterns)
                .put("kibblePackages", kibblePackages).toString()
            val tmp = File(f.parentFile, "state.json.tmp")
            tmp.writeText(txt)
            if (!tmp.renameTo(f)) {
                f.writeText(txt)
                tmp.delete()
            }
        }
    }
}
