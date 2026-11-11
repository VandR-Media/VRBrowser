# V&R Browser 1.0.9

Android browser with a native Media3 player, HLS media detection, persistent download library, an in-app ad blocker and a dark cyan/magenta visual identity.

## Repository layout

This repository contains **one complete, canonical, directly buildable Android project**.
It is built from `app/` and Gradle files at the root. All themes, icons, wallpapers and Kotlin sources are tracked in Git. No ZIP archive or older version is used as the source of truth. Signed APK and full source ZIP files are **CI artifacts**, not tracked repository files.

## Fixes in 1.0.9

- Updated AndroidX WorkManager to 2.12.0 to avoid the device-specific exact-alarm SecurityException during app startup.
- Saved separate HLS audio playlist URLs per variant, including selected audio language; audio and video are muxed into the same MP4. An output with no audio track is treated as a failed conversion, not a successful silent file.
- HLS pause/resume preserves completed segments; changed playlists no longer delete partial data without notice. Interrupted alternate-audio downloads are recovered after application restart.
- Quality labels prioritize real video dimensions probed from the first MPEG-TS segment. If a provider advertises a different resolution, the discrepancy is displayed; unknown HLS file sizes are not invented.
- Inline player now has native seek, pause, quality, audio, and download controls. Fullscreen playback is edge-to-edge, with a crop-to-fill button available while fullscreen.
- The app carries file-picker state, selected audio and video quality, and playback position across transitions.

**Important:** Successful GitHub Actions build confirms APK compilation, release signature, and package metadata only. On-device tests are still necessary for Huawei startup, HLS audio timing, network reconnect, and fullscreen/crop playback. Not every stream format can be remuxed without transcoding.

## Supported downloads and limitations

Finite, unencrypted MPEG-TS HLS playlists are supported. Live playlists, encrypted streams, DRM, fMP4/CMAF and byte-range segment downloads are not supported. MP4 export requires supported video/audio codecs; unsupported streams show an error rather than producing a broken file. Downloading separate alternate audio renditions with video is not yet implemented.

**Validation status:** the GitHub Actions pipeline checks compilation, Android package metadata and release APK signature. It cannot verify runtime stability, inline placement on every site, or final MP4 playback on a Huawei device. Those require device testing.

## Release signing

Create these repository Actions secrets:
- `VR_KEYSTORE_BASE64` or `VR_KEYSTORE_B64`
- `VR_KEYSTORE_PASSWORD`
- `VR_KEY_ALIAS`
- `VR_KEY_PASSWORD`

The build uses the existing release signing certificate. Keep the JKS private and back it up securely. A different signing key prevents normal in-place Android APK upgrades.

## CI artifacts

GitHub Actions uploads `VRBrowser-1.0.9-SIGNED-RELEASE` (the signed APK) and `VRBrowser-1.0.9-SOURCE` (the exact tracked source archive). The repository does not commit APKs, keystores or ZIP builds.
