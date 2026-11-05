package com.vrbrowser.app

import android.app.Application
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter

class BrowserApp : Application() {
 override fun onCreate() {
  super.onCreate()
  val previous = Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler { thread, error ->
   try {
    val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().take(18000)
    getSharedPreferences("diagnostics", MODE_PRIVATE).edit().putString("last_crash", trace).commit()
   } catch (_: Throwable) { }
   if (previous != null) previous.uncaughtException(thread, error) else Process.killProcess(Process.myPid())
  }
 }
}
