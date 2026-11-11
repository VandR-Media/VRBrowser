package com.vrbrowser.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.os.Process
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import io.github.edsuns.adfilter.AdFilter
import io.github.edsuns.adfilter.DownloadState

class BrowserApp : Application() {
 private var networkCallback:ConnectivityManager.NetworkCallback?=null

 override fun onCreate() {
  super.onCreate()
  installCrashRecorder()

  // Initialize the filter singleton once before the first browser WebView uses it.
  // Subscription downloads and transfer recovery MUST NOT run on the UI thread.
  val filter=try{AdFilter.create(this)}catch(error:Throwable){
   Log.e("V&R Browser","Ad filter singleton initialization failed",error)
   null
  }
  Thread({
   if(filter!=null) {
    try {
     val subscriptions=listOf(
      "AdGuard Base" to "https://filters.adtidy.org/extension/chromium/filters/2.txt",
      "EasyPrivacy Lite" to "https://filters.adtidy.org/extension/chromium/filters/118_optimized.txt",
      "AdGuard Tracking Protection" to "https://filters.adtidy.org/extension/chromium/filters/3.txt",
      "AdGuard Annoyances" to "https://filters.adtidy.org/extension/chromium/filters/14.txt"
     )
     val existing=filter.viewModel.filters.value?.values?.associateBy{it.url}.orEmpty()
     val now=System.currentTimeMillis()
     subscriptions.forEach { (name,url) ->
      val subscription=existing[url] ?: filter.viewModel.addFilter(name,url)
      val expired=subscription.updateTime<=0L ||
       now-subscription.updateTime>7L*24*60*60*1000
      val failed=subscription.downloadState==DownloadState.FAILED ||
       subscription.downloadState==DownloadState.CANCELLED
      if(expired || failed)filter.viewModel.download(subscription.id)
     }
    } catch(error:Throwable) {
     Log.w("V&R Browser","Updating filters failed; continuing without blocking startup",error)
    }
   }
  },"vr-adfilter-updater").start()

  try {
   val manager=getSystemService(ConnectivityManager::class.java)
   networkCallback=object:ConnectivityManager.NetworkCallback() {
    override fun onLost(network:Network) {
     HlsDownloader.onNetworkLost()
     DirectDownloader.onNetworkLost()
    }
   }
   manager.registerDefaultNetworkCallback(networkCallback!!)
  } catch(error:Exception) {
   Log.w("V&R Browser","Network observer unavailable",error)
  }

  Thread({
   try {
    // Recover only jobs which were active before termination; preserve paused jobs.
    TransferStore.entries(this)
     .filter{it.state in setOf("Загрузка","Загрузка аудио","Подготовка","Ожидание сети","Преобразование в MP4")}
     .forEach { item ->
      TransferStore.setState(this,item.id,"Подготовка")
      if(item.source.contains(".m3u8",true))HlsDownloader.start(this,item.id)
      else DirectDownloader.start(this,item.id)
     }
   } catch(error:Throwable) {
    Log.w("V&R Browser","Transfer restore skipped",error)
   }
  },"vr-transfer-recovery").start()
 }

 private fun installCrashRecorder() {
  val previous=Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler { thread,error ->
   try {
    val writer=StringWriter()
    error.printStackTrace(PrintWriter(writer))
    getSharedPreferences("diagnostics",MODE_PRIVATE).edit()
     .putString("last_crash",writer.toString().take(18000))
     .commit()
   } catch(_:Throwable) { }
   if(previous!=null)previous.uncaughtException(thread,error)
   else Process.killProcess(Process.myPid())
  }
 }

 override fun onTerminate() {
  try {
   networkCallback?.let { getSystemService(ConnectivityManager::class.java)
    .unregisterNetworkCallback(it) }
  } catch(_:Exception) { }
  super.onTerminate()
 }
}
