# V&R Browser 1.0.6

Android browser for Huawei / Android devices. Kotlin, WebView and Media3.

## 1.0.6 — media pipeline

- HLS master playlists are expanded recursively into leaf variants before they are offered for download.
- Unknown quality is never silently mapped to the highest-bitrate variant. Ambiguous nested playlists fail with a clear message.
- The media scanner's JavaScript scope bug is fixed.
- HLS downloads are assembled from resumable MPEG-TS segments and then remuxed/transformed to MP4 with Media3 Transformer. The selected destination is always a .mp4 file for supported HLS inputs.
- A floating V&R Player shortcut appears when an HLS stream is detected on the page.
- The native Media3 player receives the page Referer, WebView User-Agent and cookies. Its controls include separate quality and audio-track menus when the source exposes those tracks.
- Ad filtering now uses an ABP-compatible engine with AdGuard Base, EasyPrivacy Lite and AdGuard Tracking Protection subscriptions, alongside the existing host-level blocker.

## HLS and media limitations

- The resumable HLS downloader supports finite, unencrypted MPEG-TS playlists. Live HLS, DRM, AES-128, fMP4/CMAF HLS, byte-range playlists and DASH downloads are not supported by this downloader.
- MP4 export depends on the codecs supported by Media3 Transformer and the device's Android media codecs. If conversion fails, the app reports an error rather than pretending a TS file is an MP4.
- Alternate audio renditions are available in the native player when the HLS master playlist exposes them. Downloading a separately hosted alternate audio rendition is not yet implemented.
- A browser filter cannot reliably remove ads inserted directly into the video stream by the streaming provider. Blocking those segments can also break playback.

## Build and release

The complete Android resource tree (themes, drawables and launcher assets) is retained from the existing source bundle. GitHub Actions overlays the tracked Kotlin sources, manifest and Gradle configuration on that complete tree, then builds and signs the 1.0.6 APK. The workflow uploads both the APK and the merged complete source archive.

Required GitHub Actions secrets:
- VR_KEYSTORE_BASE64 (or legacy VR_KEYSTORE_B64)
- VR_KEYSTORE_PASSWORD
- VR_KEY_ALIAS
- VR_KEY_PASSWORD

Keep using the same release keystore for every update. Changing the signing certificate prevents Android from installing the new APK over an existing installation.
