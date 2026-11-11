package com.vrbrowser.app
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebSettings
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs
/** Finite clear MPEG-TS HLS downloader with segment-level resume. */
object HlsDownloader {
 private val pool=Executors.newFixedThreadPool(2)
 private val running=ConcurrentHashMap.newKeySet<String>()
 private val paused=ConcurrentHashMap.newKeySet<String>()
 private val restartRequested=ConcurrentHashMap.newKeySet<String>()
 private val connections=ConcurrentHashMap<String,HttpURLConnection>()
 private val main=Handler(Looper.getMainLooper())
 private class HttpStatusException(val status:Int):IOException("HTTP §{status}")
 private fun state(c:Context,id:String)=TransferStore.state(c,id).orEmpty()
 private fun isPaused(c:Context,id:String)=paused.contains(id)||state(c,id)=="Пауза"
 private fun isDeleted(c:Context,id:String)=state(c,id)=="Удалено"
 private fun isStopped(c:Context,id:String)=isPaused(c,id)||isDeleted(c,id)
 fun onNetworkLost(){connections.values.forEach{try{it.disconnect()}catch(_:Exception){}}}
 fun start(context:Context,id:String,callback:(String)->Unit={}){paused.remove(id);if(!running.add(id)){restartRequested.add(id);return};val app=context.applicationContext;pool.execute{try{perform(app,id,callback)}finally{running.remove(id);if(restartRequested.remove(id)&&TransferStore.state(app,id)=="Подготовка")start(app,id,callback)}}}
 fun pause(context:Context,id:String){paused.add(id);TransferStore.setState(context,id,"Пауза");connections.remove(id)?.disconnect()}
 fun delete(context:Context,id:String){paused.add(id);TransferStore.setState(context,id,"Удалено");connections.remove(id)?.disconnect();if(!running.contains(id))cleanup(context,id)}
 private fun cleanup(c:Context,id:String){val item=TransferStore.entries(c).firstOrNull{it.id==id};try{item?.uri?.takeIf{it.isNotBlank()}?.let{c.contentResolver.delete(Uri.parse(it),null,null)}}catch(_:Exception){};File(c.filesDir,"hls_§{id}").deleteRecursively();File(c.filesDir,"direct_§{id}.part").delete();File(c.filesDir,"direct_§{id}.meta").delete();TransferStore.remove(c,id)}
 private fun online(c:Context):Boolean=try{val cm=c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;val n=cm.activeNetwork;n!=null&&cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true}catch(_:Exception){false}
 private fun awaitNetwork(c:Context,id:String):Boolean{val began=SystemClock.elapsedRealtime();while(SystemClock.elapsedRealtime()-began<10*60*1000L){if(isStopped(c,id))return false;if(online(c))return true;TransferStore.setState(c,id,"Ожидание сети");Thread.sleep(1000)};return false}
 private fun connect(c:Context,id:String,url:URL,entry:TransferStore.Entry):HttpURLConnection{
  val conn=(url.openConnection() as HttpURLConnection).apply{
   connectTimeout=15000;readTimeout=25000;instanceFollowRedirects=true
   setRequestProperty("User-Agent",entry.userAgent.takeIf{it.isNotBlank()}?:WebSettings.getDefaultUserAgent(c));setRequestProperty("Accept","*/*");setRequestProperty("Accept-Encoding","identity")
   if(entry.referer.startsWith("http")){setRequestProperty("Referer",entry.referer);try{val ref=Uri.parse(entry.referer);if(ref.scheme in listOf("http","https")&&!ref.authority.isNullOrBlank())setRequestProperty("Origin","§{ref.scheme}://§{ref.authority}")}catch(_:Exception){}}
   try{CookieManager.getInstance().getCookie(url.toString())?.takeIf{it.isNotBlank()}?.let{setRequestProperty("Cookie",it)}}catch(_:Exception){}
  };connections[id]=conn;return conn
 }
 private fun playlist(c:Context,id:String,url:URL,entry:TransferStore.Entry):String{
  var last:Exception?=null
  for(attempt in 1..5){if(isStopped(c,id))throw InterruptedIOException("Загрузка остановлена");if(!awaitNetwork(c,id))throw IOException("Ожидание сети");val conn=connect(c,id,url,entry)
   try{val code=conn.responseCode;if(code!=200)throw HttpStatusException(code);val result=StringBuilder();conn.inputStream.bufferedReader().use{reader->while(true){val line=reader.readLine()?:break;result.append(line).append('\n');if(result.length>2000000)throw IOException("Слишком большой HLS-плейлист")}};val body=result.toString();check(body.trimStart().startsWith("#EXTM3U")){"Некорректный HLS-плейлист"};return body}
   catch(e:Exception){if(e is HttpStatusException&&e.status in listOf(401,403,404,410))throw e;last=e;if(attempt<5)Thread.sleep(1000L*(1L shl(attempt-1)))}
   finally{connections.remove(id,conn);conn.disconnect()}
  };throw last?:IOException("Не удалось получить HLS-плейлист")
 }
 private data class Variant(val url:URL,val height:Int?,val bandwidth:Long)
 private fun selectVariant(body:String,base:URL,quality:String?):URL{
  val lines=body.lines();val variants=mutableListOf<Variant>()
  for(i in lines.indices){val line=lines[i].trim();if(!line.startsWith("#EXT-X-STREAM-INF:"))continue;val child=lines.drop(i+1).firstOrNull{it.isNotBlank()&&!it.trim().startsWith("#")}?:continue
   val height=Regex("""RESOLUTION=\d+x(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()
   val bitrate=Regex("""(?:AVERAGE-BANDWIDTH|BANDWIDTH)=(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull()?:0L
   variants.add(Variant(URL(base,child.trim()),height,bitrate))
  }
  check(variants.isNotEmpty()){"В master-плейлисте не найдены варианты качества"}
  val wanted=quality?.removeSuffix("p")?.toIntOrNull()
  val selected=if(wanted!=null){variants.filter{it.height!=null}.minByOrNull{abs(it.height!!-wanted)}?:variants.maxByOrNull{it.bandwidth}}else variants.maxByOrNull{it.bandwidth}
  return selected!!.url
 }
 private fun isTransportStream(file:File):Boolean{val sample=ByteArray(600);val count=try{file.inputStream().use{input->var n=0;while(n<sample.size){val r=input.read(sample,n,sample.size-n);if(r<=0)break;n+=r};n}}catch(_:Exception){0};if(count<188)return false;for(offset in 0..minOf(32,count-188))if(sample[offset]==0x47.toByte()&&sample[offset+188]==0x47.toByte())return true;return false}
 private fun perform(c:Context,id:String,callback:(String)->Unit){
  val initial=TransferStore.entries(c).firstOrNull{it.id==id}?:return;val folder=File(c.filesDir,"hls_§{id}").apply{mkdirs()};var bytes=0L;var progress=initial.progress.coerceIn(0,99)
  fun publish(value:String,pct:Int,rate:Long=0,eta:Long=-1):Boolean{progress=if(value=="Готово")100 else pct.coerceIn(0,99);return TransferStore.updateIfActive(c,id,initial.name,value,progress,bytes,initial.uri,rate,eta,initial.source,initial.referer,initial.userAgent,initial.quality)}
  try{
   check(initial.source.isNotBlank()&&initial.uri.isNotBlank()){"Отсутствует адрес или место сохранения"}
   var url=URL(initial.source);var body=playlist(c,id,url,initial);var depth=0
   while(body.contains("#EXT-X-STREAM-INF")){check(depth++<4){"Слишком глубокий HLS master-плейлист"};url=selectVariant(body,url,initial.quality);body=playlist(c,id,url,initial)}
   check(body.contains("#EXT-X-ENDLIST")){"Прямой эфир не поддерживается"}
   val keys=body.lines().filter{it.trim().startsWith("#EXT-X-KEY:")};check(keys.none{!it.contains("METHOD=NONE",true)}){"AES-128/DRM-шифрование не поддерживается"}
   check(!body.contains("#EXT-X-MAP")){"fMP4 HLS пока не поддерживается"};check(!body.contains("#EXT-X-BYTERANGE")){"HLS byte-range пока не поддерживается"}
   val segments=body.lines().map{it.trim()}.filter{it.isNotEmpty()&&!it.startsWith("#")};check(segments.isNotEmpty()&&segments.size<=10000){"Некорректный HLS-плейлист"}
   check(segments.none{val path=Uri.parse(it).lastPathSegment.orEmpty().lowercase();path.endsWith(".m4s")||path.endsWith(".mp4")}){"Этот вариант использует fMP4, а не MPEG-TS"}
   val fingerprint=MessageDigest.getInstance("SHA-256").digest((url.toString()+"\n"+body).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}
   val marker=File(folder,"manifest.sha256");if(marker.exists()&&marker.readText()!=fingerprint)folder.listFiles()?.filter{it.name.endsWith(".ts")||it.name.endsWith(".part")}?.forEach{it.delete()};marker.writeText(fingerprint)
   fun segmentFile(i:Int)=File(folder,"%06d.ts".format(i));fun completed()=segments.indices.count{segmentFile(it).isFile&&segmentFile(it).length()>0};fun totalBytes()=segments.indices.sumOf{segmentFile(it).takeIf{f->f.isFile}?.length()?:0L}
   bytes=totalBytes();progress=(completed()*99/segments.size).coerceIn(0,99);publish("Загрузка",progress)
   val began=SystemClock.elapsedRealtime();var downloadedThisRun=0L;var completedThisRun=0
   for((index,segment) in segments.withIndex()){
    if(isDeleted(c,id)){cleanup(c,id);return};if(isPaused(c,id)){TransferStore.setState(c,id,"Пауза");return}
    val target=segmentFile(index);val existed=target.isFile&&target.length()>0
    if(!existed){val part=File(folder,"%06d.part".format(index));var last:Exception?=null
     for(attempt in 1..5){if(isStopped(c,id))break;if(!awaitNetwork(c,id))break;part.delete()
      try{val conn=connect(c,id,URL(url,segment),initial)
       try{val code=conn.responseCode;if(code!=200)throw HttpStatusException(code);conn.inputStream.use{input->part.outputStream().use{output->val buffer=ByteArray(32768);while(true){if(isStopped(c,id))throw InterruptedIOException("Загрузка остановлена");val n=input.read(buffer);if(n<0)break;if(isStopped(c,id))throw InterruptedIOException("Загрузка остановлена");output.write(buffer,0,n)}}}}
       finally{connections.remove(id,conn);conn.disconnect()}
       check(part.length()>0){"Пустой HLS-сегмент"};if(index==0)check(isTransportStream(part)){"Сегмент не MPEG-TS. Этот HLS-вариант не поддерживается"};check(part.renameTo(target)){"Не удалось сохранить сегмент"};last=null;break
      }catch(e:Exception){part.delete();if(isDeleted(c,id)||isPaused(c,id))break;if(e is HttpStatusException&&e.status in listOf(401,403,404,410))throw e;last=e;if(attempt<5){if(!online(c))TransferStore.setState(c,id,"Ожидание сети");Thread.sleep(1000L*(1L shl(attempt-1)))}}
     }
     if(isDeleted(c,id)){cleanup(c,id);return};if(isPaused(c,id)){TransferStore.setState(c,id,"Пауза");return};if(!target.isFile||target.length()==0L){if(last!=null)throw IOException("Сегмент §{index+1}: §{last.message}",last);throw IOException("Ожидание сети")}
    }
    bytes=totalBytes();val done=completed();progress=(done*99/segments.size).coerceIn(0,99);if(!existed){downloadedThisRun+=target.length();completedThisRun++}
    val elapsed=(SystemClock.elapsedRealtime()-began).coerceAtLeast(1000L);val rate=downloadedThisRun*1000/elapsed;val eta=if(completedThisRun>0)(elapsed/1000.0*(segments.size-done)/completedThisRun).toLong().coerceAtLeast(0)else -1L;publish("Загрузка",progress,rate,eta)
   }
   if(isDeleted(c,id)){cleanup(c,id);return};if(isPaused(c,id)){TransferStore.setState(c,id,"Пауза");return};check(completed()==segments.size){"Не все сегменты загружены"}
   c.contentResolver.openOutputStream(Uri.parse(initial.uri),"wt")?.use{out->for(i in segments.indices){if(isStopped(c,id))throw InterruptedIOException("Загрузка остановлена");segmentFile(i).inputStream().use{it.copyTo(out,32768)}}}?:error("Не удалось открыть выбранный файл")
   bytes=totalBytes();if(publish("Готово",100)){folder.deleteRecursively();main.post{callback("Загрузка завершена: §{initial.name}")}}
  }catch(e:Exception){
   if(isDeleted(c,id)){cleanup(c,id);return};if(isPaused(c,id)){TransferStore.setState(c,id,"Пауза");return};if(restartRequested.contains(id))return
   val status=(e as? HttpStatusException)?.status;val message=when(status){401,403->"Ссылка устарела · HTTP §{status}";else->if(e.message=="Ожидание сети")"Ожидание сети" else "Ошибка: §{e.message?:"сеть"}"}
   TransferStore.updateIfActive(c,id,initial.name,message,progress,bytes,initial.uri,0,-1,initial.source,initial.referer,initial.userAgent,initial.quality)
   main.post{callback(if(status==401||status==403)"Ссылка потока устарела. Откройте страницу и выберите поток заново." else "Загрузка остановлена: §{e.message}")}
  }
 }
}
