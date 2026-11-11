package com.vrbrowser.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Host-based network guard. Supplements the syntax-aware AdFilter engine;
 * it cannot filter cosmetic elements or first-party ads that share the page's host.
 */
class Shield(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("shield", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()
    data class Source(val id:String, val name:String, val url:String)
    val sources = listOf(
        Source("stevenblack", "StevenBlack Unified Hosts", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
        Source("adaway", "AdAway hosts", "https://adaway.org/hosts.txt")
    )
    private val builtin = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "taboola.com", "outbrain.com", "scorecardresearch.com", "criteo.com"
    )
    @Volatile private var remote: Set<String> = loadAll()

    fun mode() = prefs.getInt("mode",1)
    fun setMode(value:Int) { prefs.edit().putInt("mode",value.coerceIn(0,2)).apply() }
    fun exclusions():String = prefs.getString("exceptions","") ?: ""
    fun setExclusions(value:String) { prefs.edit().putString("exceptions",value.take(4096)).apply() }
    fun sourceEnabled(id:String) = prefs.getBoolean("enabled_$id",true)
    fun setSourceEnabled(id:String, enabled:Boolean) {
        if(sources.none { it.id==id })return
        prefs.edit().putBoolean("enabled_$id",enabled).apply()
        remote = loadAll()
    }
    fun lastUpdated(id:String) = prefs.getLong("updated_$id",0L)
    fun ruleCount():Int = builtin.size+remote.size
    private fun match(host:String,domain:String) = host==domain || host.endsWith(".$domain")
    private fun canonicalDomain(input:String):String? {
        val host=input.trim().lowercase().trimEnd('.')
        if(host.length !in 4..253 || host.startsWith('.') || ".." in host || '.' !in host)return null
        if(!host.matches(Regex("[a-z0-9.-]+")))return null
        if(host.split('.').any{ it.isEmpty() || it.length>63 || it.startsWith('-') || it.endsWith('-') })return null
        return host
    }
    fun shouldBlock(uri:Uri,mainFrame:Boolean):Boolean {
        if(mainFrame || mode()==0 || uri.scheme !in listOf("http","https"))return false
        val host=uri.host?.lowercase()?.trimEnd('.') ?:return false
        // Exceptions apply to the resource host. Site-level allowlisting is separate.
        if(exclusions().split(',', '\n').mapNotNull(::canonicalDomain).any{ match(host,it) })return false
        return (builtin+remote).any{match(host,it)}
    }
    private fun key(id:String)="domains_$id"
    private fun parse(text:String):Set<String> {
        val result=LinkedHashSet<String>()
        text.lineSequence().forEach { raw ->
            val line=raw.substringBefore('#').trim()
            if(line.isEmpty())return@forEach
            val parts=line.split(Regex("\\s+"))
            if(parts.size<2 || parts[0] !in setOf("0.0.0.0","127.0.0.1"))return@forEach
            parts.drop(1).mapNotNull(::canonicalDomain).filterNot{
                it=="localhost" || it.endsWith(".localhost") || it=="localdomain"
            }.forEach(result::add)
        }
        return result
    }
    private fun loadAll():Set<String> = sources.asSequence().filter{sourceEnabled(it.id)}
        .flatMap { (prefs.getString(key(it.id),"") ?: "").lineSequence() }
        .mapNotNull(::canonicalDomain).toSet()

    fun updateIfDue() {
        if(sources.any { sourceEnabled(it.id) && System.currentTimeMillis()-lastUpdated(it.id)>7L*86400000L })
            updateNow(null)
    }
    fun updateNow(callback:((String)->Unit)?) {
        worker.execute {
            val notes=ArrayList<String>()
            for(source in sources.filter{sourceEnabled(it.id)}) {
                if(source.url.startsWith("https://").not())continue
                try {
                    val conn=URL(source.url).openConnection() as HttpURLConnection
                    conn.connectTimeout=12000
                    conn.readTimeout=20000
                    conn.instanceFollowRedirects=false
                    try {
                        if(conn.responseCode!=200)error("HTTP ${conn.responseCode}")
                        val output=ByteArrayOutputStream()
                        conn.inputStream.use { input ->
                            val buffer=ByteArray(8192)
                            var size=0
                            while(true) {
                                val n=input.read(buffer)
                                if(n<0)break
                                size+=n
                                if(size>8_000_000)error("Источник превышает 8 МБ")
                                output.write(buffer,0,n)
                            }
                        }
                        val domains=parse(output.toString("UTF-8"))
                        if(domains.size<500)error("Недостаточно правил: ${domains.size}")
                        prefs.edit().putString(key(source.id),domains.joinToString("\n"))
                            .putLong("updated_${source.id}",System.currentTimeMillis()).apply()
                        notes.add("${source.name}: ${domains.size} доменов")
                    } finally { conn.disconnect() }
                } catch(e:Exception) {
                    notes.add("${source.name}: ${e.message ?: "сбой обновления"}")
                }
            }
            remote=loadAll()
            val message=(notes+ "Активных уникальных правил: ${ruleCount()}").joinToString("\n")
            if(callback!=null)Handler(Looper.getMainLooper()).post{callback(message)}
        }
    }
}
