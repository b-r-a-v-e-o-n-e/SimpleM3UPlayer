# SimpleM3UPlayer 1.2.0

Android 6.0+ (minSdk 23) / Media3 ExoPlayer 1.11.1 / Java 17.

## Interface
- Black player background, dark charcoal channel sidebar on the right, orange selection and controls.
- `◀` previous channel, `▶` next channel, `☰` show/hide channel list, `⛶` toggle fullscreen (hides system bars and the sidebar; it can still be reopened with `☰`), `↻` retry the currently selected stream.
- Tap the video background to show/hide controls.
- Tap **Смени плейлиста** to import a local M3U through the Android file picker.
- Switch **Авто следващ канал**: OFF (default) stays on the channel on errors and shows Retry; ON skips channels when startup exceeds 3.2 seconds, stream ends, or an unrecoverable player error occurs. Brief normal buffering does *not* trigger automatic skipping.
- Last playlist URI, selected channel, list visibility, fullscreen mode, and auto-skip setting persist through app restart.
- Supports per-channel VLC `http-user-agent` and `http-referrer` options.

## Build APK (GitHub Actions)
Push the *contents* of this folder to the repository root (including `.github/workflows/build-apk.yml`). Go to Actions > Build Android APK > Run workflow, or push to main. Download **SimpleM3UPlayer-APK** from Artifacts; inside is `app-debug.apk`.

## Build APK (Linux)
Requires JDK 17, Android SDK platform android-36, Android build tools, Gradle 8.11.1. Run `gradle :app:assembleDebug`.

Notes: ExoPlayer uses the decoders installed on the device; DRM/RTSP/RTMP/UDP are not supported by the included modules. `compileSdk=36`, `targetSdk=35`, `minSdk=23`.
