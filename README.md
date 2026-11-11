# V&R Browser 1.0.8

Android browser with a native Media3 player, HLS media detection, persistent download library, an in-app ad blocker and a dark cyan/magenta visual identity.

## Repository layout

This repository contains **one complete, canonical, directly buildable Android project**.
It is built from `app/` and Gradle files at the root. All themes, icons, wallpapers and Kotlin sources are tracked in Git. No ZIP archive or older version is used as the source of truth. Signed APK and full source ZIP files are **CI artifacts**, not tracked repository files.

## Fixes in 1.0.8

- Startup: register the crash recorder early, run filter subscription updates and transfer recovery off the UI thread, and guard browser lifecycle callbacks.
- HLS downloads: restore complete, validated segments after pause and resume; derive the manifest identity from stable media segment paths instead of expiring CDN auth tokens.
- MP4: explicitly select the tracks from `MediaExtractor` before remuxing samples into `MediaMuxer`. This fixes the specific error where tracks were recognized but no samples were read. Remuxes supported AVC/HEVC and AAC without re-encoding; does not relabel TS as MP4.
- Quality: avoid silent substitution of a different HLS variant and check actual height where Android can probe it. Estimated HLS sizes remain approximate.
- V&R Player: fullscreen, fit/crop toggle, in-player download with location confirmation, track quality/audio selectors, and a native inline player surface that can cover the website's video element after detecting a qualifying HLS playlist.
- Design: brighter toolbar buttons and a size-constrained logo-watermark dialog background.
- Ad filtering: combined AdblockAndroid and V&R Shield, with filter updates in the background. When inline playback succeeds, the original site iframe is suspended so its embedded ads do not continue playing in parallel. Ads multiplexed directly into media streams are not reliably removable.

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

GitHub Actions uploads `VRBrowser-1.0.8-SIGNED-RELEASE` (the signed APK) and `VRBrowser-1.0.8-SOURCE` (the exact tracked source archive). The repository does not commit APKs, keystores or ZIP builds.
