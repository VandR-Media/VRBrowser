package com.vrbrowser.app
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebSettings
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
/** Direct downloads with validated HTTP Range resume. */
object DirectDownloader {
 private val pool=Executors.newFixedThreadPool(2)
 private val active=ConcurrentHashMap.newKeySet<String>()
 private val paused=ConcurrentHashMap.newKeySet<String>()
 private val restartRequested=ConcurrentHashMap.newKeySet<String>()
 private val connections=ConcurrentHashMap<String,HttpURLConnection>()
 private class HttpStatusException(val status:Int):IOException("HTTP ${status}")
 private fun state(c:Context,id:String)=TransferStore.state(c,id).orEmpty()
 private fun isPaused(c:Context,id:String)=paused.contains(id)||state(c,id)=="Пауза"
 private fun isDeleted(c:Context,id:String)=state(c,id)=="Удалено"
 private fun isStopped(c:Context,id:String)=isPaused(c,id)||isDeleted(c,id)
 fun onNetworkLost(){connections.values.forEach{try{it.disconnect()}catch(_:Exception){}}}
 fun pause(c:Context,id:String){paused.add(id);TransferStore.setState(c,id,"Пауза");connections.remove(id)?.disconnect()}
 fun delete(c:Context,id:String){paused.add(id);TransferStore.setState(c,id,"Удалено");connections.remove(id)?.disconnect();if(!active.contains(id))cleanup(c,id)}
 private fun cleanup(c:Context,id:String){val item=TransferStore.entries(c).firstOrNull{it.id==id};File(c.filesDir,"direct_${id}.part").delete();File(c.filesDir,"direct_${id}.meta").delete();try{item?.uri?.takeIf{it.isNotBlank()}?.let{c.contentResolver.delete(Uri.parse(it),null,null)}}catch(_:Exception){};TransferStore.remove(c,id)}
 fun start(context:Context,id:String){if(!active.add(id)){restartRequested.add(id);return};paused.remove(id);pool.execute{try{run(context.applicationContext,id)}finally{active.remove(id);if(restartRequested.remove(id)&&TransferStore.state(context,id)=="Подготовка")start(context,id)}}}
 private fun online(c:Context):Boolean=try{val cm=c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;val n=cm.activeNetwork;n!=null&&cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true}catch(_:Exception){false}
 private fun awaitNetwork(c:Context,id:String):Boolean{val began=SystemClock.elapsedRealtime();while(SystemClock.elapsedRealtime()-began<10*60*1000L){if(isStopped(c,id))return false;if(online(c))return true;TransferStore.setState(c,id,"Ожидание сети");Thread.sleep(1000)};return false}
 private fun readMeta(file:File)=try{JSONObject(file.readText())}catch(_:Exception){JSONObject()}
 private fun connect(c:Context,id:String,entry:TransferStore.Entry,offset:Long,meta:JSONObject):HttpURLConnection{
  val conn=(URL(entry.source).openConnection() as HttpURLConnection).apply{
   connectTimeout=15000;readTimeout=25000;instanceFollowRedirects=true;setRequestProperty("User-Agent",entry.userAgent.takeIf{it.isNotBlank()}?:WebSettings.getDefaultUserAgent(c));setRequestProperty("Accept","*/*");setRequestProperty("Accept-Encoding","identity")
   if(entry.referer.startsWith("http")){setRequestProperty("Referer",entry.referer);try{val ref=Uri.parse(entry.referer);if(ref.scheme in listOf("http","https")&&!ref.authority.isNullOrBlank())setRequestProperty("Origin","${ref.scheme}://${ref.authority}")}catch(_:Exception){}}
   try{CookieManager.getInstance().getCookie(entry.source)?.takeIf{it.isNotBlank()}?.let{setRequestProperty("Cookie",it)}}catch(_:Exception){}
   if(offset>0){setRequestProperty("Range","bytes=${offset}-");meta.optString("etag").takeIf{it.isNotBlank()}?.let{setRequestProperty("If-Range",it)}?:meta.optString("lastModified").takeIf{it.isNotBlank()}?.let{setRequestProperty("If-Range",it)}}
  };connections[id]=conn;return conn
 }
 private fun run(c:Context,id:String){
  val entry=TransferStore.entries(c).firstOrNull{it.id==id}?:return;if(entry.source.isBlank()||entry.uri.isBlank())return
  val part=File(c.filesDir,"direct_${id}.part");val metaFile=File(c.filesDir,"direct_${id}.meta");var progress=entry.progress.coerceIn(0,99);var completed=false
  try{
   for(attempt in 1..6){
    if(isDeleted(c,id)){cleanup(c,id);return};if(isPaused(c,id)){TransferStore.setState(c,id,"Пауза");return};if(!awaitNetwork(c,id)){TransferStore.setState(c,id,"Ожидание сети");return}
    val offset=part.length();val saved=readMeta(metaFile);val conn=connect(c,id,entry,offset,saved)
    try{
     val code=conn.responseCode;if(code==401||code==403)throw HttpStatusException(code)
     if(code==416){val range=conn.getHeaderField("Content-Range").orEmpty();val full=Regex("""bytes \*/(\d+)""").find(range)?.groupValues?.get(1)?.toLongOrNull();if(full!=null&&full==offset){completed=true;break};part.delete();metaFile.delete();throw IOException("Сервер отклонил Range; частичный файл сброшен")}
     if(code!=200&&code!=206)throw HttpStatusException(code)
     var append=code==206&&offset>0
     if(code==206){val range=conn.getHeaderField("Content-Range").orEmpty();val start=Regex("""bytes (\d+)-""").find(range)?.groupValues?.get(1)?.toLongOrNull();if(start==null||start!=offset){part.delete();metaFile.delete();throw IOException("Сервер продолжил не с нужного байтового смещения; начинаю заново")}}
     else if(offset>0){part.delete();metaFile.delete();append=false}
     val newEtag=conn.getHeaderField("ETag").orEmpty();val newModified=conn.getHeaderField("Last-Modified").orEmpty()
     if(offset>0&&saved.optString("etag").isNotBlank()&&newEtag.isNotBlank()&&saved.optString("etag")!=newEtag){part.delete();metaFile.delete();throw IOException("Файл на сервере изменился; начинаю заново")}
     metaFile.writeText(JSONObject().put("etag",newEtag).put("lastModified",newModified).toString())
     val rangeTotal=Regex("""bytes \d+-\d+/(\d+)""").find(conn.getHeaderField("Content-Range").orEmpty())?.groupValues?.get(1)?.toLongOrNull();val total=rangeTotal?:conn.contentLengthLong.takeIf{it>0}
     val began=SystemClock.elapsedRealtime();var runBytes=0L
     conn.inputStream.use{input->FileOutputStream(part,append).use{out->val buffer=ByteArray(32768);while(true){
      if(isStopped(c,id))throw InterruptedIOException("Загрузка остановлена")
      val n=input.read(buffer);if(n<0)break;if(isStopped(c,id))throw InterruptedIOException("Загрузка остановлена")
      out.write(buffer,0,n);val bytes=part.length();runBytes+=n;progress=if(total!=null&&total>0)(bytes*99/total).toInt().coerceIn(0,99)else 0
      val elapsed=(SystemClock.elapsedRealtime()-began).coerceAtLeast(1000L);val speed=runBytes*1000/elapsed;val eta=if(total!=null&&speed>0)((total-bytes).coerceAtLeast(0)/speed)else -1L
      if(!TransferStore.updateIfActive(c,id,entry.name,"Загрузка",progress,bytes,entry.uri,speed,eta,entry.source,entry.referer,entry.userAgent,entry.quality)){if(isDeleted(c,id))cleanup(c,id)else if(isPaused(c,id))TransferStore.setState(c,id,"Пауза");return}
     }}}
     completed=true;break
    }catch(e:Exception){
     if(isDeleted(c,id)){cleanup(c,id);return};if(isPaused(c,id)){TransferStore.setState(c,id,"Пауза");return};if(restartRequested.contains(id))return
     if(e is HttpStatusException&&e.status in listOf(401,403))throw e
     if(attempt==6)throw e
     if(!online(c))TransferStore.setState(c,id,"Ожидание сети")
     Thread.sleep(1000L*(1L shl(attempt-1)))
    }finally{connections.remove(id,conn);conn.disconnect()}
   }
   if(!completed)throw IOException("Ожидание сети");if(isDeleted(c,id)){cleanup(c,id);return};if(isPaused(c,id)){TransferStore.setState(c,id,"Пауза");return}
   val bytes=part.length();c.contentResolver.openOutputStream(Uri.parse(entry.uri),"wt")?.use{out->part.inputStream().use{it.copyTo(out,32768)}}?:error("Не удалось открыть выбранный файл")
   TransferStore.updateIfActive(c,id,entry.name,"Готово",100,bytes,entry.uri,0,-1,entry.source,entry.referer,entry.userAgent,entry.quality);part.delete();metaFile.delete()
  }catch(e:Exception){
   if(isDeleted(c,id)){cleanup(c,id);return};if(isPaused(c,id)){TransferStore.setState(c,id,"Пауза");return};if(restartRequested.contains(id))return
   val status=(e as? HttpStatusException)?.status
   val reason=e.message ?: "сеть"
   val message=when(status){401,403->"Ссылка устарела · HTTP ${status}";else->if(e.message=="Ожидание сети")"Ожидание сети" else "Ошибка: ${reason}"}
   TransferStore.updateIfActive(c,id,entry.name,message,progress,part.length(),entry.uri,0,-1,entry.source,entry.referer,entry.userAgent,entry.quality)
  }
 }
}
