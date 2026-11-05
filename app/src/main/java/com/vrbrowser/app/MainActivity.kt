package com.vrbrowser.app

import android.app.*
import android.os.*
import android.content.*
import android.graphics.Color
import android.view.*
import android.webkit.*
import android.widget.*
import android.text.InputType
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class MainActivity : Activity() {
 private lateinit var root: LinearLayout
 private lateinit var address: EditText
 private lateinit var pages: FrameLayout
 private lateinit var status: TextView
 private val tabs = mutableListOf<WebView>()
 private var current = 0
 private val media = linkedSetOf<String>()
 private val detected = java.util.Collections.synchronizedSet(mutableSetOf<String>())
 private val shield by lazy { Shield(this) }
 private val prefs by lazy { getSharedPreferences("browser", MODE_PRIVATE) }
 private val bookmarks get() = readList("bookmarks")
 private val history get() = readList("history")
 private fun readList(key: String): MutableList<String> {
  val arr = JSONArray(prefs.getString(key,"[]")); return (0 until arr.length()).map { arr.optString(it) }.toMutableList()
 }
 private fun writeList(key: String, items: List<String>) { prefs.edit().putString(key, JSONArray(items.takeLast(300)).toString()).apply() }
 private fun button(s: String, action: () -> Unit): Button = Button(this).apply { text=s; setOnClickListener { action() }; textSize=12f; setAllCaps(false) }
 private fun row(): LinearLayout = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  window.statusBarColor=Color.rgb(19,24,32); window.navigationBarColor=Color.rgb(19,24,32)
  root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(19,24,32)) }; setContentView(root)
  val top=row(); root.addView(top)
  address=EditText(this).apply { setSingleLine(true); hint="Адрес или поиск"; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
  top.addView(address,LinearLayout.LayoutParams(0,dp(52),1f)); top.addView(button("Перейти") { navigate(address.text.toString()) })
  address.setOnEditorActionListener { _,_,_ -> navigate(address.text.toString()); true }
  pages=FrameLayout(this); root.addView(pages,LinearLayout.LayoutParams(-1,0,1f))
  status=TextView(this).apply { setTextColor(Color.LTGRAY); textSize=11f; text="Готов" }; root.addView(status)
  val bottom=row(); root.addView(bottom)
  listOf("◀","▶","+","Вкладки","★","Медиа","⋮").forEachIndexed { i,s ->
   val action: () -> Unit = when(i) { 0->{ { active().takeIf{it.canGoBack()}?.goBack() } }; 1->{ { active().takeIf{it.canGoForward()}?.goForward() } }; 2->{ { addTab("https://www.google.com") } }; 3->{ { showTabs() } }; 4->{ { showBookmarks() } }; 5->{ { showMedia() } }; else->{ { showMenu() } } }
   bottom.addView(button(s,action),LinearLayout.LayoutParams(0,dp(54),1f))
  }
  shield.updateIfDue()
  addTab("https://www.google.com")
 }
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun active()=tabs[current]
 private fun addTab(url:String) {
  val web=WebView(this); web.settings.apply { javaScriptEnabled=true; domStorageEnabled=true; mediaPlaybackRequiresUserGesture=true; builtInZoomControls=true; displayZoomControls=false; allowFileAccess=false; allowContentAccess=false; mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW }
  web.webChromeClient=object:WebChromeClient() {
   override fun onShowCustomView(view:View,callback:CustomViewCallback) { val dialog=Dialog(this@MainActivity,android.R.style.Theme_Black_NoTitleBar_Fullscreen); dialog.setContentView(view); dialog.setOnDismissListener { callback.onCustomViewHidden() }; dialog.show(); view.setOnLongClickListener { dialog.dismiss(); true } }
  }
  web.webViewClient=object:WebViewClient() {
   override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
    val u=request?.url?.toString()
    if(u!=null && Regex("\\.(mp4|mp3|m4a|aac|webm|m3u8|mpd|m4v|ogg)(\\?|$)",RegexOption.IGNORE_CASE).containsMatchIn(u)) detected.add(u)
    if (request != null && shield.shouldBlock(request.url, request.isForMainFrame)) return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
    return super.shouldInterceptRequest(view,request)
   }
   override fun shouldOverrideUrlLoading(v:WebView?,r:WebResourceRequest?):Boolean { val u=r?.url ?:return false; return if(u.scheme=="http"||u.scheme=="https") false else { try { startActivity(Intent(Intent.ACTION_VIEW,u)) } catch (_:Exception){}; true } }
   override fun onPageFinished(v:WebView?,url:String?) { if(v==active()) { address.setText(url); status.text="Готов · найдено медиа: ${media.size}" }; if(url?.startsWith("https://")==true) { val h=history; h.remove(url); h.add(url); writeList("history",h) }; scanPage(v) }
  }
  web.setDownloadListener { url,ua,disposition,mime,_ -> if(url.startsWith("https://")||url.startsWith("http://")) confirmDownload(url,ua,mime,disposition) }
  web.setOnLongClickListener { val hit=web.hitTestResult; val u=hit.extra; if(u!=null && u.startsWith("http")) { media.add(u); status.text="Ссылка добавлена в Медиа"; true } else false }
  tabs.add(web); pages.addView(web); switchTab(tabs.lastIndex); web.loadUrl(url)
 }
 private fun switchTab(index:Int) { current=index; tabs.forEachIndexed { i,v -> v.visibility=if(i==index) View.VISIBLE else View.GONE }; address.setText(active().url ?: ""); media.clear(); detected.clear() }
 private fun navigate(raw:String) { val t=raw.trim(); if(t.isEmpty())return; val u=if(t.startsWith("https://")||t.startsWith("http://")) t else if(t.contains('.')&&!t.contains(' ')) "https://$t" else "https://www.google.com/search?q=${URLEncoder.encode(t,"UTF-8")}"; active().loadUrl(u) }
 private fun scanPage(web:WebView?) { if(web==null)return; web.evaluateJavascript("(function(){return JSON.stringify(Array.from(document.querySelectorAll('video,audio,source,a')).map(e=>e.currentSrc||e.src||e.href).filter(x=>x&&/^(https?:)/.test(x)&&(/\\.(mp4|mp3|m4a|aac|webm|m3u8|mpd)(\\?|#|$)/i.test(x)||e===document.querySelector('video'))).slice(0,150))})()") { raw ->
  try { val decoded=JSONArray("[$raw]").getString(0); val arr=JSONArray(decoded); for(i in 0 until arr.length())media.add(arr.getString(i)); status.text="Готов · найдено медиа: ${media.size}" } catch (_:Exception) {} }
 }
 private fun showMedia() { scanPage(active()); Handler(Looper.getMainLooper()).postDelayed({ media.addAll(detected); val entries=media.toList(); if(entries.isEmpty()) { alert("Медиа","На странице пока не обнаружены прямые ссылки. Запусти воспроизведение и попробуй ещё раз.");return@postDelayed }
  AlertDialog.Builder(this).setTitle("Медиа (${entries.size})").setItems(entries.map { it.take(90) }.toTypedArray()) { _,i -> val url=entries[i]; val choices=arrayOf("Открыть во встроенном плеере","Скачать прямой файл","Поделиться ссылкой","Копировать ссылку")
   AlertDialog.Builder(this).setTitle(url.take(110)).setItems(choices) { _,choice -> when(choice) { 0->startActivity(Intent(this, PlayerActivity::class.java).putExtra("url",url)); 1->confirmDownload(url,"", "", ""); 2->startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,url) },"Поделиться")); 3->(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("Media URL",url)) } }.show()
  }.show() },450) }
 private fun confirmDownload(url:String,ua:String,mime:String,disposition:String) {
  if(Regex("\\.(m3u8|mpd)(\\?|$)",RegexOption.IGNORE_CASE).containsMatchIn(url)) { alert("Потоковый плейлист","HLS/DASH обнаружен, но эта версия не объединяет сегменты и не конвертирует их в MP4/MP3. Можно скопировать ссылку из раздела Медиа."); return }
  AlertDialog.Builder(this).setTitle("Скачать файл?").setMessage(url.take(250)).setPositiveButton("Скачать") { _,_ -> try {
   val req=DownloadManager.Request(Uri.parse(url)).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
   if(ua.isNotBlank())req.addRequestHeader("User-Agent",ua)
   val referer=active().url; if(referer?.startsWith("http")==true)req.addRequestHeader("Referer",referer)
   val name=URLUtil.guessFileName(url,disposition,mime).replace(Regex("[^a-zA-Z0-9._ -]"),"_")
   req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,name)
   (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req); Toast.makeText(this,"Загрузка начата",Toast.LENGTH_SHORT).show()
  } catch(e:Exception) { alert("Ошибка",e.message?:"Не удалось скачать") } }.setNegativeButton("Отмена",null).show()
 }
 private fun showTabs() { AlertDialog.Builder(this).setTitle("Вкладки").setItems(tabs.mapIndexed { i,v -> "${i+1}. ${(v.title?:v.url?:"Новая вкладка").take(55)}" }.toTypedArray()) { _,i -> switchTab(i) }.setPositiveButton("Новая") { _,_->addTab("https://www.google.com") }.setNeutralButton("Закрыть текущую") { _,_-> if(tabs.size>1) { val old=active(); tabs.removeAt(current); pages.removeView(old); old.destroy(); switchTab(tabs.lastIndex) } }.show() }
 private fun showBookmarks() { val list=bookmarks; AlertDialog.Builder(this).setTitle("Закладки").setItems(list.toTypedArray()) { _,i -> navigate(list[i]) }.setPositiveButton("Добавить текущую") { _,_-> val u=active().url; if(u!=null){ list.remove(u); list.add(u); writeList("bookmarks",list) } }.setNegativeButton("Закрыть",null).show() }
 private fun showMenu() { val choices=arrayOf("История","Библиотека загрузок","Обновить","Очистить историю","Очистить данные сайтов","V&R Shield")
  AlertDialog.Builder(this).setItems(choices) { _,i -> when(i) { 0->{ val h=history.reversed(); AlertDialog.Builder(this).setTitle("История").setItems(h.toTypedArray()) { _,j -> navigate(h[j]) }.show() }; 1->startActivity(Intent(this, LibraryActivity::class.java)); 2->active().reload(); 3->writeList("history",emptyList()); 5->showShield(); 4->AlertDialog.Builder(this).setMessage("Удалить cookies и кэш браузера?").setPositiveButton("Удалить") { _,_->CookieManager.getInstance().removeAllCookies(null); tabs.forEach{it.clearCache(true)} }.setNegativeButton("Отмена",null).show() } }.show()
 }
 private fun showShield() {
  val modes=arrayOf("Выключен", "Сбалансированный", "Строгий")
  val current=shield.mode()
  AlertDialog.Builder(this).setTitle("V&R Shield · режим").setSingleChoiceItems(modes,current) { dialog,which -> shield.setMode(which); dialog.dismiss(); Toast.makeText(this,"Режим сохранён",Toast.LENGTH_SHORT).show() }
   .setPositiveButton("Обновить фильтры") { _,_ -> shield.updateNow { result -> runOnUiThread { alert("Фильтры",result) } } }
   .setNeutralButton("Исключения") { _,_ -> val input=EditText(this).apply { setText(shield.exclusions()); hint="example.com, другой-сайт.ru" }; AlertDialog.Builder(this).setTitle("Сайты без блокировки").setView(input).setPositiveButton("Сохранить") { _,_ -> shield.setExclusions(input.text.toString()) }.setNegativeButton("Отмена",null).show() }
   .setNegativeButton("Закрыть",null).show()
 }
 private fun alert(title:String,msg:String) { AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK",null).show() }
 override fun onBackPressed() { if(active().canGoBack())active().goBack() else super.onBackPressed() }
 override fun onDestroy() { tabs.forEach { it.destroy() }; super.onDestroy() }
}
