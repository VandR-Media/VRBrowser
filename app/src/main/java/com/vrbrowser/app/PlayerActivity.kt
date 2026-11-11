package com.vrbrowser.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.webkit.CookieManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
 private var player: ExoPlayer? = null
 private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()

 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val url=intent.getStringExtra("url") ?: run { finish(); return }
  if(!url.startsWith("https://") && !url.startsWith("http://")) { finish(); return }
  val referer=intent.getStringExtra("referer").orEmpty()
  val userAgent=intent.getStringExtra("user_agent")?.takeIf{it.isNotBlank()} ?: "Mozilla/5.0 (Android) V&R Browser"
  val headers=linkedMapOf<String,String>()
  if(referer.startsWith("http")) {
   headers["Referer"]=referer
   try {
    val ref=android.net.Uri.parse(referer)
    if(ref.scheme in listOf("http","https") && !ref.authority.isNullOrBlank()) headers["Origin"]="${ref.scheme}://${ref.authority}"
   } catch(_:Exception) { }
  }
  try { CookieManager.getInstance().getCookie(url)?.takeIf{it.isNotBlank()}?.let{headers["Cookie"]=it} } catch(_:Exception) { }

  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
  val view=PlayerView(this).apply { useController=true }
  root.addView(view,LinearLayout.LayoutParams(-1,0,1f))
  val controls=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER
   setPadding(dp(8),dp(5),dp(8),dp(8)); setBackgroundColor(Color.rgb(19,24,32))
  }
  val quality=Button(this).apply { text="Качество"; isAllCaps=false; isEnabled=false; setOnClickListener{showTrackSelection(C.TRACK_TYPE_VIDEO,"Качество")} }
  val audio=Button(this).apply { text="Озвучка"; isAllCaps=false; isEnabled=false; setOnClickListener{showTrackSelection(C.TRACK_TYPE_AUDIO,"Аудиодорожка")} }
  controls.addView(quality,LinearLayout.LayoutParams(0,dp(46),1f).apply{setMargins(dp(4),0,dp(4),0)})
  controls.addView(audio,LinearLayout.LayoutParams(0,dp(46),1f).apply{setMargins(dp(4),0,dp(4),0)})
  root.addView(controls,LinearLayout.LayoutParams(-1,dp(58)))
  setContentView(root)

  try {
   val httpFactory=DefaultHttpDataSource.Factory()
    .setUserAgent(userAgent)
    .setAllowCrossProtocolRedirects(true)
    .setDefaultRequestProperties(headers)
   val sourceFactory=DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory)
   player=ExoPlayer.Builder(this).setMediaSourceFactory(sourceFactory).build().also { p ->
    view.player=p
    p.addListener(object:androidx.media3.common.Player.Listener {
     override fun onTracksChanged(tracks:androidx.media3.common.Tracks) {
      quality.isEnabled=tracks.groups.any { it.type==C.TRACK_TYPE_VIDEO && it.isSupported }
      audio.isEnabled=tracks.groups.any { it.type==C.TRACK_TYPE_AUDIO && it.isSupported }
     }
     override fun onPlayerError(error:androidx.media3.common.PlaybackException) {
      Toast.makeText(this@PlayerActivity,error.message ?: "Ошибка воспроизведения",Toast.LENGTH_LONG).show()
     }
    })
    p.setMediaItem(MediaItem.fromUri(url))
    p.prepare()
    p.playWhenReady=true
   }
  } catch(e:Exception) {
   Toast.makeText(this,e.message ?: "Ошибка плеера",Toast.LENGTH_LONG).show()
  }
 }

 private fun showTrackSelection(trackType:Int,title:String) {
  val current=player ?: return
  val available=current.currentTracks.groups.any { it.type==trackType && it.isSupported }
  if(!available) {
   Toast.makeText(this,if(trackType==C.TRACK_TYPE_VIDEO) "Варианты качества не обнаружены" else "Дополнительные аудиодорожки не обнаружены",Toast.LENGTH_SHORT).show()
   return
  }
  TrackSelectionDialogBuilder(this,title,current,trackType)
   .setAllowAdaptiveSelections(false)
   .setShowDisableOption(false)
   .build()
   .show()
 }

 override fun onStop() { player?.pause(); super.onStop() }
 override fun onDestroy() { player?.release(); player=null; super.onDestroy() }
}
