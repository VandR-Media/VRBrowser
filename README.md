# V&R Browser v0.3 — Android project (not a verified APK)

Target: Huawei Pura 80 Ultra, EMUI 15. Kotlin, Android WebView, Media3.

## Added in v0.3
- V&R Shield: enabled by default in balanced mode. Host-level blocking for third-party subresources via WebView.shouldInterceptRequest. Toggle Off/Balanced/Strict; strict currently uses same domain rules (additional strict filtering not implemented).
- Weekly refresh of a public hosts-format filter over HTTPS on a background executor; 5 MB cap, basic validation, cached copy and bundled fallback. Manual refresh and editable domain exceptions.
- GitHub Actions workflow builds a debug APK and uploads an artifact on manual run. No credentials needed for a debug build.

## Current limitations
- Not a full Adblock Plus/EasyList parser. No cosmetic filtering or first-party page context handling; exceptions are by request hostname, not the top-level page. Some ads remain and some sites may break.
- Direct downloads only via DownloadManager. HLS/DASH can be played if compatible, but are NOT yet assembled or converted. MP3 transcoding, background media service, resumable conversion jobs and full QA are pending. DRM bypass is out of scope.
- This environment has Java but no Android SDK or Gradle, so APK could not be built or tested here.

## Build in GitHub Actions
1. Create a private GitHub repository and upload **the contents** of this folder (including `.github`).
2. Go to Actions > Android APK > Run workflow.
3. If the build succeeds, open the workflow run and download the `VRBrowser-debug-apk` artifact. Extract the ZIP and install the APK on your device after reviewing it.
4. For real distribution, use a release keystore and secure signing; debug APK is for testing only.

## Local build
Install Android SDK 35, JDK 17, Gradle 8.11.1. Run `gradle assembleDebug` in project root. Build dependencies require network access.
