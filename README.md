# V&R Browser 1.0.7

Android browser for Huawei / Android devices. Kotlin, WebView and Media3.

## 1.0.7 — stable media pipeline

- HLS master playlists are expanded recursively into leaf variants. Ambiguous variants are never silently mapped to the highest bitrate.
- Before saving an HLS download, the app compares the selected resolution with the actual video track when the container exposes it. A mismatch is reported instead of saving the wrong quality.
- Finite, clear MPEG-TS HLS downloads resume from complete segments after pause, network loss or process restart.
- HLS segments are remuxed into MP4 by copying supported H.264/H.265 video and AAC audio samples. The app does not decode and re-encode the full video, reducing memory use and avoiding the device-dependent encoder crash seen during the previous MP4 export.
- The MP4 is written to the selected destination only after the local MP4 has been finalized and validated. Progress does not reach 100% before the final copy succeeds.
- The native V&R Player has a custom dark/neon interface and in-app quality/audio selection menus. It receives the page Referer, User-Agent and cookies.
- Ad filtering uses AdblockAndroid with AdGuard Base, EasyPrivacy Lite, AdGuard Tracking Protection and AdGuard Annoyances, alongside the host-level blocker. Filter subscriptions are restored idempotently if an earlier download was interrupted.
- WebView renderer termination is handled by recreating the affected tab instead of leaving the browser unusable.

## HLS and media limitations

- HLS downloads support finite, unencrypted MPEG-TS playlists. Live HLS, DRM, AES-128, fMP4/CMAF, byte-range playlists and DASH downloads are not supported.
- MP4 remuxing supports H.264/H.265 video and AAC audio without re-encoding. Unsupported codecs produce an explicit error; the app does not mislabel a TS file as MP4.
- Alternate audio renditions can be selected in the native player when the master playlist publishes them. Downloading a separately hosted alternate audio playlist together with the video is not yet implemented.
- A browser filter can block network ad requests and cosmetic ads, but cannot reliably remove advertisements stitched directly into the HLS video segments. Blocking those segments can break playback.
- The media finder displays estimated HLS sizes. The download library reports bytes actually written.

## Build and release

GitHub Actions retains the complete Android resource tree from the existing source bundle, overlays the tracked Kotlin sources, manifest and Gradle configuration, then builds and signs the 1.0.7 APK. The workflow uploads both the signed APK and the merged complete source archive.

Required GitHub Actions secrets:
- VR_KEYSTORE_BASE64 (or legacy VR_KEYSTORE_B64)
- VR_KEYSTORE_PASSWORD
- VR_KEY_ALIAS
- VR_KEY_PASSWORD

Keep using the same release keystore for every update. Changing the signing certificate prevents Android from installing the new APK over an existing installation.
