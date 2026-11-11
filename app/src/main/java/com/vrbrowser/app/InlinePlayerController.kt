package com.vrbrowser.app

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import android.widget.Toast
import android.app.AlertDialog
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject

/** Native Media3 player surface that overlays the site's embedded player element.
 * On close, the original iframe is restored. No global site-specific selectors.
 */
@UnstableApi
class InlinePlayerController(
 private val activity:Activity,
 private val pages:FrameLayout,
 val web:WebView,
 private val source:String,
 private val referer:String,
 private val userAgent:String,
 private val fullscreen:(Long)->Unit,
 private val download:(String?)->Unit,
 private val closed:()->Unit
) {
 private val density=activity.resources.displayMetrics.density
 private fun dp(v:Int)=(v*density).toInt()
 private var player:ExoPlayer?=null
 private var host:FrameLayout?=null
 private var released=false
 private var attached=false
 private var playControl:TextView?=null
 private val accent=Color.rgb(39,190,245)

 private fun rounded(bg:Int,stroke:Int):GradientDrawable=GradientDrawable().apply {
  setColor(bg);cornerRadius=dp(18).toFloat();setStroke(dp(1),stroke)
 }

 fun attach(onComplete:(Boolean)->Unit) {
  val js="""
   (function(){
    const nodes=Array.from(document.querySelectorAll('iframe,video,[id*=player],[class*=player]'));
    const candidates=nodes.map(node=>({node,rect:node.getBoundingClientRect()}))
     .filter(p=>p.rect.width>230 && p.rect.height>120 && p.rect.bottom>0 && p.rect.top<innerHeight)
     .sort((a,b)=>(b.rect.width*b.rect.height)-(a.rect.width*a.rect.height));
    const found=candidates.find(p=>p.node.tagName==='IFRAME' || p.node.tagName==='VIDEO') || candidates[0];
    if(!found) return '';
    let node=found.node;
    if(node.tagName!=='VIDEO' && node.tagName!=='IFRAME'){
     node=node.querySelector('iframe,video') || node;
    }
    const r=node.getBoundingClientRect();
    if(r.width<200 || r.height<100)return '';
    window.__vrInlineTarget=node;
    if(node.dataset.vrOldVisibility===undefined)
     node.dataset.vrOldVisibility=node.style.visibility||'';
    node.style.visibility='hidden';
    if(node.tagName==='IFRAME'){
     if(!node.dataset.vrOldSrc)node.dataset.vrOldSrc=node.getAttribute('src')||'';
     if(node.getAttribute('src')!=='about:blank')node.setAttribute('src','about:blank');
    } else if(node.tagName==='VIDEO') {
     try{node.pause();}catch(e){}
    }
    return JSON.stringify({x:r.left,y:r.top,w:r.width,h:r.height});
   })()
  """.trimIndent()
  web.evaluateJavascript(js){value->
   if(released){onComplete(false);return@evaluateJavascript}
   val rect=try {
    val str=JSONArray("[$value]").optString(0)
    JSONObject(str)
   } catch(_:Exception){null}
   if(rect==null){onComplete(false);return@evaluateJavascript}
   try { create(rect);attached=true;onComplete(true) }
   catch(error:Throwable){android.util.Log.e("V&R Browser","Inline player failed",error);release();onComplete(false)}
  }
 }

 private fun create(rect:JSONObject) {
  val headers=linkedMapOf<String,String>()
  if(referer.startsWith("http"))headers["Referer"]=referer
  try { CookieManager.getInstance().getCookie(source)?.takeIf{it.isNotBlank()}
   ?.let{headers["Cookie"]=it} }catch(_:Exception){}
  val http=DefaultHttpDataSource.Factory()
   .setUserAgent(userAgent)
   .setDefaultRequestProperties(headers)
   .setAllowCrossProtocolRedirects(true)
  val p=ExoPlayer.Builder(activity)
   .setMediaSourceFactory(DefaultMediaSourceFactory(activity).setDataSourceFactory(http))
   .build()
  player=p
  val frame=FrameLayout(activity).apply {
   background=rounded(Color.BLACK,accent)
   clipToOutline=true
   elevation=dp(6).toFloat()
  }
  val video=PlayerView(activity).apply {
   useController=false
   setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
   player=p
  }
  frame.addView(video,FrameLayout.LayoutParams(-1,-1))
  val bar=LinearLayout(activity).apply {
   gravity=Gravity.END or Gravity.CENTER_VERTICAL
   setPadding(dp(7),dp(3),dp(7),dp(3))
   background=rounded(Color.rgb(24,31,47),accent)
  }
  fun control(text:String,width:Int,onClick:()->Unit)=TextView(activity).apply {
   this.text=text
   textSize=15f
   setTextColor(Color.WHITE)
   gravity=Gravity.CENTER
   contentDescription=text
   setOnClickListener{onClick()}
   bar.addView(this,LinearLayout.LayoutParams(dp(width),dp(36)))
  }
  control("V&R",47){showTracks(C.TRACK_TYPE_VIDEO)}
  control("⛶",42){fullscreen(player?.currentPosition ?: 0L)}
  control("×",42){release();closed()}
  frame.addView(bar,FrameLayout.LayoutParams(-2,dp(44),Gravity.TOP or Gravity.END).apply {
   topMargin=dp(3);rightMargin=dp(3)
  })
  val mediaBar=LinearLayout(activity).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding(dp(5),dp(2),dp(5),dp(4))
   background=rounded(Color.argb(224,22,31,48),accent)
  }
  fun mediaAction(title:String,weight:Float,onAction:()->Unit):TextView {
   return TextView(activity).apply {
    text=title
    textSize=13f
    setTextColor(Color.WHITE)
    gravity=Gravity.CENTER
    contentDescription=title
    setOnClickListener{onAction()}
    mediaBar.addView(this,LinearLayout.LayoutParams(0,dp(37),weight).apply{
     setMargins(dp(2),0,dp(2),0)
    })
   }
  }
  mediaAction("−10",0.8f){player?.let{it.seekTo((it.currentPosition-10000).coerceAtLeast(0))}}
  playControl=mediaAction("Ⅱ",0.9f){player?.let{if(it.isPlaying)it.pause() else it.play()}}
  mediaAction("+10",0.8f){player?.let{it.seekTo(it.currentPosition+10000)}}
  mediaAction("Кач.",1f){showTracks(C.TRACK_TYPE_VIDEO)}
  mediaAction("Звук",1f){showTracks(C.TRACK_TYPE_AUDIO)}
  mediaAction("↓",0.7f){download(player?.videoSize?.height?.takeIf{it>0}?.let{"${it}p"})}
  frame.addView(mediaBar,FrameLayout.LayoutParams(-1,dp(48),Gravity.BOTTOM))
  p.addListener(object:androidx.media3.common.Player.Listener{
   override fun onIsPlayingChanged(isPlaying:Boolean){
    playControl?.text=if(isPlaying)"Ⅱ" else "▶"
   }
   override fun onPlayerError(error:androidx.media3.common.PlaybackException){
    Toast.makeText(activity,error.message ?: "Не удалось открыть поток",Toast.LENGTH_LONG).show()
   }
  })
  host=frame
  pages.addView(frame,FrameLayout.LayoutParams(dp(320),dp(180)))
  updateBounds(rect)
  p.setMediaItem(MediaItem.fromUri(source))
  p.prepare()
  p.playWhenReady=true
 }

 fun refreshPosition() {
  if(released || !attached)return
  web.evaluateJavascript("""
   (function(){
    const n=window.__vrInlineTarget;
    if(!n)return '';
    const r=n.getBoundingClientRect();
    return JSON.stringify({x:r.left,y:r.top,w:r.width,h:r.height});
   })()
  """.trimIndent()){value->
   if(released)return@evaluateJavascript
   val rect=try{JSONObject(JSONArray("[$value]").optString(0))}catch(_:Exception){null}
   if(rect!=null)updateBounds(rect)
  }
 }

 private fun updateBounds(rect:JSONObject) {
  val frame=host ?: return
  val scale=web.scale.coerceAtLeast(0.5f)
  val w=(rect.optDouble("w",320.0)*scale).toInt()
  val h=(rect.optDouble("h",180.0)*scale).toInt()
  val left=(rect.optDouble("x",0.0)*scale).toInt()
  val top=(rect.optDouble("y",0.0)*scale).toInt()
  frame.visibility=if(left+w<=0||top+h<=0||left>=pages.width||top>=pages.height)View.GONE else View.VISIBLE
  frame.layoutParams=FrameLayout.LayoutParams(w.coerceAtLeast(dp(200)),h.coerceAtLeast(dp(120)),Gravity.TOP or Gravity.START).apply {
   leftMargin=left;topMargin=top
  }
 }

 private fun showTracks(trackType:Int){
  val p=player?:return
  try{
   data class Choice(val group:Tracks.Group,val index:Int,val title:String,val selected:Boolean)
   val options=p.currentTracks.groups.filter{it.type==trackType}.flatMap{group->
    (0 until group.length).filter{group.isTrackSupported(it)}.map{index->
     val format=group.getTrackFormat(index)
     val title=if(trackType==C.TRACK_TYPE_VIDEO){
      (format.height.takeIf{it>0}?.let{"${it}p"}?:"Авто")+
       (format.bitrate.takeIf{it>0}?.let{" · ${it/1000} кбит/с"}?:"")
     }else{
      format.label?.takeIf{it.isNotBlank()} ?: format.language?.takeIf{it!="und"} ?: "Звук ${index+1}"
     }
     Choice(group,index,title,group.isTrackSelected(index))
    }
   }
   if(options.isEmpty()){
    Toast.makeText(activity,"Нет доступных дорожек",Toast.LENGTH_SHORT).show()
    return
   }
   AlertDialog.Builder(activity,R.style.VRDialog)
    .setTitle(if(trackType==C.TRACK_TYPE_VIDEO)"Качество" else "Озвучка")
    .setSingleChoiceItems(options.map{it.title}.toTypedArray(),options.indexOfFirst{it.selected}){dialog,which->
     val option=options[which]
     p.trackSelectionParameters=p.trackSelectionParameters.buildUpon()
      .clearOverridesOfType(trackType)
      .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup,listOf(option.index)))
      .build()
     dialog.dismiss()
    }
    .setNeutralButton("Авто"){dialog,_->
     p.trackSelectionParameters=p.trackSelectionParameters.buildUpon()
      .clearOverridesOfType(trackType).build()
     dialog.dismiss()
    }.setNegativeButton("Закрыть",null).show()
  }catch(e:Exception){
   Toast.makeText(activity,e.message ?: "Ошибка выбора дорожки",Toast.LENGTH_LONG).show()
  }
 }

 fun pause() { player?.pause() }
 fun release(restoreSite:Boolean=true) {
  if(released)return
  released=true
  player?.release();player=null
  host?.let{pages.removeView(it)};host=null
  if(restoreSite)web.evaluateJavascript("""
   (function(){
    const n=window.__vrInlineTarget;if(!n)return;
    n.style.visibility=n.dataset.vrOldVisibility||'';
    if(n.tagName==='IFRAME'&&n.dataset.vrOldSrc)n.setAttribute('src',n.dataset.vrOldSrc);
    delete window.__vrInlineTarget;
   })()
  """.trimIndent(),null)
 }
}
