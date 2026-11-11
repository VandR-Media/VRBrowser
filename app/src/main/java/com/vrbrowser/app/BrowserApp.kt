package com.vrbrowser.app
import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter
class BrowserApp : Application(){
 private var networkCallback:ConnectivityManager.NetworkCallback?=null
 override fun onCreate(){
  super.onCreate()
  val previous=Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler{thread,error->try{val trace=StringWriter().also{error.printStackTrace(PrintWriter(it))}.toString().take(18000);getSharedPreferences("diagnostics",MODE_PRIVATE).edit().putString("last_crash",trace).commit()}catch(_:Throwable){};if(previous!=null)previous.uncaughtException(thread,error)else Process.killProcess(Process.myPid())}
  try{val cm=getSystemService(ConnectivityManager::class.java);networkCallback=object:ConnectivityManager.NetworkCallback(){override fun onLost(network:Network){HlsDownloader.onNetworkLost();DirectDownloader.onNetworkLost()}};cm.registerDefaultNetworkCallback(networkCallback!!)}catch(_:Exception){}
  Handler(Looper.getMainLooper()).post{TransferStore.entries(this).filter{it.state in listOf("Загрузка","Подготовка","Ожидание сети")}.forEach{item->TransferStore.setState(this,item.id,"Подготовка");if(item.source.contains(".m3u8",true))HlsDownloader.start(this,item.id)else DirectDownloader.start(this,item.id)}}
 }
}
