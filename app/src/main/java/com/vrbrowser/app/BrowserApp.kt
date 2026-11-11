package com.vrbrowser.app
import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter
import io.github.edsuns.adfilter.AdFilter
class BrowserApp : Application(){
 private var networkCallback:ConnectivityManager.NetworkCallback?=null
 override fun onCreate(){
  super.onCreate()
  try {
   val filter=AdFilter.create(this)
   if(!filter.hasInstallation) {
    val subscriptions=listOf(
     "AdGuard Base" to "https://filters.adtidy.org/extension/chromium/filters/2.txt",
     "EasyPrivacy Lite" to "https://filters.adtidy.org/extension/chromium/filters/118_optimized.txt",
     "AdGuard Tracking Protection" to "https://filters.adtidy.org/extension/chromium/filters/3.txt"
    )
    subscriptions.forEach { (name,url) ->
     val subscription=filter.viewModel.addFilter(name,url)
     filter.viewModel.download(subscription.id)
    }
   }
  } catch(error:Throwable) {
   android.util.Log.w("V&R Browser","Ad filter initialization failed",error)
  }
  val previous=Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler{thread,error->try{val trace=StringWriter().also{error.printStackTrace(PrintWriter(it))}.toString().take(18000);getSharedPreferences("diagnostics",MODE_PRIVATE).edit().putString("last_crash",trace).commit()}catch(_:Throwable){};if(previous!=null)previous.uncaughtException(thread,error)else Process.killProcess(Process.myPid())}
  try{val cm=getSystemService(ConnectivityManager::class.java);networkCallback=object:ConnectivityManager.NetworkCallback(){override fun onLost(network:Network){HlsDownloader.onNetworkLost();DirectDownloader.onNetworkLost()}};cm.registerDefaultNetworkCallback(networkCallback!!)}catch(_:Exception){}
  Handler(Looper.getMainLooper()).post{TransferStore.entries(this).filter{it.state in listOf("Загрузка","Подготовка","Ожидание сети")}.forEach{item->TransferStore.setState(this,item.id,"Подготовка");if(item.source.contains(".m3u8",true))HlsDownloader.start(this,item.id)else DirectDownloader.start(this,item.id)}}
 }
}
