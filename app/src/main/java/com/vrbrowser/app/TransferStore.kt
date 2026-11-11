package com.vrbrowser.app
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
object TransferStore {
 data class Entry(val id:String,val name:String,val state:String,val progress:Int,val bytes:Long,val uri:String,val speed:Long,val etaSeconds:Long,val source:String,val referer:String,val userAgent:String,val quality:String,val audioSource:String,val audioLanguage:String)
 private val lock=Any()
 private fun prefs(c:Context)=c.getSharedPreferences("hls_transfers",Context.MODE_PRIVATE)
 private fun read(c:Context)=try{JSONArray(prefs(c).getString("items","[]"))}catch(_:Exception){JSONArray()}
 private fun mutate(c:Context,id:String,create:Boolean,allowStopped:Boolean,make:(JSONObject?)->JSONObject):Boolean=synchronized(lock){
  val arr=read(c);val old=(0 until arr.length()).mapNotNull{arr.optJSONObject(it)}.firstOrNull{it.optString("id")==id}
  if(old==null&&!create)return false
  if(old!=null&&!allowStopped&&old.optString("state") in listOf("Пауза","Удалено"))return false
  val updated=make(old);val next=JSONArray()
  for(i in 0 until arr.length()){val item=arr.optJSONObject(i)?:continue;if(item.optString("id")!=id)next.put(item)}
  next.put(updated);val trimmed=JSONArray()
  for(i in maxOf(0,next.length()-100) until next.length())trimmed.put(next.opt(i))
  prefs(c).edit().putString("items",trimmed.toString()).commit();true
 }
 private fun build(old:JSONObject?,id:String,name:String,state:String,progress:Int,bytes:Long,uri:String,speed:Long,etaSeconds:Long,source:String,referer:String,userAgent:String?,quality:String?,audioSource:String?,audioLanguage:String?):JSONObject{
  val j=old?.let{JSONObject(it.toString())}?:JSONObject()
  return j.put("id",id).put("name",name).put("state",state).put("progress",progress.coerceIn(0,100)).put("bytes",bytes.coerceAtLeast(0)).put("uri",uri).put("speed",speed.coerceAtLeast(0)).put("eta",etaSeconds).put("source",source).put("referer",referer).put("userAgent",userAgent?:old?.optString("userAgent").orEmpty()).put("quality",quality?:old?.optString("quality").orEmpty()).put("audioSource",audioSource?:old?.optString("audioSource").orEmpty()).put("audioLanguage",audioLanguage?:old?.optString("audioLanguage").orEmpty())
 }
 fun update(c:Context,id:String,name:String,state:String,progress:Int,bytes:Long,uri:String="",speed:Long=0,etaSeconds:Long=-1,source:String="",referer:String="",userAgent:String?=null,quality:String?=null,audioSource:String?=null,audioLanguage:String?=null){mutate(c,id,true,true){old->build(old,id,name,state,progress,bytes,uri,speed,etaSeconds,source,referer,userAgent,quality,audioSource,audioLanguage)}}
 fun updateIfActive(c:Context,id:String,name:String,state:String,progress:Int,bytes:Long,uri:String,speed:Long=0,etaSeconds:Long=-1,source:String="",referer:String="",userAgent:String?=null,quality:String?=null,audioSource:String?=null,audioLanguage:String?=null):Boolean=mutate(c,id,false,false){old->build(old,id,name,state,progress,bytes,uri,speed,etaSeconds,source,referer,userAgent,quality,audioSource,audioLanguage)}
 fun setState(c:Context,id:String,state:String){mutate(c,id,false,true){old->JSONObject(old!!.toString()).put("state",state)}}
 fun state(c:Context,id:String):String?=entries(c).firstOrNull{it.id==id}?.state
 fun remove(c:Context,id:String)=synchronized(lock){val arr=read(c);val next=JSONArray();for(i in 0 until arr.length())arr.optJSONObject(i)?.let{if(it.optString("id")!=id)next.put(it)};prefs(c).edit().putString("items",next.toString()).commit()}
 fun entries(c:Context):List<Entry> = synchronized(lock){val arr=read(c);(0 until arr.length()).mapNotNull{arr.optJSONObject(it)?.let{j->Entry(j.optString("id"),j.optString("name"),j.optString("state"),j.optInt("progress"),j.optLong("bytes"),j.optString("uri"),j.optLong("speed"),j.optLong("eta",-1),j.optString("source"),j.optString("referer"),j.optString("userAgent"),j.optString("quality"),j.optString("audioSource"),j.optString("audioLanguage"))}}}
}
