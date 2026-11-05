package com.vrbrowser.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Conservative host-level blocker; not a full Adblock syntax engine. */
class Shield(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("shield", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val builtin = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "taboola.com", "outbrain.com", "scorecardresearch.com", "criteo.com"
    )
    @Volatile private var remote: Set<String> = load()

    fun mode(): Int = prefs.getInt("mode", 1)
    fun setMode(value: Int) { prefs.edit().putInt("mode", value.coerceIn(0, 2)).apply() }
    fun exclusions(): String = prefs.getString("exceptions", "") ?: ""
    fun setExclusions(value: String) { prefs.edit().putString("exceptions", value).apply() }

    private fun hostMatches(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    fun shouldBlock(uri: Uri, mainFrame: Boolean): Boolean {
        if (mainFrame || mode() == 0 || uri.scheme !in listOf("https", "http")) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (exclusions().split(',', '\n').map { it.trim().lowercase() }
                .any { it.isNotEmpty() && hostMatches(host, it) }) return false
        return (builtin + remote).any { hostMatches(host, it) }
    }

    private fun load(): Set<String> = (prefs.getString("domains", "") ?: "")
        .lineSequence().filter { it.isNotBlank() }.toSet()

    fun updateIfDue() {
        if (System.currentTimeMillis() - prefs.getLong("updated", 0L) > 7L * 24 * 60 * 60 * 1000) {
            updateNow(null)
        }
    }

    private fun readLimited(connection: HttpURLConnection, limit: Int): ByteArray {
        return connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                if (total > limit) error("Список превышает лимит 5 МБ")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    fun updateNow(callback: ((String) -> Unit)?) {
        executor.execute {
            val result = try {
                val connection = URL("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 12000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = false
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        error("HTTP ${connection.responseCode}")
                    }
                    val bytes = readLimited(connection, 5_000_000)
                    val domainPattern = Regex("[a-z0-9.-]{3,253}")
                    val domains = bytes.toString(Charsets.UTF_8).lineSequence().mapNotNull { line ->
                        val parts = line.substringBefore('#').trim().split(Regex("\\s+"))
                        if (parts.size >= 2 && parts[0] in setOf("0.0.0.0", "127.0.0.1")) {
                            parts[1].lowercase().takeIf {
                                '.' in it && domainPattern.matches(it) && !it.startsWith('.') && !it.endsWith('.')
                            }
                        } else null
                    }.toSet()
                    if (domains.size < 1000) error("Список выглядит неполным")
                    remote = domains
                    prefs.edit().putString("domains", domains.joinToString("\n"))
                        .putLong("updated", System.currentTimeMillis()).apply()
                    "Обновлено: ${domains.size} доменов"
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                "Не удалось обновить: ${e.message ?: e.javaClass.simpleName}"
            } catch (e: LinkageError) {
                "Ошибка совместимости: ${e.message ?: e.javaClass.simpleName}"
            }
            if (callback != null) Handler(Looper.getMainLooper()).post { callback(result) }
        }
    }
}
