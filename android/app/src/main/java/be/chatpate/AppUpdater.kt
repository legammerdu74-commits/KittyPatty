package be.chatpate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

data class AppUpdate(
    val tagName: String,
    val versionName: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
)

data class AppUpdateState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val message: String = "",
    val release: AppUpdate? = null,
    val downloaded: Boolean = false,
)

object AppUpdater {
    private const val OWNER = "legammerdu74-commits"
    private const val REPO = "KittyPatty"
    private const val RELEASES_URL =
        "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=100"
    private const val PREFERENCES = "github_updates"
    private const val DOWNLOADED_TAG = "downloaded_tag"
    private const val MAX_APK_BYTES = 200L * 1024 * 1024

    private val checking = AtomicBoolean(false)
    private val downloading = AtomicBoolean(false)
    private val _state = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = _state

    fun check(context: Context) {
        val app = context.applicationContext
        val preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!checking.compareAndSet(false, true)) return
        _state.value = _state.value.copy(checking = true, message = "Vérification des mises à jour…")
        thread(name = "github-update-check") {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "KittyPatty-Android")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("GitHub a répondu $code")
                val releases = JSONArray(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                val installedVersion = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
                val installedParts = versionParts(installedVersion)
                    ?: throw IllegalStateException("Version installée non reconnue : $installedVersion")
                val update = (0 until releases.length())
                    .asSequence()
                    .map { releases.getJSONObject(it) }
                    .filterNot { it.optBoolean("draft", false) }
                    .mapNotNull { release ->
                        val tag = release.optString("tag_name")
                        val releaseParts = versionParts(tag) ?: return@mapNotNull null
                        if (compareVersions(releaseParts, installedParts) <= 0) return@mapNotNull null
                        val apk = release.optJSONArray("assets")
                            ?.let { assets ->
                                (0 until assets.length()).asSequence()
                                    .map { assets.getJSONObject(it) }
                                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                            }
                            ?.optString("browser_download_url")
                            ?.takeIf { it.startsWith("https://github.com/") }
                            ?: return@mapNotNull null
                        AppUpdate(
                            tagName = tag,
                            versionName = tag.removePrefix("v").removePrefix("V"),
                            title = release.optString("name").ifBlank { "Kitty Patty $tag" },
                            notes = release.optString("body").trim().take(1200),
                            apkUrl = apk,
                        )
                    }
                    .firstOrNull()
                val current = if (update == null) {
                    AppUpdateState(message = "L’application est à jour (v$installedVersion).")
                } else {
                    val alreadyDownloaded = preferences.getString(DOWNLOADED_TAG, null) == update.tagName &&
                        updateFile(app).isFile
                    AppUpdateState(
                        message = "La version ${update.versionName} est disponible.",
                        release = update,
                        downloaded = alreadyDownloaded,
                    )
                }
                _state.value = current
            } catch (error: Exception) {
                _state.value = AppUpdateState(
                    message = "Vérification impossible : ${error.message ?: "erreur réseau"}",
                )
            } finally {
                connection?.disconnect()
                checking.set(false)
            }
        }
    }

    fun downloadAndInstall(context: Context, release: AppUpdate) {
        val app = context.applicationContext
        if (!downloading.compareAndSet(false, true)) return
        _state.value = _state.value.copy(downloading = true, message = "Téléchargement de Kitty Patty ${release.versionName}…")
        thread(name = "github-update-download") {
            try {
                val preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                val apk = updateFile(app)
                if (preferences.getString(DOWNLOADED_TAG, null) != release.tagName || !apk.isFile) {
                    downloadApk(release.apkUrl, apk)
                    preferences.edit().putString(DOWNLOADED_TAG, release.tagName).apply()
                }
                _state.value = _state.value.copy(
                    downloading = false,
                    downloaded = true,
                    message = "Mise à jour téléchargée. Android va demander l’autorisation d’installation.",
                )
                openInstaller(app, apk)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    downloading = false,
                    message = "Téléchargement impossible : ${error.message ?: "erreur inconnue"}",
                )
            } finally {
                downloading.set(false)
            }
        }
    }

    private fun downloadApk(downloadUrl: String, destination: File) {
        val url = URL(downloadUrl)
        require(url.protocol == "https" && url.host.equals("github.com", ignoreCase = true)) {
            "l’adresse de téléchargement GitHub est invalide"
        }
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "KittyPatty-Android")
            val code = connection.responseCode
            require(code in 200..299) { "GitHub a répondu $code" }
            require(connection.url.host in setOf("github.com", "release-assets.githubusercontent.com")) {
                "le téléchargement a été redirigé vers un hôte inattendu"
            }
            val contentLength = connection.contentLengthLong
            require(contentLength <= MAX_APK_BYTES) { "le fichier APK dépasse la taille autorisée" }
            destination.parentFile?.mkdirs()
            val temporary = File(destination.parentFile, "${destination.name}.part")
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_APK_BYTES) { "le fichier APK dépasse la taille autorisée" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(temporary.length() >= 4L) { "le fichier téléchargé est vide ou incomplet" }
            val signature = temporary.inputStream().use { input -> ByteArray(2).also { input.read(it) } }
            require(signature[0] == 'P'.code.toByte() && signature[1] == 'K'.code.toByte()) {
                "le fichier téléchargé n’est pas un APK valide"
            }
            if (destination.exists()) destination.delete()
            require(temporary.renameTo(destination)) { "impossible d’enregistrer le fichier APK" }
        } finally {
            connection.disconnect()
        }
    }

    private fun openInstaller(context: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _state.value = _state.value.copy(
                message = "Autorise « Kitty Patty » à installer des applications, puis appuie de nouveau sur Installer.",
            )
            val settings = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val installer = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(installer)
    }

    private fun updateFile(context: Context) = File(File(context.filesDir, "updates"), "KittyPatty-update.apk")

    private fun versionParts(value: String): List<Int>? {
        val version = Regex("""^[vV]?(\d+(?:\.\d+){1,3})(?:[-+].*)?$""")
            .find(value.trim())?.groupValues?.get(1) ?: return null
        return version.split('.').map { it.toIntOrNull() ?: return null }
    }

    private fun compareVersions(left: List<Int>, right: List<Int>): Int {
        for (index in 0 until maxOf(left.size, right.size)) {
            val comparison = (left.getOrElse(index) { 0 }).compareTo(right.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }
}
