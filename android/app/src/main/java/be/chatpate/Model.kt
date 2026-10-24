package be.chatpate

import org.json.JSONArray
import org.json.JSONObject

/** Les types de pâtée. Pour en ajouter un, il suffit d'ajouter une ligne ici. */
enum class Pate(val key: String, val label: String, val adj: String, val emoji: String) {
    POULET("poulet", "Poulet", "au poulet", "🍗"),
    BOEUF("boeuf", "Bœuf", "au bœuf", "🥩");

    companion object {
        fun from(key: String?): Pate = values().firstOrNull { it.key == key } ?: POULET
    }
}

object Status {
    const val PENDING = "pending"
    const val DONE = "done"
    const val SKIPPED = "skipped"
}

/** Un chat. */
data class Cat(
    val id: String,
    val name: String,
    val color: Int = 0,            // index dans la palette (voir Theme.kt)
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("color", color)
        .put("deleted", deleted)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject): Cat = Cat(
            id = o.getString("id"),
            name = o.optString("name", ""),
            color = o.optInt("color", 0),
            deleted = o.optBoolean("deleted", false),
            updatedAt = o.optLong("updatedAt", 0L),
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
) {
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
        )
    }
}

data class AppState(
    val plans: List<Plan> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val cats: List<Cat> = emptyList(),
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
