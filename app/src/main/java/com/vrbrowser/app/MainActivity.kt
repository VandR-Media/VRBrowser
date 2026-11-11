package com.vrbrowser.app

import android.app.*
import android.os.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.webkit.*
import android.widget.*
import android.text.InputType
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import io.github.edsuns.adfilter.AdFilter

class MainActivity : Activity() {
 private val neonBlue = Color.rgb(39, 190, 245)
 private val neonPink = Color.rgb(238, 77, 171)
 private val surface = Color.rgb(27, 33, 49)
 private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
  setColor(color); cornerRadius = dp(radius).toFloat(); if(stroke != null) setStroke(dp(1),stroke)
 }
 private lateinit var root: LinearLayout
 private lateinit var address: EditText
 private lateinit var pages: FrameLayout
 private lateinit var status: TextView
 private lateinit var playerButton: TextView
 private val tabs = mutableListOf<WebView>()
 private var current = 0
 private val media = linkedSetOf<String>()
 private val detected = java.util.Collections.synchronizedSet(linkedSetOf<String>())
 private var pendingDownload: Triple<String,String,String>? = null
 private val knownQualities=mutableMapOf<String,String>()
 private val shield by lazy { Shield(this) }
 private val adFilter by lazy { AdFilter.get(this) }
 private val prefs by lazy { getSharedPreferences("browser", MODE_PRIVATE) }
 private val bookmarks get() = readList("bookmarks")
 private val history get() = readList("history")
 private fun readList(key: String): MutableList<String> {
  val arr = JSONArray(prefs.getString(key,"[]")); return (0 until arr.length()).map { arr.optString(it) }.toMutableList()
 }
 private fun writeList(key: String, items: List<String>) { prefs.edit().putString(key, JSONArray(items.takeLast(300)).toString()).apply() }
 private fun button(s: String, action: () -> Unit): Button = Button(this).apply { text=s; setOnClickListener { action() }; textSize=12f; setAllCaps(false) }
 private fun navButton(icon: String, description: String, action: () -> Unit): TextView = TextView(this).apply {
  text = icon; textSize = 23f; gravity = Gravity.CENTER
  setTextColor(if(description == "Медиа" || description == "Новая вкладка") neonPink else neonBlue)
  contentDescription = description
  background = rounded(surface, 15)
  setOnClickListener { action() }
 }
 private fun row(): LinearLayout = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  window.statusBarColor=Color.rgb(19,24,32); window.navigationBarColor=Color.rgb(19,24,32)
  root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(19,24,32)) }; setContentView(root)
  val top=row().apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(9),dp(8),dp(9),dp(8)) }
  root.addView(top)
  val addressShell=row().apply { gravity=Gravity.CENTER_VERTICAL; background=rounded(surface,22,neonBlue); setPadding(dp(12),0,dp(4),0) }
  top.addView(addressShell,LinearLayout.LayoutParams(0,dp(48),1f))
  val lock=TextView(this).apply { text="⌕"; textSize=23f; setTextColor(neonBlue); gravity=Gravity.CENTER }
  addressShell.addView(lock,LinearLayout.LayoutParams(dp(28),-1))
  address=EditText(this).apply {
   setSingleLine(true); hint="Адрес или поиск"; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
   setTextColor(Color.WHITE); setHintTextColor(Color.rgb(154,167,188)); textSize=15f
   background=null; setSelectAllOnFocus(true); setPadding(dp(5),0,dp(4),0)
  }
  addressShell.addView(address,LinearLayout.LayoutParams(0,-1,1f))
  val go=TextView(this).apply { text="➜"; textSize=24f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
   background=rounded(neonPink,17); contentDescription="Перейти"; setOnClickListener { navigate(address.text.toString()) } }
  addressShell.addView(go,LinearLayout.LayoutParams(dp(42),dp(38)))
  address.setOnEditorActionListener { _,_,_ -> navigate(address.text.toString()); true }
  pages=FrameLayout(this); root.addView(pages,LinearLayout.LayoutParams(-1,0,1f))
  playerButton=TextView(this).apply {
   text="▶  Открыть в V&R Player"; textSize=13f; gravity=Gravity.CENTER
   setTextColor(Color.WHITE); background=rounded(surface,20,neonBlue)
   elevation=dp(6).toFloat(); visibility=View.GONE
   setOnClickListener { openDetectedPlayer() }
  }
  pages.addView(playerButton,FrameLayout.LayoutParams(dp(184),dp(46),Gravity.BOTTOM or Gravity.END).apply {
   setMargins(0,0,dp(14),dp(14))
  })
  status=TextView(this).apply { setTextColor(Color.rgb(156,171,192)); textSize=10f; text="Готов"; setPadding(dp(12),dp(2),0,dp(3)) }; root.addView(status)
  val bottom=row().apply { setPadding(dp(5),dp(5),dp(5),dp(8)); gravity=Gravity.CENTER_VERTICAL }
  root.addView(bottom)
  listOf("‹","›","+","▣","★","♫","⋮").forEachIndexed { i,s ->
   val action: () -> Unit = when(i) { 0->{ { active().takeIf{it.canGoBack()}?.goBack() } }; 1->{ { active().takeIf{it.canGoForward()}?.goForward() } }; 2->{ { addTab("https://www.google.com") } }; 3->{ { showTabs() } }; 4->{ { showBookmarks() } }; 5->{ { showMedia() } }; else->{ { showMenu() } } }
   val descriptions = listOf("Назад", "Вперёд", "Новая вкладка", "Вкладки", "Закладки", "Медиа", "Меню")
   val lp=LinearLayout.LayoutParams(0,dp(46),1f).apply { setMargins(dp(2),0,dp(2),0) }
   bottom.addView(navButton(s, descriptions[i], action),lp)
  }
  // A failed WebView initialization should not terminate the entire app.
  try {
   addTab(intent.getStringExtra("open_url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: "https://www.google.com")
  } catch (error: Throwable) {
   getSharedPreferences("diagnostics", MODE_PRIVATE).edit()
    .putString("last_crash", android.util.Log.getStackTraceString(error).take(18000)).apply()
   status.text = "Не удалось запустить WebView"
   dialogBuilder().setTitle("Ошибка запуска WebView")
    .setMessage("Открой диагностику и скопируй отчёт.")
    .setPositiveButton("Диагностика") { _, _ -> startActivity(Intent(this, DiagnosticActivity::class.java)) }
    .setNegativeButton("Закрыть", null).show()
  }
  try { shield.updateIfDue() } catch (_: Exception) { }

 }
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun active()=tabs[current]
 private fun addTab(url:String) {
  val web=WebView(this); web.settings.apply { javaScriptEnabled=true; domStorageEnabled=true; mediaPlaybackRequiresUserGesture=true; builtInZoomControls=true; displayZoomControls=false; allowFileAccess=false; allowContentAccess=false; mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW }
  try { adFilter.setupWebView(web) } catch (_:Throwable) { }
  web.webChromeClient=object:WebChromeClient() {
   override fun onShowCustomView(view:View,callback:CustomViewCallback) { val dialog=Dialog(this@MainActivity,android.R.style.Theme_Black_NoTitleBar_Fullscreen); dialog.setContentView(view); dialog.setOnDismissListener { callback.onCustomViewHidden() }; dialog.show(); view.setOnLongClickListener { dialog.dismiss(); true } }
  }
  web.webViewClient=object:WebViewClient() {
   override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
    val url=request?.url?.toString()
    if(url!=null && Regex("\\.(mp4|mp3|m4a|aac|webm|m3u8|mpd|m4v|ogg)(\\?|#|$)",RegexOption.IGNORE_CASE).containsMatchIn(url)) {
     detected.add(url)
     if(url.contains(".m3u8",true) && view!=null) runOnUiThread {
      if(!isFinishing && view==active()) { playerButton.visibility=View.VISIBLE; playerButton.bringToFront() }
     }
    }
    if(view!=null && request!=null && shield.mode()!=0) {
     try {
      val result=adFilter.shouldIntercept(view,request)
      if(result.shouldBlock) return result.resourceResponse ?: WebResourceResponse("text/plain","UTF-8",java.io.ByteArrayInputStream(ByteArray(0)))
     } catch (_:Throwable) { }
     if(shield.shouldBlock(request.url,request.isForMainFrame))
      return WebResourceResponse("text/plain","UTF-8",java.io.ByteArrayInputStream(ByteArray(0)))
    }
    return super.shouldInterceptRequest(view,request)
   }
   override fun onPageStarted(v:WebView?,url:String?,favicon:android.graphics.Bitmap?) {
    if(shield.mode()!=0) try { adFilter.performScript(v,url) } catch (_:Throwable) { }
    super.onPageStarted(v,url,favicon)
   }
   override fun shouldOverrideUrlLoading(v:WebView?,r:WebResourceRequest?):Boolean { val u=r?.url ?:return false; return if(u.scheme=="http"||u.scheme=="https") false else { try { startActivity(Intent(Intent.ACTION_VIEW,u)) } catch (_:Exception){}; true } }
   override fun onPageFinished(v:WebView?,url:String?) { if(v==active()) { address.setText(url); status.text="Готов · найдено медиа: ${media.size}"; if(synchronized(detected){detected.any{it.contains(".m3u8",true)}}){playerButton.visibility=View.VISIBLE;playerButton.bringToFront()} }; if(url?.startsWith("https://")==true) { val h=history; h.remove(url); h.add(url); writeList("history",h) }; scanPage(v) }
  }
  web.setDownloadListener { url,ua,disposition,mime,_ -> if(url.startsWith("https://")||url.startsWith("http://")) confirmDownload(url,ua,mime,disposition) }
  web.setOnLongClickListener { val hit=web.hitTestResult; val u=hit.extra; if(u!=null && u.startsWith("http")) { media.add(u); status.text="Ссылка добавлена в Медиа"; true } else false }
  tabs.add(web); pages.addView(web); playerButton.bringToFront(); switchTab(tabs.lastIndex); web.loadUrl(url)
 }
 private fun switchTab(index:Int) { current=index; tabs.forEachIndexed { i,v -> v.visibility=if(i==index) View.VISIBLE else View.GONE }; address.setText(active().url ?: ""); media.clear(); detected.clear(); playerButton.visibility=View.GONE }
 private fun navigate(raw:String) { val t=raw.trim(); if(t.isEmpty())return; val u=if(t.startsWith("https://")||t.startsWith("http://")) t else if(t.contains('.')&&!t.contains(' ')) "https://$t" else "https://www.google.com/search?q=${URLEncoder.encode(t,"UTF-8")}"; active().loadUrl(u) }
 private fun openDetectedPlayer() {
  val urls=synchronized(detected){detected.toList()}
  val hls=urls.filter{it.contains(".m3u8",true)}
  val direct=urls.lastOrNull{Regex("\\.(mp4|m4v|webm)(\\?|#|$)",RegexOption.IGNORE_CASE).containsMatchIn(it)}
  if(hls.isEmpty() && direct==null) { Toast.makeText(this,"Поток ещё не найден. Запусти видео на странице и попробуй снова.",Toast.LENGTH_LONG).show(); return }
  val web=active()
  Thread {
   val master=hls.asReversed().firstOrNull { candidate ->
    try {
     val body=readHlsPlaylist(candidate,web.url.orEmpty())
     body.contains("#EXT-X-STREAM-INF") || body.contains("#EXT-X-MEDIA:TYPE=AUDIO")
    } catch (_:Exception) { false }
   }
   val source=master ?: hls.lastOrNull() ?: direct
   runOnUiThread {
    if(source.isNullOrBlank() || isFinishing) return@runOnUiThread
    startActivity(Intent(this,PlayerActivity::class.java)
     .putExtra("url",source)
     .putExtra("referer",web.url.orEmpty())
     .putExtra("user_agent",web.settings.userAgentString))
   }
  }.start()
 }
 private fun scanPage(web:WebView?) {
  if(web==null)return
  val script = """
   (function() {
    const items = Array.from(document.querySelectorAll('video,audio,source,a'))
      .map(e => ({url:e.currentSrc || e.src || e.href, media:e.tagName === 'VIDEO' || e.tagName === 'AUDIO'}))
      .filter(item => item.url && /^https?:/i.test(item.url) &&
        (/\.(mp4|mp3|m4a|aac|webm|m3u8|mpd|m4v|ogg)(\?|#|$)/i.test(item.url) || item.media))
      .map(item => item.url);
    return JSON.stringify(Array.from(new Set(items)).slice(0,150));
   })()
  """.trimIndent()
  web.evaluateJavascript(script) { raw ->
   try {
    val decoded=JSONArray("[$raw]").getString(0)
    val arr=JSONArray(decoded)
    for(i in 0 until arr.length()) media.add(arr.getString(i))
    status.text="Готов · найдено медиа: ${media.size}"
   } catch (_:Exception) { }
  }
 }
 private fun mediaType(url: String): String = when {
  Regex("\\.m3u8(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url) -> "HLS · потоковое видео"
  Regex("\\.mpd(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url) -> "DASH · потоковое видео"
  Regex("\\.(mp3|aac|m4a|ogg)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url) -> "Аудио"
  else -> "Видео · прямой файл"
 }
 private fun mediaLabel(url: String, index: Int): String {
  val path = Uri.parse(url).path.orEmpty()
  val quality = Regex("(?:^|/)([0-9]{3,4})p?(?:/|$)", RegexOption.IGNORE_CASE).find(path)?.groupValues?.get(1)
  val kind = mediaType(url)
  return "${index + 1}. $kind" + (quality?.let { " · ${it}p" } ?: "")
 }
 private fun prettyDuration(ms: Long): String {
  if (ms <= 0L) return "Длительность неизвестна"
  val sec = ms / 1000L
  return if (sec >= 3600) "%d:%02d:%02d".format(sec/3600,(sec/60)%60,sec%60)
  else "%d:%02d".format(sec/60,sec%60)
 }
 private data class MediaDetails(val quality: String?, val durationMs: Long?, val frame: android.graphics.Bitmap?, val estimatedBytes: Long?)
 private fun prettySize(bytes: Long?): String = when {
  bytes == null || bytes < 1048576L -> "Размер неизвестен"
  bytes >= 1073741824L -> "≈ %.2f ГБ".format(java.util.Locale.US, bytes / 1073741824.0)
  else -> "≈ %.1f МБ".format(java.util.Locale.US, bytes / 1048576.0)
 }
 private fun remoteLength(url: String, referer: String): Long? {
  return try {
   val conn=(java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
    requestMethod="HEAD"; connectTimeout=2500; readTimeout=2500
    setRequestProperty("User-Agent","Mozilla/5.0 (Android) V&R Browser")
    if(referer.startsWith("http"))setRequestProperty("Referer",referer)
     CookieManager.getInstance().getCookie(url)?.takeIf{it.isNotBlank()}?.let{setRequestProperty("Cookie",it)}
   }
   try { if(conn.responseCode in 200..299) conn.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf{it>0L} else null }
   finally {conn.disconnect()}
  } catch (_:Exception) {null}
 }
 private fun estimateHls(url:String, referer:String, depth:Int=0): Pair<Long?,Long?> {
  if(depth>2)return Pair(null,null)
  return try {
   val conn=(java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
    connectTimeout=3500;readTimeout=3500
    setRequestProperty("User-Agent","Mozilla/5.0 (Android) V&R Browser")
    if(referer.startsWith("http"))setRequestProperty("Referer",referer)
   }
   val playlist=try { conn.inputStream.bufferedReader().use {it.readText().take(400000)} } finally {conn.disconnect()}
   if(!playlist.startsWith("#EXTM3U"))return Pair(null,null)
   val lines=playlist.lines()
   val variants=lines.mapIndexedNotNull {i,line ->
    if(!line.startsWith("#EXT-X-STREAM-INF"))null else {
     val bitrate=Regex("(?:AVERAGE-BANDWIDTH|BANDWIDTH)=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
     val child=lines.drop(i+1).firstOrNull{it.isNotBlank()&&!it.startsWith("#")}
     if(bitrate!=null && child!=null) Pair(bitrate,child) else null
    }
   }
   if(variants.isNotEmpty()) {
    if(variants.size!=1) return Pair(null,null)
   val best=variants.single()
    val child=java.net.URL(java.net.URL(url),best.second).toString()
    val nested=estimateHls(child,referer,depth+1)
    return Pair(nested.first, nested.first?.let{(it/1000.0*best.first/8.0).toLong()} ?: nested.second)
   }
   val seconds=Regex("#EXTINF:([0-9]+(?:\\.[0-9]+)?)").findAll(playlist).sumOf{it.groupValues[1].toDoubleOrNull()?:0.0}
   val duration=if(seconds>0)(seconds*1000).toLong() else null
   val segments=lines.filter{it.isNotBlank()&&!it.startsWith("#")}
   val sample=segments.take(3).mapNotNull { segment -> remoteLength(java.net.URL(java.net.URL(url),segment).toString(),referer) }
   val estimate=if(sample.isNotEmpty()&&segments.isNotEmpty())(sample.average()*segments.size).toLong() else null
   Pair(duration,estimate)
  }catch (_:Exception){Pair(null,null)}
 }
 private fun inspectMedia(url: String, referer: String): MediaDetails {
  var quality: String? = null
  var duration: Long? = null
  var frame: android.graphics.Bitmap? = null
  var estimatedBytes: Long? = null
  // Metadata lookup is strictly best effort and never blocks the browser UI.
  if (url.contains(".m3u8",true)) {
   try {
    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
     connectTimeout=3500; readTimeout=3500
     setRequestProperty("User-Agent", "Mozilla/5.0 (Android) V&R Browser")
     if(referer.startsWith("http"))setRequestProperty("Referer",referer)
    }
    try {
     val playlist = conn.inputStream.bufferedReader().use { it.readText().take(350000) }
     if (playlist.startsWith("#EXTM3U")) {
      val resolution = Regex("RESOLUTION=(\\d+)x(\\d+)").find(playlist)
      if (resolution != null && !playlist.contains("#EXT-X-STREAM-INF:")) quality = "${resolution.groupValues[2]}p"
      if (playlist.contains("#EXTINF:")) {
       val seconds = Regex("#EXTINF:([0-9]+(?:\\.[0-9]+)?)").findAll(playlist)
        .sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
       if (seconds > 0.0) duration=(seconds*1000).toLong()
      }
     }
    } finally { conn.disconnect() }
   } catch (_: Exception) { }
  }
  if(url.contains(".m3u8",true)) {
   val estimate=estimateHls(url,referer)
   if(duration==null)duration=estimate.first
   estimatedBytes=estimate.second
  } else if(!url.contains(".mpd",true)) {
   estimatedBytes=remoteLength(url,referer)
  }
  if (!url.contains(".m3u8",true)) try {
   val retriever = android.media.MediaMetadataRetriever()
   try {
    retriever.setDataSource(url, mapOf("User-Agent" to "Mozilla/5.0 (Android)", "Referer" to referer))
    val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
    if (height != null && height > 0) quality="${height}p"
    val length = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    if (length != null && length > 0) duration=length
    frame = retriever.getFrameAtTime(1000000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
   } finally { retriever.release() }
  } catch (_: Exception) { }
  return MediaDetails(quality,duration,frame,estimatedBytes)
 }
 private data class StreamEntry(val url: String, val quality: String? = null, val bitrate: Long? = null)
 private data class HlsVariant(val url:String,val quality:String?,val bitrate:Long?)
 private fun readHlsPlaylist(url:String,referer:String):String {
  val conn=(java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
   connectTimeout=5000; readTimeout=7000; instanceFollowRedirects=true
   setRequestProperty("User-Agent",WebSettings.getDefaultUserAgent(this@MainActivity))
   setRequestProperty("Accept","*/*")
   if(referer.startsWith("http"))setRequestProperty("Referer",referer)
   CookieManager.getInstance().getCookie(url)?.takeIf{it.isNotBlank()}?.let{setRequestProperty("Cookie",it)}
  }
  return try { conn.inputStream.bufferedReader().use{it.readText().take(600000)} } finally { conn.disconnect() }
 }
 private fun expandStreams(url:String,referer:String,depth:Int=0,inheritedQuality:String?=null,inheritedBitrate:Long?=null,visited:Set<String> = emptySet()):List<StreamEntry> {
  if(!url.contains(".m3u8",true))return listOf(StreamEntry(url,inheritedQuality,inheritedBitrate))
  if(depth>4 || url in visited)return listOf(StreamEntry(url,inheritedQuality ?: "Качество неизвестно",inheritedBitrate))
  return try {
   val content=readHlsPlaylist(url,referer)
   if(!content.trimStart().startsWith("#EXTM3U"))return listOf(StreamEntry(url,inheritedQuality ?: "Качество неизвестно",inheritedBitrate))
   val lines=content.lines()
   val variants=lines.mapIndexedNotNull { index,line ->
    val tag=line.trim()
    if(!tag.startsWith("#EXT-X-STREAM-INF:"))null else {
     val child=lines.drop(index+1).firstOrNull{it.isNotBlank()&&!it.trim().startsWith("#")}
     if(child==null)null else {
      val height=Regex("""RESOLUTION=\d+x(\d+)""").find(tag)?.groupValues?.get(1)?.toIntOrNull()
      val name=Regex("NAME=\\"([^\\"]+)\\"").find(tag)?.groupValues?.get(1)
      val namedQuality=name?.let { raw ->
       val match=Regex("""(?i)(\d{3,4})p?""").find(raw)
       match?.let{"${it.groupValues[1]}p"} ?: raw.take(40)
      }
      val quality=height?.let{"${it}p"} ?: namedQuality
      val bitrate=Regex("""(?:AVERAGE-BANDWIDTH|BANDWIDTH)=(\d+)""").find(tag)?.groupValues?.get(1)?.toLongOrNull()
      HlsVariant(java.net.URL(java.net.URL(url),child.trim()).toString(),quality,bitrate)
     }
    }
   }
   if(variants.isEmpty())return listOf(StreamEntry(url,inheritedQuality ?: "Качество неизвестно",inheritedBitrate))
   variants.flatMap { variant ->
    val label=variant.quality ?: inheritedQuality ?: variant.bitrate?.let{"Битрейт %.2f Мбит/с".format(java.util.Locale.getDefault(),it/1000000.0)} ?: "Качество неизвестно"
    expandStreams(variant.url,referer,depth+1,label,variant.bitrate ?: inheritedBitrate,visited+url).map { leaf ->
     leaf.copy(
      quality=if(leaf.quality.isNullOrBlank() || leaf.quality=="Качество неизвестно") label else leaf.quality,
      bitrate=leaf.bitrate ?: variant.bitrate ?: inheritedBitrate
     )
    }
   }.distinctBy{it.url}
  } catch (_:Exception) {
   listOf(StreamEntry(url,inheritedQuality ?: "Качество неизвестно",inheritedBitrate))
  }
 }
 private fun showMedia() {
  scanPage(active())
  Handler(Looper.getMainLooper()).postDelayed({
   media.addAll(detected)
   val urls=media.toList().distinct().filter { it.startsWith("https://") || it.startsWith("http://") }
   if (urls.isEmpty()) { alert("Медиа", "Запусти видео на странице и попробуй снова."); return@postDelayed }
   val pageTitle=active().title?.take(48) ?: "Видео со страницы"
   val referer=active().url.orEmpty()
   Toast.makeText(this,"Определяем варианты качества…",Toast.LENGTH_SHORT).show()
   Thread {
    val entries=urls.flatMap { expandStreams(it,referer) }.distinctBy { it.url }.take(35)
    runOnUiThread {
     if (isFinishing || isDestroyed) return@runOnUiThread
     val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(8),dp(6),dp(8),dp(6)) }
     entries.forEachIndexed { index, entry ->
      val url=entry.url
      if(entry.quality!=null)knownQualities[url]=entry.quality
      val item=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(5),dp(9),dp(5),dp(9)) }
      val thumb=ImageView(this).apply {
       setImageResource(android.R.drawable.ic_media_play)
       background=rounded(surface,10,neonBlue); setPadding(dp(8),dp(8),dp(8),dp(8))
       scaleType=ImageView.ScaleType.CENTER_INSIDE
      }
      item.addView(thumb,LinearLayout.LayoutParams(dp(72),dp(52)))
      val info=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(9),0,dp(3),0) }
      val heading=TextView(this).apply { text="${index+1}. $pageTitle"; textSize=13f; maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; setTextColor(Color.WHITE) }
      val metadata=TextView(this).apply {
       text="${mediaType(url).substringBefore(" · ")} · ${entry.quality ?: "Качество уточняется"} · Определяем размер…"
       textSize=12f; setTextColor(Color.rgb(181,209,231)); maxLines=3
      }
      info.addView(heading); info.addView(metadata)
      item.addView(info,LinearLayout.LayoutParams(0,-2,1f))
      val download=TextView(this).apply {
       text="↓"; textSize=27f; gravity=Gravity.CENTER; setTextColor(neonPink)
       contentDescription="Скачать ${entry.quality ?: "медиа"}"
       setOnClickListener { confirmDownload(url,"","","",entry.quality) }
      }
      item.addView(download,LinearLayout.LayoutParams(dp(42),dp(48)))
      thumb.setOnClickListener { startActivity(Intent(this,PlayerActivity::class.java).putExtra("url",url).putExtra("referer",referer).putExtra("user_agent",active().settings.userAgentString)) }
      info.setOnClickListener { showMediaActions(url,"${index+1}. ${entry.quality ?: mediaType(url)}") }
      list.addView(item)
      list.addView(View(this).apply { setBackgroundColor(Color.rgb(53,64,83)) },LinearLayout.LayoutParams(-1,dp(1)))
      Thread {
       val details=inspectMedia(url,referer)
       runOnUiThread {
        if (isFinishing || isDestroyed) return@runOnUiThread
        val resolution=entry.quality ?: details.quality ?: "Качество неизвестно"
        val size=details.estimatedBytes
        metadata.text="${mediaType(url).substringBefore(" · ")} · $resolution · ${details.durationMs?.let { prettyDuration(it) } ?: "Длительность неизвестна"} · ${prettySize(size)}"
        details.frame?.let { bitmap ->
         thumb.setPadding(0,0,0,0); thumb.scaleType=ImageView.ScaleType.CENTER_CROP
         thumb.setImageBitmap(bitmap)
        }
       }
      }.start()
     }
     val scroll=ScrollView(this).apply { addView(list); layoutParams=ViewGroup.LayoutParams(-1, minOf(dp(470),(resources.displayMetrics.heightPixels*0.58f).toInt())) }
     dialogBuilder().setTitle("Найдено медиа (${entries.size})")
      .setView(scroll).setNegativeButton("Закрыть",null).show()
    }
   }.start()
  },450)
 }
 private fun showMediaActions(url:String, label:String) {
  val choices=arrayOf("Смотреть", "Скачать", "Поделиться ссылкой", "Копировать ссылку", "Технический адрес")
  dialogBuilder().setTitle(label).setItems(choices) { _,choice -> when(choice) {
   0 -> startActivity(Intent(this, PlayerActivity::class.java).putExtra("url",url).putExtra("referer",active().url.orEmpty()).putExtra("user_agent",active().settings.userAgentString))
   1 -> confirmDownload(url,"","","",knownQualities[url])
   2 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,url) },"Поделиться"))
   3 -> (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("Media URL",url))
   4 -> alert("Адрес потока",url.take(1200))
  } }.show()
 }
 private fun confirmDownload(url:String,ua:String,mime:String,disposition:String,quality:String?=null) {
  if(Regex("\\.mpd(\\?|$)",RegexOption.IGNORE_CASE).containsMatchIn(url)) { alert("DASH","Скачивание DASH пока не поддерживается");return }
  val hls=url.contains(".m3u8",true)
  val ext=if(hls)"mp4" else (URLUtil.guessFileName(url,disposition,mime).substringAfterLast('.',"bin").take(8))
  val title=(active().title ?: "VRBrowser").replace(Regex("[^\\p{L}\\p{N}._ -]"),"_").trim().take(65).ifBlank{"VRBrowser"}
  val res=quality?.takeIf{Regex("[0-9]{3,4}p").matches(it)}?.let{"_${it}"} ?: ""
  val name="${title}${res}.$ext"
  if(quality!=null)knownQualities[url]=quality
  pendingDownload=Triple(url,active().url.orEmpty(),name)
  // ACTION_CREATE_DOCUMENT lets the user select the location and name without storage permissions.
  try {
   startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE);type=if(hls)"video/mp4" else (mime.takeIf{it.isNotBlank()}?:"application/octet-stream")
    putExtra(Intent.EXTRA_TITLE,name)
   },4901)
  } catch(e:Exception) { alert("Ошибка выбора места",e.message?:"Нет системного файлового менеджера") }
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode!=4901)return
  val pending=pendingDownload;pendingDownload=null
  val target=data?.data
  if(resultCode!=RESULT_OK || pending==null || target==null)return
  val (url,referer,name)=pending
  dialogBuilder().setTitle("Подтвердить загрузку")
   .setMessage("Файл: $name\n\nМесто сохранения выбрано.\n\nНачать загрузку?")
   .setNegativeButton("Отмена",null)
   .setPositiveButton("Скачать") { _,_->
    try {
     val id=System.currentTimeMillis().toString()
     val grantFlags=(data?.flags?:0) and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
     try { if(grantFlags!=0)contentResolver.takePersistableUriPermission(target,grantFlags) } catch (_:Exception) { }
     val ua=active().settings.userAgentString
     TransferStore.update(this,id,name,"Подготовка",0,0,target.toString(),source=url,referer=referer,userAgent=ua,quality=knownQualities[url])
     if(url.contains(".m3u8",true)) HlsDownloader.start(this,id)
     else {
      // Non-HLS direct files are downloaded to the selected document in the same resumable task framework.
      DirectDownloader.start(this,id)
     }
     startActivity(Intent(this,LibraryActivity::class.java))
    }catch(e:Exception){alert("Ошибка",e.message?:"Не удалось запустить загрузку")}
   }.show()
 }
 private fun showTabs() { dialogBuilder().setTitle("Вкладки").setItems(tabs.mapIndexed { i,v -> "${i+1}. ${(v.title?:v.url?:"Новая вкладка").take(55)}" }.toTypedArray()) { _,i -> switchTab(i) }.setPositiveButton("Новая") { _,_->addTab("https://www.google.com") }.setNeutralButton("Закрыть текущую") { _,_-> if(tabs.size>1) { val old=active(); tabs.removeAt(current); pages.removeView(old); old.destroy(); switchTab(tabs.lastIndex) } }.show() }
 private fun showBookmarks() { val list=bookmarks; dialogBuilder().setTitle("Закладки").setItems(list.toTypedArray()) { _,i -> navigate(list[i]) }.setPositiveButton("Добавить текущую") { _,_-> val u=active().url; if(u!=null){ list.remove(u); list.add(u); writeList("bookmarks",list) } }.setNegativeButton("Закрыть",null).show() }
 private fun showMenu() { val choices=arrayOf("История","Библиотека загрузок","Обновить","Очистить историю","Очистить данные сайтов","V&R Shield","Диагностика")
  dialogBuilder().setItems(choices) { _,i -> when(i) { 0->{ val h=history.reversed(); dialogBuilder().setTitle("История").setItems(h.toTypedArray()) { _,j -> navigate(h[j]) }.show() }; 1->startActivity(Intent(this, LibraryActivity::class.java)); 2->active().reload(); 3->writeList("history",emptyList()); 5->showShield(); 6->startActivity(Intent(this, DiagnosticActivity::class.java)); 4->dialogBuilder().setMessage("Удалить cookies и кэш браузера?").setPositiveButton("Удалить") { _,_->CookieManager.getInstance().removeAllCookies(null); tabs.forEach{it.clearCache(true)} }.setNegativeButton("Отмена",null).show() } }.show()
 }
 private fun showShield() {
  val modes=arrayOf("Выключен", "Сбалансированный", "Строгий")
  val current=shield.mode()
  dialogBuilder().setTitle("V&R Shield · режим").setSingleChoiceItems(modes,current) { dialog,which -> shield.setMode(which); dialog.dismiss(); Toast.makeText(this,"Режим сохранён",Toast.LENGTH_SHORT).show() }
   .setPositiveButton("Обновить фильтры") { _,_ ->
    try { adFilter.viewModel.filters.value?.values?.forEach { adFilter.viewModel.download(it.id) } } catch (_:Throwable) { }
    shield.updateNow { result -> runOnUiThread { alert("Фильтры",result) } }
   }
   .setNeutralButton("Исключения") { _,_ -> val input=EditText(this).apply { setText(shield.exclusions()); hint="example.com, другой-сайт.ru" }; dialogBuilder().setTitle("Сайты без блокировки").setView(input).setPositiveButton("Сохранить") { _,_ -> shield.setExclusions(input.text.toString()) }.setNegativeButton("Отмена",null).show() }
   .setNegativeButton("Закрыть",null).show()
 }
 private fun dialogBuilder(): AlertDialog.Builder = AlertDialog.Builder(this, R.style.VRDialog)
 private fun alert(title:String,msg:String) { dialogBuilder().setTitle(title).setMessage(msg).setPositiveButton("OK",null).show() }
 override fun onBackPressed() { if(active().canGoBack())active().goBack() else super.onBackPressed() }
 override fun onDestroy() { tabs.forEach { it.destroy() }; super.onDestroy() }
}
