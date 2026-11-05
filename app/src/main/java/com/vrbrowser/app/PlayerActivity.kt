package com.vrbrowser.app

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : Activity() {
 private var player: ExoPlayer? = null
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val url = intent.getStringExtra("url") ?: run { finish(); return }
  if (!url.startsWith("https://") && !url.startsWith("http://")) { finish(); return }
  val view = PlayerView(this)
  setContentView(view)
  try {
   player = ExoPlayer.Builder(this).build().also { p ->
    view.player = p
    p.setMediaItem(MediaItem.fromUri(url))
    p.prepare()
    p.playWhenReady = true
   }
  } catch (e: Exception) { Toast.makeText(this, e.message ?: "Ошибка плеера", Toast.LENGTH_LONG).show() }
 }
 override fun onStop() { player?.pause(); super.onStop() }
 override fun onDestroy() { player?.release(); player=null; super.onDestroy() }
}
