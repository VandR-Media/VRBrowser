package com.vrbrowser.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.widget.*
import android.view.ViewGroup

/** Local-only diagnostic screen; no crash reports are uploaded. */
class DiagnosticActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs = getSharedPreferences("diagnostics", MODE_PRIVATE)
  val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 32, 28, 24); setBackgroundColor(Color.rgb(22,27,36)) }
  val title = TextView(this).apply { text = "V&R Browser · Диагностика"; textSize = 23f; setTextColor(Color.WHITE) }
  layout.addView(title)
  val info = TextView(this).apply { text = prefs.getString("last_crash", "Отчёт об ошибке отсутствует."); setTextColor(Color.WHITE); textSize = 13f; setTextIsSelectable(true) }
  val scroll = ScrollView(this).apply { addView(info) }
  layout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
  val copy = Button(this).apply { text = "Скопировать отчёт"; setOnClickListener {
   val clip = android.content.ClipData.newPlainText("V&R crash report", info.text)
   (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
   Toast.makeText(this@DiagnosticActivity, "Отчёт скопирован", Toast.LENGTH_SHORT).show()
  } }
  layout.addView(copy)
  layout.addView(Button(this).apply { text = "Запустить браузер"; setOnClickListener { startActivity(Intent(this@DiagnosticActivity, MainActivity::class.java)); finish() } })
  setContentView(layout)
 }
}
