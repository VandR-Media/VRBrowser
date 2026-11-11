# V&R Browser 1.0.5

Android browser for Huawei / Android devices. Kotlin, WebView and Media3.

## 1.0.5 — download reliability

- HLS pause interrupts the active segment request instead of only changing the label.
- Resume reuses only fully written segments from the same playlist fingerprint.
- HLS and direct downloads use the browser's current WebView User-Agent, Referer and Cookie header.
- Direct-file resume validates the server's Content-Range offset and uses If-Range with ETag / Last-Modified when available.
- Network loss interrupts stale connections; transient failures wait for connectivity and retry.
- Interrupted transfers are recovered when the app process starts again. Explicitly paused tasks are not auto-started.
- The Storage Access Framework write permission is persisted so a selected destination can survive an app restart.
- Progress remains below 100% until the final output file has been written successfully.
- A 401/403 is reported as an expired/session-bound link. The library offers to reopen the source page; a fresh stream must be selected there.
- Media metadata requests now include WebView cookies. File opening uses the MIME type derived from the actual extension.

## HLS support and limitations

The downloader assembles finite, unencrypted MPEG-TS HLS playlists. It does not bypass DRM. Live streams, AES-128 encrypted streams, fMP4 (EXT-X-MAP), byte-range playlists, DASH and some non-TS segment formats are not supported and should report a clear error instead of producing a misleading completed file.

Displayed size in the media finder is an estimate. The download library reports the bytes actually written to the output file.

## Build

The canonical release source is VRBrowser-1.0.5.zip. The GitHub Actions workflow extracts and builds that archive, validates applicationId, versionCode, versionName and the APK signature, then uploads VRBrowser-1.0.5-SIGNED-RELEASE.

The repository app/ source files are updated for download review; the canonical archive remains the only release build input.

Required GitHub Actions secrets:
- VR_KEYSTORE_BASE64 (or legacy VR_KEYSTORE_B64)
- VR_KEYSTORE_PASSWORD
- VR_KEY_ALIAS
- VR_KEY_PASSWORD

Keep using the same release keystore for every update. Changing the signing certificate prevents Android from installing the new APK over an existing installation.
