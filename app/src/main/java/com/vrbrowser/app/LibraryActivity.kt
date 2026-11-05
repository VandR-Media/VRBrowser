package com.vrbrowser.app

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import android.database.Cursor

class LibraryActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val heading=TextView(this).apply { text="V&R Browser — загрузки"; textSize=22f; setPadding(20,24,20,24) }
  layout.addView(heading)
  val list=ListView(this)
  layout.addView(list,LinearLayout.LayoutParams(-1,0,1f))
  val open=Button(this).apply { text="Открыть системные загрузки"; setOnClickListener { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } }
  layout.addView(open)
  setContentView(layout)
  val dm=getSystemService(DOWNLOAD_SERVICE) as DownloadManager
  val entries=mutableListOf<Pair<Long,String>>()
  try {
   dm.query(DownloadManager.Query()).use { c ->
    val id=c.getColumnIndex(DownloadManager.COLUMN_ID)
    val name=c.getColumnIndex(DownloadManager.COLUMN_TITLE)
    val status=c.getColumnIndex(DownloadManager.COLUMN_STATUS)
    while(c.moveToNext()) {
     val n=if(name>=0)c.getString(name) ?: "Файл" else "Файл"
     val s=if(status>=0)c.getInt(status) else 0
     entries.add((if(id>=0)c.getLong(id) else -1L) to "$n — ${if(s==DownloadManager.STATUS_SUCCESSFUL) "готово" else if(s==DownloadManager.STATUS_FAILED) "ошибка" else "в процессе"}")
    }
   }
  } catch (_: Exception) {}
  list.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,entries.map { it.second })
  list.setOnItemClickListener { _,_,pos,_ ->
   try { dm.getUriForDownloadedFile(entries[pos].first)?.let { uri ->
    startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri,"*/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
   } } catch (e:Exception) { Toast.makeText(this,"Не удалось открыть файл",Toast.LENGTH_SHORT).show() }
  }
 }
}
