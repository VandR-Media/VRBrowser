package com.vrbrowser.app

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.util.Locale

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
 private val blue=Color.rgb(39,190,245)
 private val pink=Color.rgb(238,77,171)
 private val surface=Color.rgb(27,33,49)
 private val backgroundColor=Color.rgb(13,18,29)
 private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
 private fun rounded(color:Int,radius:Int,stroke:Int?=null)=GradientDrawable().apply {
  setColor(color)
  cornerRadius=dp(radius).toFloat()
  if(stroke!=null)setStroke(dp(1),stroke)
 }
 private var player:ExoPlayer?=null
 private var trackSelector:DefaultTrackSelector?=null
 private lateinit var playerView:PlayerView
 private lateinit var rootLayout:LinearLayout
 private lateinit var videoStage:FrameLayout
 private lateinit var fullscreenControls:LinearLayout
 private lateinit var bottomSpacer:View
 private lateinit var playButton:TextView
 private lateinit var qualityButton:TextView
 private lateinit var audioButton:TextView
 private lateinit var seekBar:SeekBar
 private lateinit var positionLabel:TextView
 private lateinit var durationLabel:TextView
 private lateinit var headerContainer:LinearLayout
 private lateinit var controlPanel:LinearLayout
 private lateinit var fullscreenButton:TextView
 private lateinit var cropButton:TextView
 private var fullscreen=false
 private var cropped=false
 private var pendingDownloadQuality:String?=null
 private var pendingAudioLanguage:String?=null
 private var seeking=false
 private val handler=Handler(Looper.getMainLooper())
 private val progressUpdater=object:Runnable {
  override fun run() {
   updateProgress()
   handler.postDelayed(this,500)
  }
 }

 private fun label(value:String,size:Float=13f,color:Int=Color.WHITE,bold:Boolean=false)=TextView(this).apply {
  text=value
  textSize=size
  setTextColor(color)
  if(bold)setTypeface(typeface,Typeface.BOLD)
  gravity=Gravity.CENTER_VERTICAL
 }

 private fun actionButton(
  value:String,
  fill:Int=surface,
  border:Int=blue,
  weight:Float=1f,
  action:()->Unit
 )=TextView(this).apply {
  text=value
  textSize=13f
  gravity=Gravity.CENTER
  setTextColor(Color.WHITE)
  background=rounded(fill,16,border)
  setPadding(dp(7),dp(8),dp(7),dp(8))
  minHeight=dp(44)
  setOnClickListener{action()}
  layoutParams=LinearLayout.LayoutParams(0,dp(44),weight).apply {
   setMargins(dp(3),0,dp(3),0)
  }
 }

 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=backgroundColor
  window.navigationBarColor=backgroundColor
  window.decorView.keepScreenOn=true

  val url=intent.getStringExtra("url") ?: run { finish(); return }
  if(!url.startsWith("https://") && !url.startsWith("http://")) { finish(); return }
  val referer=intent.getStringExtra("referer").orEmpty()
  val userAgent=intent.getStringExtra("user_agent")?.takeIf{it.isNotBlank()}
   ?: "Mozilla/5.0 (Android) V&R Browser"

  rootLayout=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setBackgroundColor(backgroundColor)
   setPadding(dp(12),dp(8),dp(12),dp(12))
  }

  val header=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding(dp(3),dp(2),dp(3),dp(10))
  }
  header.addView(actionButton("‹",surface,blue,0.55f){finish()})
  header.addView(LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setPadding(dp(10),0,0,0)
   addView(label("V&R PLAYER",12f,blue,true))
   addView(label(intent.getStringExtra("title") ?: "Видео онлайн",14f,Color.WHITE,true))
  },LinearLayout.LayoutParams(0,-2,1f))
  headerContainer=header
  rootLayout.addView(header,LinearLayout.LayoutParams(-1,dp(62)))

  videoStage=FrameLayout(this).apply {
   setBackground(rounded(Color.BLACK,22,Color.rgb(48,68,91)))
   clipToOutline=true
   elevation=dp(3).toFloat()
  }
  playerView=PlayerView(this).apply {
   useController=false
   resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT
   setShutterBackgroundColor(Color.BLACK)
   setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
   setKeepContentOnPlayerReset(true)
  }
  videoStage.addView(playerView,FrameLayout.LayoutParams(-1,-1))
  fullscreenButton=TextView(this).apply {
   text="⛶"
   textSize=25f
   gravity=Gravity.CENTER
   setTextColor(Color.WHITE)
   background=rounded(Color.rgb(29,38,57),15,blue)
   contentDescription="Полноэкранный режим"
   setOnClickListener{setFullscreen(!fullscreen)}
  }
  videoStage.addView(fullscreenButton,FrameLayout.LayoutParams(dp(48),dp(44),Gravity.TOP or Gravity.END).apply{
   setMargins(0,dp(10),dp(10),0)
  })
  val portraitVideoHeight=((resources.displayMetrics.widthPixels-dp(24))*9/16).coerceAtLeast(dp(160))
  rootLayout.addView(videoStage,LinearLayout.LayoutParams(-1,portraitVideoHeight).apply{
   setMargins(0,dp(4),0,dp(12))
  })

  val panel=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   background=rounded(surface,22,Color.rgb(48,68,91))
   setPadding(dp(12),dp(10),dp(12),dp(12))
  }
  val seekRow=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
  }
  positionLabel=label("00:00",11f,Color.rgb(174,194,214))
  durationLabel=label("--:--",11f,Color.rgb(174,194,214))
  seekBar=SeekBar(this).apply {
   max=1000
   progressTintList=android.content.res.ColorStateList.valueOf(pink)
   thumbTintList=android.content.res.ColorStateList.valueOf(blue)
   progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(58,69,88))
   setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(bar:SeekBar?,progress:Int,fromUser:Boolean) {
     if(fromUser) {
      val duration=player?.duration ?: 0L
      if(duration>0L)positionLabel.text=formatTime(duration*progress/1000L)
     }
    }
    override fun onStartTrackingTouch(bar:SeekBar?) { seeking=true }
    override fun onStopTrackingTouch(bar:SeekBar?) {
     val duration=player?.duration ?: 0L
     if(duration>0L)player?.seekTo(duration*bar!!.progress/1000L)
     seeking=false
    }
   })
  }
  seekRow.addView(positionLabel,LinearLayout.LayoutParams(dp(42),dp(32)))
  seekRow.addView(seekBar,LinearLayout.LayoutParams(0,dp(32),1f))
  seekRow.addView(durationLabel,LinearLayout.LayoutParams(dp(42),dp(32)))
  panel.addView(seekRow)

  val controls=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER
   setPadding(0,dp(5),0,0)
  }
  controls.addView(actionButton("−10",Color.rgb(33,42,59),blue,0.8f) {
   val p=player ?: return@actionButton
   p.seekTo((p.currentPosition-10000L).coerceAtLeast(0L))
  })
  playButton=actionButton("▶",pink,pink,0.9f) {
   player?.let { if(it.isPlaying)it.pause() else it.play() }
  }
  controls.addView(playButton)
  controls.addView(actionButton("+10",Color.rgb(33,42,59),blue,0.8f) {
   val p=player ?: return@actionButton
   val target=p.currentPosition+10000L
   p.seekTo(if(p.duration>0L)target.coerceAtMost(p.duration) else target)
  })
  qualityButton=actionButton("Качество",Color.rgb(33,42,59),blue,1.2f) {
   showTrackSelection(C.TRACK_TYPE_VIDEO,"Качество видео")
  }
  audioButton=actionButton("Озвучка",Color.rgb(33,42,59),pink,1.2f) {
   showTrackSelection(C.TRACK_TYPE_AUDIO,"Аудиодорожка")
  }
  qualityButton.isEnabled=false
  audioButton.isEnabled=false
  qualityButton.alpha=0.45f
  audioButton.alpha=0.45f
  controls.addView(qualityButton)
  controls.addView(audioButton)
  cropButton=actionButton("Обрезка: выкл.",Color.rgb(33,42,59),blue,1.5f) {
   setCrop(!cropped)
  }
  val secondary=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER
   setPadding(0,dp(9),0,0)
  }
  secondary.addView(cropButton)
  secondary.addView(actionButton("↓ Скачать",Color.rgb(33,42,59),pink,1.15f) {
   requestDownload()
  })
  panel.addView(controls)
  panel.addView(secondary)
  controlPanel=panel
  rootLayout.addView(panel,LinearLayout.LayoutParams(-1,dp(172)))
  bottomSpacer=View(this)
  rootLayout.addView(bottomSpacer,LinearLayout.LayoutParams(-1,0,1f))
  fullscreenControls=LinearLayout(this).apply{
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding(dp(5),dp(4),dp(5),dp(8))
   setBackgroundColor(Color.argb(190,10,15,25))
   visibility=View.GONE
  }
  fullscreenControls.addView(actionButton("−10",surface,blue,0.7f){
   player?.let{it.seekTo((it.currentPosition-10000).coerceAtLeast(0))}
  })
  fullscreenControls.addView(actionButton("Ⅱ / ▶",pink,pink,0.8f){
   player?.let{if(it.isPlaying)it.pause() else it.play()}
  })
  fullscreenControls.addView(actionButton("+10",surface,blue,0.7f){
   player?.let{it.seekTo(if(it.duration>0)(it.currentPosition+10000).coerceAtMost(it.duration) else it.currentPosition+10000)}
  })
  fullscreenControls.addView(actionButton("Кач.",surface,blue,1f){
   showTrackSelection(C.TRACK_TYPE_VIDEO,"Качество видео")
  })
  fullscreenControls.addView(actionButton("Звук",surface,pink,1f){
   showTrackSelection(C.TRACK_TYPE_AUDIO,"Аудиодорожка")
  })
  fullscreenControls.addView(actionButton("Обрезать",surface,blue,1.2f){
   setCrop(!cropped)
  })
  fullscreenControls.addView(actionButton("↓",surface,pink,0.5f){
   requestDownload()
  })
  videoStage.addView(fullscreenControls,FrameLayout.LayoutParams(-1,dp(60),Gravity.BOTTOM))
  fullscreenControls.bringToFront()
  fullscreenButton.bringToFront()
  setContentView(rootLayout)
  pendingDownloadQuality=savedInstanceState?.getString("download_quality")
  pendingAudioLanguage=savedInstanceState?.getString("download_audio")
  if(savedInstanceState?.getBoolean("fullscreen")==true)setFullscreen(true)
  if(savedInstanceState?.getBoolean("cropped")==true)setCrop(true)

  val headers=linkedMapOf<String,String>()
  if(referer.startsWith("http")) {
   headers["Referer"]=referer
   try {
    val ref=android.net.Uri.parse(referer)
    if(ref.scheme in listOf("http","https") && !ref.authority.isNullOrBlank()) {
     headers["Origin"]="${ref.scheme}://${ref.authority}"
    }
   } catch(_:Exception) { }
  }
  try {
   CookieManager.getInstance().getCookie(url)?.takeIf{it.isNotBlank()}?.let{headers["Cookie"]=it}
  } catch(_:Exception) { }

  try {
   val selector=DefaultTrackSelector(this)
   trackSelector=selector
   val httpFactory=DefaultHttpDataSource.Factory()
    .setUserAgent(userAgent)
    .setConnectTimeoutMs(15000)
    .setReadTimeoutMs(25000)
    .setAllowCrossProtocolRedirects(true)
    .setDefaultRequestProperties(headers)
   val sourceFactory=DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory)
   player=ExoPlayer.Builder(this)
    .setTrackSelector(selector)
    .setMediaSourceFactory(sourceFactory)
    .build().also { p ->
     playerView.player=p
     p.addListener(object:androidx.media3.common.Player.Listener {
      override fun onTracksChanged(tracks:Tracks) {
       val hasVideo=tracks.groups.any{it.type==C.TRACK_TYPE_VIDEO && it.isSupported}
       val hasAudio=tracks.groups.any{it.type==C.TRACK_TYPE_AUDIO && it.isSupported}
       qualityButton.isEnabled=hasVideo
       audioButton.isEnabled=hasAudio
       qualityButton.alpha=if(hasVideo)1f else 0.45f
       audioButton.alpha=if(hasAudio)1f else 0.45f
      }
      override fun onIsPlayingChanged(isPlaying:Boolean) {
       playButton.text=if(isPlaying)"Ⅱ" else "▶"
      }
      override fun onPlayerError(error:androidx.media3.common.PlaybackException) {
       Toast.makeText(this@PlayerActivity,error.message ?: "Ошибка воспроизведения",Toast.LENGTH_LONG).show()
       android.util.Log.e("V&R Player","Playback failed",error)
      }
     })
     p.setMediaItem(MediaItem.fromUri(url))
     p.prepare()
     val savedPosition=savedInstanceState?.getLong("player_position") ?: intent.getLongExtra("start_position",0L)
     if(savedPosition>0L)p.seekTo(savedPosition)
     p.playWhenReady=savedInstanceState?.getBoolean("player_playing") ?: true
    }
  } catch(error:Exception) {
   android.util.Log.e("V&R Player","Player initialization failed",error)
   Toast.makeText(this,error.message ?: "Ошибка запуска плеера",Toast.LENGTH_LONG).show()
  }
 }

 private fun setCrop(value:Boolean){
  cropped=value
  playerView.resizeMode=if(fullscreen && cropped)
   AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
  cropButton.text=if(cropped)"Обрезка: вкл." else "Обрезка: выкл."
 }

 private fun setFullscreen(enable:Boolean) {
  fullscreen=enable
  if(!::rootLayout.isInitialized || !::videoStage.isInitialized || !::fullscreenControls.isInitialized)return
  headerContainer.visibility=if(enable)View.GONE else View.VISIBLE
  controlPanel.visibility=if(enable)View.GONE else View.VISIBLE
  bottomSpacer.visibility=if(enable)View.GONE else View.VISIBLE
  fullscreenControls.visibility=if(enable)View.VISIBLE else View.GONE
  fullscreenButton.text=if(enable)"⤡" else "⛶"
  rootLayout.setPadding(if(enable)0 else dp(12),if(enable)0 else dp(8),
   if(enable)0 else dp(12),if(enable)0 else dp(12))
  videoStage.background=if(enable)android.graphics.drawable.ColorDrawable(Color.BLACK)
   else rounded(Color.BLACK,22,Color.rgb(48,68,91))
  videoStage.clipToOutline=!enable
  videoStage.elevation=if(enable)0f else dp(3).toFloat()
  val portraitHeight=((resources.displayMetrics.widthPixels-dp(24))*9/16).coerceAtLeast(dp(160))
  videoStage.layoutParams=LinearLayout.LayoutParams(-1,if(enable)0 else portraitHeight,if(enable)1f else 0f).apply{
   if(!enable)setMargins(0,dp(4),0,dp(12))
  }
  playerView.resizeMode=if(enable && cropped)AspectRatioFrameLayout.RESIZE_MODE_ZOOM
   else AspectRatioFrameLayout.RESIZE_MODE_FIT
  val flags=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
   View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
   View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
  window.decorView.systemUiVisibility=if(enable)flags else 0
  if(android.os.Build.VERSION.SDK_INT>=28) {
   val params=window.attributes
   params.layoutInDisplayCutoutMode=if(enable)
    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    else android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
   window.attributes=params
  }
  requestedOrientation=if(enable)ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
   else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
 }

 override fun onWindowFocusChanged(hasFocus:Boolean){
  super.onWindowFocusChanged(hasFocus)
  if(hasFocus && fullscreen){
   window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or
    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
  }
 }

 private fun requestDownload() {
  val url=intent.getStringExtra("url").orEmpty()
  if(url.isBlank())return
  val selected=player?.currentTracks?.groups
   ?.firstOrNull{it.type==C.TRACK_TYPE_VIDEO && it.isSelected}
  val selectedHeight=selected?.let{group->
   (0 until group.length).firstOrNull{group.isTrackSelected(it)}?.let{group.getTrackFormat(it).height}
  }?.takeIf{it>0}
  val decoded=player?.videoSize?.height?.takeIf{it>0}
  pendingDownloadQuality=(decoded?:selectedHeight)?.let{"${it}p"}
  pendingAudioLanguage=player?.currentTracks?.groups
   ?.firstOrNull{it.type==C.TRACK_TYPE_AUDIO && it.isSelected}
   ?.let{group->
    (0 until group.length).firstOrNull{group.isTrackSelected(it)}
     ?.let{group.getTrackFormat(it).language}
   }
  val base=(intent.getStringExtra("title") ?: "V&R video")
   .replace(Regex("[^\\p{L}\\p{N}._ -]"),"_").trim().take(56).ifBlank{"VRBrowser"}
  val suffix=pendingDownloadQuality?.let{"_$it"}.orEmpty()
  val name="$base$suffix.${if(url.contains(".m3u8",true))"mp4" else "mp4"}"
  try {
   startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type="video/mp4"
    putExtra(Intent.EXTRA_TITLE,name)
   },5107)
  } catch(e:Exception) { Toast.makeText(this,"Выбор места недоступен: ${e.message}",Toast.LENGTH_LONG).show() }
 }

 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode!=5107)return
  val target=data?.data ?: return
  if(resultCode!=RESULT_OK)return
  val url=intent.getStringExtra("url") ?: return
  val sourceTitle=intent.getStringExtra("title") ?: "V&R video"
  val name=(sourceTitle.replace(Regex("[^\\p{L}\\p{N}._ -]"),"_").trim().take(56).ifBlank{"VRBrowser"})+
   (pendingDownloadQuality?.let{"_$it"} ?: "")+".mp4"
  android.app.AlertDialog.Builder(this,R.style.VRDialog)
   .setTitle("Подтвердить загрузку")
   .setMessage("Сохранить файл $name в выбранную папку?")
   .setNegativeButton("Отмена",null)
   .setPositiveButton("Скачать") { _,_->
    try {
     val flags=(data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
     try{if(flags!=0)contentResolver.takePersistableUriPermission(target,flags)}catch(_:Exception){}
     val id=System.currentTimeMillis().toString()
     TransferStore.update(this,id,name,"Подготовка",0,0,target.toString(),
      source=url,referer=intent.getStringExtra("referer").orEmpty(),
      userAgent=intent.getStringExtra("user_agent").orEmpty(),quality=pendingDownloadQuality,
      audioSource=intent.getStringExtra("audio_source"),audioLanguage=pendingAudioLanguage)
     if(url.contains(".m3u8",true))HlsDownloader.start(this,id)
     else DirectDownloader.start(this,id)
     startActivity(Intent(this,LibraryActivity::class.java))
    }catch(e:Exception){
     Toast.makeText(this,e.message ?: "Ошибка скачивания",Toast.LENGTH_LONG).show()
    }
   }.show()
 }

 override fun onBackPressed() {
  if(fullscreen)setFullscreen(false)else super.onBackPressed()
 }

 private fun formatTime(ms:Long):String {
  val seconds=(ms/1000L).coerceAtLeast(0L)
  return if(seconds>=3600L) "%d:%02d:%02d".format(seconds/3600,(seconds/60)%60,seconds%60)
   else "%02d:%02d".format(seconds/60,seconds%60)
 }

 private fun updateProgress() {
  val current=player ?: return
  val duration=current.duration.takeIf{it>0L && it!=C.TIME_UNSET} ?: 0L
  val position=current.currentPosition.coerceAtLeast(0L)
  if(!seeking)seekBar.progress=if(duration>0L)((position*1000L)/duration).toInt().coerceIn(0,1000) else 0
  positionLabel.text=formatTime(position)
  durationLabel.text=if(duration>0L)formatTime(duration) else "--:--"
  playButton.text=if(current.isPlaying)"Ⅱ" else "▶"
 }

 private data class TrackOption(
  val group:Tracks.Group,
  val index:Int,
  val label:String,
  val selected:Boolean
 )

 private fun showTrackSelection(trackType:Int,title:String) {
  val current=player ?: return
  val selector=trackSelector ?: return
  try {
   val options=mutableListOf<TrackOption>()
   current.currentTracks.groups.forEach { group ->
    if(group.type!=trackType) return@forEach
    for(index in 0 until group.length) {
     if(!group.isTrackSupported(index))continue
     val format=group.getTrackFormat(index)
     val selected=group.isTrackSelected(index)
     val text=if(trackType==C.TRACK_TYPE_VIDEO) {
      val height=format.height.takeIf{it>0}?.let{"${it}p"} ?: "Вариант ${index+1}"
      val bitrate=format.bitrate.takeIf{it>0}?.let{" · ${it/1000} кбит/с"} ?: ""
      height+bitrate
     } else {
      val language=format.language?.takeIf{it.isNotBlank() && it!="und"}
       ?.let{Locale.forLanguageTag(it).getDisplayName(Locale.getDefault())}
      val name=format.label?.takeIf{it.isNotBlank()} ?: language ?: "Дорожка ${index+1}"
      val channels=format.channelCount.takeIf{it>0}?.let{" · ${it} канал(а)"} ?: ""
      name+channels
     }
     options.add(TrackOption(group,index,text,selected))
    }
   }
   if(options.isEmpty()) {
    Toast.makeText(this,if(trackType==C.TRACK_TYPE_VIDEO)"Качество не обнаружено" else "Другие аудиодорожки не обнаружены",Toast.LENGTH_SHORT).show()
    return
   }
   val selectedIndex=options.indexOfFirst{it.selected}
   val labels=options.map{it.label}.toTypedArray()
   android.app.AlertDialog.Builder(this,R.style.VRDialog)
    .setTitle(title)
    .setSingleChoiceItems(labels,selectedIndex) { dialog,which ->
     try {
      val option=options[which]
      val override=TrackSelectionOverride(option.group.mediaTrackGroup,listOf(option.index))
      selector.parameters=selector.buildUponParameters()
       .clearOverridesOfType(trackType)
       .setOverrideForType(override)
       .build()
     } catch(error:Exception) {
      android.util.Log.e("V&R Player","Track override failed",error)
      Toast.makeText(this,"Не удалось переключить дорожку",Toast.LENGTH_LONG).show()
     }
     dialog.dismiss()
    }
    .setNeutralButton("Авто") { dialog,_ ->
     selector.parameters=selector.buildUponParameters().clearOverridesOfType(trackType).build()
     dialog.dismiss()
    }
    .setNegativeButton("Закрыть",null)
    .show()
  } catch(error:Exception) {
   android.util.Log.e("V&R Player","Track selection dialog failed",error)
   Toast.makeText(this,"Не удалось открыть настройки дорожек",Toast.LENGTH_LONG).show()
  }
 }

 override fun onResume() {
  super.onResume()
  handler.removeCallbacks(progressUpdater)
  handler.post(progressUpdater)
 }

 override fun onPause() {
  handler.removeCallbacks(progressUpdater)
  player?.pause()
  super.onPause()
 }

 override fun onSaveInstanceState(outState:Bundle) {
  outState.putString("download_quality",pendingDownloadQuality)
  outState.putString("download_audio",pendingAudioLanguage)
  outState.putBoolean("fullscreen",fullscreen)
  outState.putBoolean("cropped",cropped)
  outState.putLong("player_position",player?.currentPosition ?: 0L)
  outState.putBoolean("player_playing",player?.playWhenReady ?: false)
  super.onSaveInstanceState(outState)
 }

 override fun onDestroy() {
  handler.removeCallbacks(progressUpdater)
  if(::playerView.isInitialized)playerView.player=null
  player?.release()
  player=null
  trackSelector=null
  super.onDestroy()
 }
}
