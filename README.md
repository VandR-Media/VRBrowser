# V&R Browser 1.0.11 (security candidate)

> **Security status: preliminary source hardening, NOT an audited secure browser.**
> APK compilation, dependency vulnerability checks, on-device Huawei validation and anti-phishing behavior must be verified before a release claim.

## Security changes pending validation

- WebView disallows mixed HTTP content and file URL access; HTTPS certificate failures are explicitly cancelled.
- Known Safe Browsing hits are rejected (subject to the availability of the device's WebView safe browsing provider).
- External links only open through user-confirmed `tel:`, `mailto:`, `sms:` and `smsto:` navigation.
- Third-party cookies are disabled by default; a browser-menu toggle restores compatibility if required.
- Full site-data cleanup includes cookies, WebStorage, in-WebView history, browser history and cache.
- V&R Shield can update StevenBlack and AdAway domain-hosts lists over HTTPS, reject malformed/oversized files, preserve older lists on download failure, and enable/disable sources independently.
- Existing AdFilter engine still performs rule-based/cosmetic filtering; host blocklists alone cannot hide ads served from a site's own domain.

## Known security limitations

- Safe Browsing availability on Huawei/EMUI without Google Mobile Services needs testing.
- JavaScript, website cookies and DOM storage are supported for website compatibility; they are not private browsing.
- File downloads are not antivirus-scanned. Only open files from trusted sources.
- Third-party library `ad-filter:master-SNAPSHOT` remains unpinned and needs a reproducible build and dependency vulnerability review.
- The current `Shield` exclusions only apply to the additional host blocker; they do not globally disable the AdFilter rule engine.
- HTTPS-only site navigation, download redirects, site-isolated permission prompts and malicious-site test suites require further work.
- These changes should not be merged or described as fully secure before successful CI plus on-device regression testing.

---

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
