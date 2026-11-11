package be.chatpate

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Les types de pâtée. Pour en ajouter un, il suffit d'ajouter une ligne ici. */
enum class Pate(val key: String, val label: String, val adj: String, val emoji: String) {
    POULET("poulet", "Poulet", "au poulet", "🍗"),
    BOEUF("boeuf", "Bœuf", "au bœuf", "🥩"),
    SAUMON("saumon", "Saumon", "au saumon", "🐟");

    companion object {
        fun from(key: String?): Pate = values().firstOrNull { it.key == key } ?: POULET
    }
}

fun pateLabel(key: String): String = when (key) {
    "sachet" -> "Sachet"
    "auto" -> "Nourriture attribuée selon le chat"
    else -> Pate.values().firstOrNull { it.key == key }?.label ?: key
}

fun pateEmoji(key: String): String = when (key) {
    "boeuf" -> "🥩"
    "saumon" -> "🐟"
    "sachet" -> "🥫"
    "auto" -> "🍽️"
    else -> "🍗"
}

fun defaultFoodIconColor(kind: String): String = when (kind) {
    "boeuf" -> "#FFB84A4A"
    "saumon" -> "#FFE58A24"
    "sachet" -> "#FF74808F"
    else -> "#FF4F8FC9"
}

data class FoodType(
    val id: String,
    val name: String,
    val emoji: String,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("emoji", emoji)
        .put("deleted", deleted).put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject) = FoodType(
            id = o.getString("id"),
            name = o.optString("name", ""),
            emoji = o.optString("emoji", "🥫"),
            deleted = o.optBoolean("deleted", false),
            updatedAt = o.optLong("updatedAt", 0L),
        )
    }
}

data class FoodPattern(
    val id: String,
    val name: String,
    val foodIds: List<String>,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("foodIds", jsonArrayOf(foodIds))
        .put("deleted", deleted).put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject) = FoodPattern(
            id = o.getString("id"),
            name = o.optString("name", ""),
            foodIds = strList(o.optJSONArray("foodIds") ?: o.optJSONArray("typeIds")),
            deleted = o.optBoolean("deleted", false),
            updatedAt = o.optLong("updatedAt", 0L),
        )
    }
}

fun defaultFoodTypes() = listOf(
    FoodType("poulet", "Poulet", "🍗", updatedAt = 1L),
    FoodType("boeuf", "Bœuf", "🥩", updatedAt = 1L),
    FoodType("saumon", "Saumon", "🐟", updatedAt = 1L),
)

fun defaultFoodPatterns() = listOf(
    FoodPattern("poulet-boeuf", "Poulet → Bœuf", listOf("poulet", "boeuf"), updatedAt = 1L),
    FoodPattern("boeuf-poulet", "Bœuf → Poulet", listOf("boeuf", "poulet"), updatedAt = 1L),
)

fun defaultFoodProducts() = listOf(
    FoodProduct("poulet", "Poulet", "poulet", "", "", updatedAt = 1L),
    FoodProduct("boeuf", "Bœuf", "boeuf", "", "", updatedAt = 1L),
    FoodProduct("saumon", "Saumon", "saumon", "", "", updatedAt = 1L),
)

object Status {
    const val PENDING = "pending"
    const val DONE = "done"
    const val SKIPPED = "skipped"
}

data class FoodProduct(
    val id: String,
    val name: String,
    val kind: String,
    val brand: String,
    val packPrice: String,
    val weight: String = "",
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val stockCount: Int? = null,
    val putAside: Boolean = false,
    val packaging: String = "sachet",
    val servingsPerPackage: Int = 1,
    val openServings: Int = 0,
    val catIds: List<String> = emptyList(),
    val iconColor: String = defaultFoodIconColor(kind),
) {
    val pateKey: String
        get() = kind.takeIf { key -> Pate.values().any { it.key == key } } ?: "sachet"

    val packagingLabel: String
        get() = when (packaging) {
            "boite" -> "Boîte"
            "barquette" -> "Barquette"
            else -> "Sachet"
        }

    val availableServings: Long?
        get() {
            val servings = servingsPerPackage.coerceAtLeast(1)
            return stockCount?.toLong()?.times(servings)
                ?.plus(openServings.coerceIn(0, servings - 1))
        }

    fun withStockPortionDelta(delta: Long, timestamp: Long): FoodProduct {
        val servings = servingsPerPackage.coerceAtLeast(1)
        val current = availableServings ?: return this
        val maximum = Int.MAX_VALUE.toLong() * servings
        val total = (current + delta).coerceIn(0L, maximum)
        return copy(
            stockCount = (total / servings).toInt(),
            openServings = (total % servings).toInt(),
            updatedAt = timestamp,
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("kind", kind)
        .put("brand", brand)
        .put("packPrice", packPrice)
        .put("weight", weight)
        .put("deleted", deleted)
        .put("updatedAt", updatedAt)
        .put("stockCount", stockCount ?: JSONObject.NULL)
        .put("putAside", putAside)
        .put("packaging", packaging)
        .put("servingsPerPackage", servingsPerPackage)
        .put("openServings", openServings)
        .put("catIds", jsonArrayOf(catIds))
        .put("iconColor", iconColor)

    companion object {
        fun maxServings(packaging: String, stockSettings: StockSettings? = null): Int = when (packaging) {
            "boite" -> stockSettings?.boiteMaxServings ?: 6
            "barquette" -> stockSettings?.barquetteMaxServings ?: 4
            else -> 1
        }

        fun fromJson(o: JSONObject): FoodProduct {
            val packaging = o.optString("packaging", "sachet").takeIf {
                it in setOf("sachet", "boite", "barquette")
            } ?: "sachet"
            val servingsPerPackage = o.optInt("servingsPerPackage", 1).coerceAtLeast(1)
            return FoodProduct(
                id = o.getString("id"),
                name = o.optString("name", ""),
                kind = o.optString("kind", "sachet"),
                brand = o.optString("brand", ""),
                packPrice = o.optString("packPrice", ""),
                weight = o.optString("weight", ""),
                deleted = o.optBoolean("deleted", false),
                updatedAt = o.optLong("updatedAt", 0L),
                stockCount = if (o.isNull("stockCount")) null else o.optInt("stockCount").coerceAtLeast(0),
                putAside = o.optBoolean("putAside", false),
                packaging = packaging,
                servingsPerPackage = servingsPerPackage,
                openServings = o.optInt("openServings", 0).coerceIn(0, maxOf(servingsPerPackage - 1, 0)),
                catIds = strList(o.optJSONArray("catIds")),
                iconColor = o.optString("iconColor", defaultFoodIconColor(o.optString("kind", "sachet"))),
            )
        }
    }
}

data class FoodAllocation(
    val catId: String,
    val productId: String,
    val servings: Int = 1,
    val inStock: Boolean = true,
    val stockWarning: Boolean = false,
    val lowStock: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("catId", catId)
        .put("productId", productId)
        .put("servings", servings)
        .put("inStock", inStock)
        .put("stockWarning", stockWarning)
        .put("lowStock", lowStock)

    companion object {
        fun fromJson(o: JSONObject) = FoodAllocation(
            catId = o.optString("catId", ""),
            productId = o.optString("productId", ""),
            servings = o.optInt("servings", 1).coerceAtLeast(1),
            inStock = o.optBoolean("inStock", true),
            stockWarning = o.optBoolean("stockWarning", false),
            lowStock = o.optBoolean("lowStock", false),
        )
    }
}

data class StockSettings(
    val warningThreshold: Int = 5,
    val lowThreshold: Int = 2,
    val boiteMaxServings: Int = 6,
    val barquetteMaxServings: Int = 4,
    val updatedAt: Long = 0L,
) {
    fun maxServingsFor(packaging: String): Int = when (packaging) {
        "boite" -> boiteMaxServings.coerceAtLeast(1)
        "barquette" -> barquetteMaxServings.coerceAtLeast(1)
        else -> 1
    }

    fun toJson(): JSONObject = JSONObject()
        .put("warningThreshold", warningThreshold)
        .put("lowThreshold", lowThreshold)
        .put("boiteMaxServings", boiteMaxServings)
        .put("barquetteMaxServings", barquetteMaxServings)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject): StockSettings {
            val warning = o.optInt("warningThreshold", 5).coerceAtLeast(0)
            val boiteMax = o.optInt("boiteMaxServings", 6).coerceAtLeast(1)
            val barquetteMax = o.optInt("barquetteMaxServings", 4).coerceAtLeast(1)
            return StockSettings(
                warningThreshold = warning,
                lowThreshold = o.optInt("lowThreshold", 2).coerceIn(0, warning),
                boiteMaxServings = boiteMax,
                barquetteMaxServings = barquetteMax,
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }
    }
}

data class KibblePackage(
    val id: String,
    val price: String,
    val addedOn: String,
    val finishedOn: String = "",
    val catIds: List<String>,
    val brand: String = "",
    val flavor: String = "",
    val specialty: String = "",
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("price", price)
        .put("addedOn", addedOn)
        .put("finishedOn", finishedOn)
        .put("catIds", jsonArrayOf(catIds))
        .put("brand", brand)
        .put("flavor", flavor)
        .put("specialty", specialty)
        .put("deleted", deleted)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject) = KibblePackage(
            id = o.getString("id"),
            price = o.optString("price", ""),
            addedOn = o.optString("addedOn", ""),
            finishedOn = o.optString("finishedOn", ""),
            catIds = strList(o.optJSONArray("catIds")),
            brand = o.optString("brand", ""),
            flavor = o.optString("flavor", ""),
            specialty = o.optString("specialty", ""),
            deleted = o.optBoolean("deleted", false),
            updatedAt = o.optLong("updatedAt", 0L),
        )
    }
}

/** Un chat. */
data class Cat(
    val id: String,
    val name: String,
    val color: Int = 0,            // index dans la palette (voir Theme.kt)
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val photo: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("color", color)
        .put("deleted", deleted)
        .put("updatedAt", updatedAt)
        .put("photo", photo)

    companion object {
        fun fromJson(o: JSONObject): Cat = Cat(
            id = o.getString("id"),
            name = o.optString("name", ""),
            color = o.optInt("color", 0),
            deleted = o.optBoolean("deleted", false),
            updatedAt = o.optLong("updatedAt", 0L),
            photo = o.optString("photo", ""),
        )
    }
}

private fun strList(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    val out = ArrayList<String>()
    for (i in 0 until arr.length()) out.add(arr.optString(i))
    return out
}

private fun jsonArrayOf(list: List<String>): JSONArray {
    val a = JSONArray()
    list.forEach { a.put(it) }
    return a
}

/** Un programme : "tous les N jours à HH:mm, pâtée X, pour tel(s) chat(s)". */
data class Plan(
    val id: String,
    val timeMin: Int,          // minutes depuis minuit (ex : 8h30 = 510)
    val everyDays: Int,        // 1 = tous les jours, 2 = tous les 2 jours...
    val start: String,         // premier jour, format yyyy-MM-dd
    val pate: String,          // clé de Pate
    val qty: String,           // texte libre : "1 sachet", "80 g"...
    val active: Boolean = true,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val catIds: List<String> = emptyList(),
    val alternatePate: Boolean = false,
    val productId: String = "",
    val patternId: String = "",
    val autoAllocate: Boolean = false,
) {
    fun foodTypeOn(date: LocalDate, patterns: List<FoodPattern>): String {
        val effectivePatternId = patternId.ifEmpty {
            if (!alternatePate) "" else if (pate == Pate.BOEUF.key) "boeuf-poulet" else "poulet-boeuf"
        }
        val pattern = patterns.firstOrNull { it.id == effectivePatternId && !it.deleted }
        if (pattern != null && pattern.foodIds.isNotEmpty()) {
            val startDate = runCatching { LocalDate.parse(start) }.getOrNull() ?: return pate
            val occurrence = ChronoUnit.DAYS.between(startDate, date) / everyDays.coerceAtLeast(1)
            return pattern.foodIds[(occurrence % pattern.foodIds.size).toInt()]
                .takeIf { it == Pate.POULET.key || it == Pate.BOEUF.key } ?: pate
        }
        if (alternatePate) return pateOn(date)
        return pate
    }

    fun foodProductOn(
        date: LocalDate,
        patterns: List<FoodPattern>,
        products: List<FoodProduct>,
    ): FoodProduct? {
        val direct = products.firstOrNull { it.id == productId && !it.deleted && !it.putAside }
        if (direct != null) return direct
        val effectivePatternId = patternId.ifEmpty {
            if (!alternatePate) "" else if (pate == Pate.BOEUF.key) "boeuf-poulet" else "poulet-boeuf"
        }
        val pattern = patterns.firstOrNull { it.id == effectivePatternId && !it.deleted }
            ?: return null
        if (pattern.foodIds.isEmpty()) return null
        val startDate = runCatching { LocalDate.parse(start) }.getOrNull() ?: return null
        val occurrence = ChronoUnit.DAYS.between(startDate, date) / everyDays.coerceAtLeast(1)
        val availableFoods = pattern.foodIds.mapNotNull { foodId ->
            products.firstOrNull { it.id == foodId && !it.deleted && !it.putAside }
        }
        if (availableFoods.isEmpty()) return null
        return availableFoods[(occurrence % availableFoods.size).toInt()]
    }

    fun pateOn(date: LocalDate): String {
        if (!alternatePate) return pate
        val startDate = runCatching { LocalDate.parse(start) }.getOrNull() ?: return pate
        val occurrence = ChronoUnit.DAYS.between(startDate, date) / everyDays.coerceAtLeast(1)
        if (occurrence % 2L == 0L) return pate
        return if (pate == Pate.BOEUF.key) Pate.POULET.key else Pate.BOEUF.key
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("timeMin", timeMin)
        .put("everyDays", everyDays)
        .put("start", start)
        .put("pate", pate)
        .put("qty", qty)
        .put("active", active)
        .put("deleted", deleted)
        .put("updatedAt", updatedAt)
        .put("catIds", jsonArrayOf(catIds))
        .put("alternatePate", alternatePate)
        .put("productId", productId)
        .put("patternId", patternId)
        .put("autoAllocate", autoAllocate)

    companion object {
        fun fromJson(o: JSONObject): Plan = Plan(
            id = o.getString("id"),
            timeMin = o.optInt("timeMin", 720),
            everyDays = o.optInt("everyDays", 1).coerceAtLeast(1),
            start = o.optString("start", ""),
            pate = o.optString("pate", "poulet"),
            qty = o.optString("qty", ""),
            active = o.optBoolean("active", true),
            deleted = o.optBoolean("deleted", false),
            updatedAt = o.optLong("updatedAt", 0L),
            catIds = strList(o.optJSONArray("catIds")),
            alternatePate = o.optBoolean("alternatePate", false),
            productId = o.optString("productId", ""),
            patternId = o.optString("patternId", ""),
            autoAllocate = o.optBoolean("autoAllocate", false),
        )
    }
}

/** Un repas concret (une occurrence d'un programme à une date précise). */
data class Entry(
    val id: String,            // "<idProgramme>_<yyyyMMdd>"
    val planId: String,
    val at: Long,              // heure prévue (millisecondes epoch)
    val pate: String,          // pâtée prévue
    val qty: String,           // quantité prévue
    val status: String = Status.PENDING,
    val givenPate: String = "",   // ce qui a vraiment été donné
    val givenQty: String = "",
    val note: String = "",
    val doneAt: Long = 0L,
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val catIds: List<String> = emptyList(),
    val photo: String = "",
    val productId: String = "",
    val plannedProductId: String = "",
    val foodTypeId: String = "",
    val foodTypeName: String = "",
    val foodTypeEmoji: String = "",
    val givenFoodTypeId: String = "",
    val givenFoodTypeName: String = "",
    val givenFoodTypeEmoji: String = "",
    val permanentlyDeleted: Boolean = false,
    val stockDeductedProductId: String = "",
    val stockDeductedCount: Int = 0,
    val foodAllocations: List<FoodAllocation> = emptyList(),
    val stockDeductedPortions: Map<String, Int> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("planId", planId)
        .put("at", at)
        .put("pate", pate)
        .put("qty", qty)
        .put("status", status)
        .put("givenPate", givenPate)
        .put("givenQty", givenQty)
        .put("note", note)
        .put("doneAt", doneAt)
        .put("deleted", deleted)
        .put("updatedAt", updatedAt)
        .put("catIds", jsonArrayOf(catIds))
        .put("photo", photo)
        .put("productId", productId)
        .put("plannedProductId", plannedProductId)
        .put("foodTypeId", foodTypeId)
        .put("foodTypeName", foodTypeName)
        .put("foodTypeEmoji", foodTypeEmoji)
        .put("givenFoodTypeId", givenFoodTypeId)
        .put("givenFoodTypeName", givenFoodTypeName)
        .put("givenFoodTypeEmoji", givenFoodTypeEmoji)
        .put("permanentlyDeleted", permanentlyDeleted)
        .put("stockDeductedProductId", stockDeductedProductId)
        .put("stockDeductedCount", stockDeductedCount)
        .put("foodAllocations", JSONArray().apply {
            foodAllocations.forEach { put(it.toJson()) }
        })
        .put("stockDeductedPortions", JSONObject().apply {
            stockDeductedPortions.forEach { (productId, portions) -> put(productId, portions) }
        })

    companion object {
        fun fromJson(o: JSONObject): Entry = Entry(
            id = o.getString("id"),
            planId = o.optString("planId", ""),
            at = o.optLong("at", 0L),
            pate = o.optString("pate", "poulet"),
            qty = o.optString("qty", ""),
            status = o.optString("status", Status.PENDING),
            givenPate = o.optString("givenPate", ""),
            givenQty = o.optString("givenQty", ""),
            note = o.optString("note", ""),
            doneAt = o.optLong("doneAt", 0L),
            deleted = o.optBoolean("deleted", false),
            updatedAt = o.optLong("updatedAt", 0L),
            catIds = strList(o.optJSONArray("catIds")),
            photo = o.optString("photo", ""),
            productId = o.optString("productId", ""),
            plannedProductId = o.optString("plannedProductId", ""),
            foodTypeId = o.optString("foodTypeId", ""),
            foodTypeName = o.optString("foodTypeName", ""),
            foodTypeEmoji = o.optString("foodTypeEmoji", ""),
            givenFoodTypeId = o.optString("givenFoodTypeId", ""),
            givenFoodTypeName = o.optString("givenFoodTypeName", ""),
            givenFoodTypeEmoji = o.optString("givenFoodTypeEmoji", ""),
            permanentlyDeleted = o.optBoolean("permanentlyDeleted", false),
            stockDeductedProductId = o.optString("stockDeductedProductId", ""),
            stockDeductedCount = o.optInt("stockDeductedCount", 0).coerceAtLeast(0),
            foodAllocations = o.optJSONArray("foodAllocations")?.let { allocations ->
                (0 until allocations.length()).mapNotNull { index ->
                    runCatching { FoodAllocation.fromJson(allocations.getJSONObject(index)) }.getOrNull()
                }
            }.orEmpty(),
            stockDeductedPortions = o.optJSONObject("stockDeductedPortions")?.let { portions ->
                portions.keys().asSequence().mapNotNull { productId ->
                    val count = portions.optInt(productId, 0).coerceAtLeast(0)
                    productId.takeIf { count > 0 }?.let { it to count }
                }.toMap()
            }.orEmpty(),
        )
    }
}

data class AppState(
    val plans: List<Plan> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val cats: List<Cat> = emptyList(),
    val products: List<FoodProduct> = defaultFoodProducts(),
    val foodTypes: List<FoodType> = defaultFoodTypes(),
    val foodPatterns: List<FoodPattern> = defaultFoodPatterns(),
    val kibblePackages: List<KibblePackage> = emptyList(),
    val stockSettings: StockSettings = StockSettings(),
)

fun parsePlans(arr: JSONArray?): List<Plan> {
    if (arr == null) return emptyList()
    val out = ArrayList<Plan>()
    for (i in 0 until arr.length()) {
        runCatching { out.add(Plan.fromJson(arr.getJSONObject(i))) }
    }
    return out
}

fun parseEntries(arr: JSONArray?): List<Entry> {
    if (arr == null) return emptyList()
    val out = ArrayList<Entry>()
    for (i in 0 until arr.length()) {
        runCatching { out.add(Entry.fromJson(arr.getJSONObject(i))) }
    }
    return out
}

fun parseCats(arr: JSONArray?): List<Cat> {
    if (arr == null) return emptyList()
    val out = ArrayList<Cat>()
    for (i in 0 until arr.length()) {
        runCatching { out.add(Cat.fromJson(arr.getJSONObject(i))) }
    }
    return out
}

fun parseProducts(arr: JSONArray?): List<FoodProduct> {
    if (arr == null) return emptyList()
    val out = ArrayList<FoodProduct>()
    for (i in 0 until arr.length()) {
        runCatching { out.add(FoodProduct.fromJson(arr.getJSONObject(i))) }
    }

    return out
}

fun parseFoodTypes(arr: JSONArray?): List<FoodType> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        runCatching { FoodType.fromJson(arr.getJSONObject(i)) }.getOrNull()
    }
}

fun parseFoodPatterns(arr: JSONArray?): List<FoodPattern> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        runCatching { FoodPattern.fromJson(arr.getJSONObject(i)) }.getOrNull()
    }
}

fun parseKibblePackages(arr: JSONArray?): List<KibblePackage> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        runCatching { KibblePackage.fromJson(arr.getJSONObject(i)) }.getOrNull()
    }
}
