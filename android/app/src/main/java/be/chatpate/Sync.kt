package be.chatpate

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Synchronisation (optionnelle) avec ton petit serveur maison.
 * Si aucune adresse n'est renseignée dans les réglages, l'appli fonctionne 100 % en local.
 */
object Sync {
    private val running = AtomicBoolean(false)
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    fun run(ctx: Context, timeoutMs: Int = 8000): Boolean {
        val app = ctx.applicationContext
        val base = Prefs.serverUrl(app).trim().trimEnd('/')
        if (base.isEmpty()) {
            _status.value = ""
            return false
        }
        if (!running.compareAndSet(false, true)) return false
        var conn: HttpURLConnection? = null
        try {
            val snap = Store.snapshot()
            val plans = JSONArray()
            snap.plans.forEach { plans.put(it.toJson()) }
            val entries = JSONArray()
            snap.entries.forEach { entries.put(it.toJson()) }
            val cats = JSONArray()
            snap.cats.forEach { cats.put(it.toJson()) }
            val products = JSONArray()
            snap.products.forEach { products.put(it.toJson()) }
            val foodTypes = JSONArray()
            snap.foodTypes.forEach { foodTypes.put(it.toJson()) }
            val foodPatterns = JSONArray()
            snap.foodPatterns.forEach { foodPatterns.put(it.toJson()) }
            val kibblePackages = JSONArray()
            snap.kibblePackages.forEach { kibblePackages.put(it.toJson()) }
            val body = JSONObject().put("plans", plans).put("entries", entries).put("cats", cats)
                .put("products", products).put("foodTypes", foodTypes).put("foodPatterns", foodPatterns)
                .put("kibblePackages", kibblePackages).toString()

            val url = if (base.startsWith("http://") || base.startsWith("https://")) base else "http://$base"
            conn = URL("$url/api/sync").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val token = Prefs.token(app)
            if (token.isNotEmpty()) conn.setRequestProperty("X-Token", token)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code == 401) {
                _status.value = "Serveur : code secret refusé"
                return false
            }
            if (code != 200) {
                _status.value = "Serveur : erreur $code"
                return false
            }
            val txt = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val o = JSONObject(txt)
            val changed = Store.mergeRemote(
                parsePlans(o.optJSONArray("plans")),
                parseEntries(o.optJSONArray("entries")),
                parseCats(o.optJSONArray("cats")),
                parseProducts(o.optJSONArray("products")),
                parseFoodTypes(o.optJSONArray("foodTypes")),
                parseFoodPatterns(o.optJSONArray("foodPatterns")),
                parseKibblePackages(o.optJSONArray("kibblePackages")),
            )
            if (changed) {
                Planner.reconcile()
                Alarms.reschedule(app)
            }
            _status.value = "Synchronisé à " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            return true
        } catch (e: Exception) {
            _status.value = "Serveur injoignable (mode hors-ligne)"
            return false
        } finally {
            conn?.disconnect()
            running.set(false)
        }
    }
}
