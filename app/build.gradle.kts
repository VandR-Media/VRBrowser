plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.vrbrowser.app"; compileSdk = 35
 defaultConfig { applicationId = "com.vrbrowser.app"; minSdk = 26; targetSdk = 35; versionCode = 1010; versionName = "1.0.10" }
 signingConfigs {
  create("persistent") {
   storeFile = file(System.getenv("VR_KEYSTORE_PATH") ?: "vrbrowser-signing.jks")
   storePassword = System.getenv("VR_KEYSTORE_PASSWORD") ?: ""
   keyAlias = System.getenv("VR_KEY_ALIAS") ?: "vrbrowser"
   keyPassword = System.getenv("VR_KEY_PASSWORD") ?: ""
  }
 }
 buildTypes { getByName("release") {
  isMinifyEnabled = false
  if (!System.getenv("VR_KEYSTORE_PATH").isNullOrBlank()) {
   signingConfig = signingConfigs.getByName("persistent")
  }
 } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}

dependencies {
 implementation("androidx.media3:media3-exoplayer:1.5.1")
 implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
 implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
 implementation("androidx.media3:media3-ui:1.5.1")
 implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
 // Fix OEM SecurityException from old WorkManager ForceStopRunnable.setExact().
 implementation("androidx.work:work-runtime:2.12.0")
 implementation("com.github.Edsuns.AdblockAndroid:ad-filter:master-SNAPSHOT")
}
