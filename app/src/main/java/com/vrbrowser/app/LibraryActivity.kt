package com.vrbrowser.app

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup

class LibraryActivity : Activity() {
 private val blue = Color.rgb(39, 190, 245)
 private val pink = Color.rgb(238, 77, 171)
 private val surface = Color.rgb(27, 33, 49)
 private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
 private fun mimeForName(name:String):String=when(name.substringAfterLast('.', "").lowercase()) {
  "mp4"->"video/mp4";"ts"->"video/mp2t";"webm"->"video/webm";"mkv"->"video/x-matroska"
  "mp3"->"audio/mpeg";"m4a"->"audio/mp4";"aac"->"audio/aac";"ogg","oga"->"audio/ogg";"opus"->"audio/opus"
  else->"application/octet-stream"
 }
 private fun rounded(color: Int, radius: Int, border: Int? = null) = GradientDrawable().apply {
  setColor(color); cornerRadius = dp(radius).toFloat()
  if (border != null) setStroke(dp(1), border)
 }
 private data class DownloadRow(val title: String, val state: String, val progress: Int, val size: String, val done: Boolean, val details: String = "")
 private val handler = Handler(Looper.getMainLooper())
 private lateinit var list: ListView
 private val actions = mutableListOf<() -> Unit>()
 private val refresh = object : Runnable {
  override fun run() { updateList(); handler.postDelayed(this, 1500) }
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor = Color.rgb(19, 24, 32)
  window.navigationBarColor = Color.rgb(19, 24, 32)
  val layout = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setPadding(dp(14), dp(20), dp(14), dp(12))
   background = getDrawable(R.drawable.vr_wallpaper)
  }
  layout.addView(TextView(this).apply {
   text = "Загрузки"; textSize = 25f; setTextColor(Color.WHITE)
   setTypeface(null, Typeface.BOLD); setPadding(dp(6), 0, 0, dp(6))
  })
  layout.addView(TextView(this).apply {
   text = "V&R Browser  ·  видео, аудио и файлы"
   textSize = 13f; setTextColor(blue); setPadding(dp(6), 0, 0, dp(14))
  })
  list = ListView(this).apply {
   divider = null; dividerHeight = dp(8); setBackgroundColor(Color.TRANSPARENT)
   cacheColorHint = Color.TRANSPARENT
  }
  layout.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
  layout.addView(TextView(this).apply {
   text = "↗  Системные загрузки"; textSize = 15f
   setTextColor(Color.WHITE); gravity = Gravity.CENTER
   background = rounded(surface, 18, blue)
   setOnClickListener { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
  }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
  setContentView(layout)
  list.setOnItemClickListener { _, _, position, _ -> actions.getOrNull(position)?.invoke() }
 }
 override fun onResume() { super.onResume(); handler.post(refresh) }
 override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
 private fun updateList() {
  val rows = mutableListOf<DownloadRow>()
  actions.clear()
  for (item in TransferStore.entries(this)) {
   val size = String.format(java.util.Locale.getDefault(), "%.1f МБ", item.bytes / 1048576.0)
   val speed = if (item.speed > 0) String.format(java.util.Locale.getDefault(), "%.2f МБ/с", item.speed / 1048576.0) else ""
   val eta = if (item.etaSeconds >= 0 && item.progress < 100 && item.state == "Загрузка") {
    val secs = item.etaSeconds
    "Осталось ~${secs / 3600}ч ${(secs % 3600) / 60}м ${secs % 60}с"
   } else ""
   val displayProgress=if(item.state=="Готово")100 else item.progress.coerceIn(0,99)
   rows.add(DownloadRow(item.name, item.state, displayProgress, size, item.state == "Готово", listOf(speed, eta).filter { it.isNotBlank() }.joinToString("  ·  ")))
   actions.add {
    if (item.uri.isNotBlank()) {
     val uri = Uri.parse(item.uri)
     val mime = mimeForName(item.name)
     val expired=item.state.startsWith("Ссылка устарела")
     val options=when{item.state=="Готово"->arrayOf("Открыть","Поделиться","Удалить");expired->arrayOf("Открыть страницу источника","Удалить");else->arrayOf("Продолжить / повторить","Пауза","Удалить")}
     android.app.AlertDialog.Builder(this,R.style.VRDialog).setTitle(item.name).setItems(options){_,choice->
      when(choice){
       0->if(expired){if(item.referer.startsWith("http"))startActivity(Intent(this,MainActivity::class.java).putExtra("open_url",item.referer))else Toast.makeText(this,"Исходная страница неизвестна",Toast.LENGTH_SHORT).show()}
       else if(item.state!="Готово"){TransferStore.setState(this,item.id,"Подготовка");if(item.source.contains(".m3u8",true))HlsDownloader.start(this,item.id)else DirectDownloader.start(this,item.id);updateList()}
       else try{startActivity(Intent(Intent.ACTION_VIEW).apply{setDataAndType(uri,mime);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)})}catch(_:Exception){Toast.makeText(this,"Нет подходящего плеера",Toast.LENGTH_SHORT).show()}
       1->if(expired){try{if(item.source.contains(".m3u8",true))HlsDownloader.delete(this,item.id)else DirectDownloader.delete(this,item.id);updateList()}catch(_:Exception){Toast.makeText(this,"Не удалось удалить",Toast.LENGTH_SHORT).show()}}
       else if(item.state!="Готово"){if(item.source.contains(".m3u8",true))HlsDownloader.pause(this,item.id)else DirectDownloader.pause(this,item.id);updateList()}
       else try{startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type=mime;putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Поделиться"))}catch(_:Exception){Toast.makeText(this,"Не удалось поделиться",Toast.LENGTH_SHORT).show()}
       2->android.app.AlertDialog.Builder(this,R.style.VRDialog).setMessage("Удалить загрузку ${item.name} и её временные данные?").setNegativeButton("Отмена",null).setPositiveButton("Удалить"){_,_->try{if(item.source.contains(".m3u8",true))HlsDownloader.delete(this,item.id)else DirectDownloader.delete(this,item.id);updateList()}catch(_:Exception){Toast.makeText(this,"Не удалось удалить",Toast.LENGTH_SHORT).show()}}.show()
      }
     }.show()
    }
   }
  }
  val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
  try {
   dm.query(DownloadManager.Query()).use { cursor ->
    val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
    val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
    val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
    val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
    val doneCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
    while (cursor.moveToNext()) {
     val id = cursor.getLong(idCol)
     val name = cursor.getString(titleCol) ?: "Файл"
     val status = cursor.getInt(statusCol)
     val total = cursor.getLong(totalCol)
     val done = cursor.getLong(doneCol)
     val pct = if (total > 0) "${done * 100 / total}%" else "${done / 1048576} МБ"
     val state = when(status) {
      DownloadManager.STATUS_SUCCESSFUL -> "Готово"
      DownloadManager.STATUS_FAILED -> "Ошибка"
      DownloadManager.STATUS_PAUSED -> "Приостановлено"
      DownloadManager.STATUS_RUNNING -> "Загрузка $pct"
      else -> "В очереди"
     }
     rows.add(DownloadRow(name, state, if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else if (status == DownloadManager.STATUS_SUCCESSFUL) 100 else 0, if (total > 0) String.format(java.util.Locale.getDefault(), "%.1f МБ", total / 1048576.0) else "", status == DownloadManager.STATUS_SUCCESSFUL))
     actions.add {
      try {
       val uri = dm.getUriForDownloadedFile(id)
       if (uri != null) startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "*/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
       })
      } catch (_: Exception) { Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_SHORT).show() }
     }
    }
   }
  } catch (_: Exception) { }
  if (rows.isEmpty()) { rows.add(DownloadRow("Загрузок пока нет", "Открой видео и нажми скачать", 0, "", false)); actions.add {} }
  list.adapter = object : android.widget.BaseAdapter() {
   override fun getCount() = rows.size
   override fun getItem(position: Int): Any = rows[position]
   override fun getItemId(position: Int) = position.toLong()
   override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    val item = rows[position]
    val card = LinearLayout(this@LibraryActivity).apply {
     orientation = LinearLayout.VERTICAL
     background = rounded(surface, 18, Color.rgb(47, 65, 84))
     setPadding(dp(14), dp(12), dp(14), dp(12))
    }
    // Always display the actual filename, including its extension.
    val friendly = item.title
    card.addView(TextView(this@LibraryActivity).apply {
     text = friendly; textSize = 15f; setTextColor(Color.WHITE)
     maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
     setTypeface(null, Typeface.BOLD)
    })
    card.addView(TextView(this@LibraryActivity).apply {
     text = "${item.state}  ·  ${item.progress}%" + if (item.size.isNotBlank()) "  ·  ${item.size}" else ""
     textSize = 12f; setTextColor(if (item.done) blue else pink)
     setPadding(0, dp(6), 0, dp(8))
    })
    if (item.details.isNotBlank()) card.addView(TextView(this@LibraryActivity).apply {
     text = item.details; textSize = 12f; setTextColor(Color.rgb(165, 202, 226))
     setPadding(0, 0, 0, dp(8))
    })
    card.addView(ProgressBar(this@LibraryActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
     max = 100; progress = item.progress.coerceIn(0,100)
     progressTintList = android.content.res.ColorStateList.valueOf(if (item.done) blue else pink)
     progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(48, 56, 72))
    }, LinearLayout.LayoutParams(-1, dp(5)))
    return card
   }
  }
 }
}
