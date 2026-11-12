# Simple M3U TV v1.3.0

Native Android IPTV player, compatible with Android 6.0+ (minSdk 23). Uses Media3 / ExoPlayer.

## What's new
- New launcher icon: charcoal TV with orange play button, adaptive on Android 8+ and bitmap on older phones.
- **English-only UI**: playlist selection, controls, labels, errors, loading, buffering, retries, and messages.
- **Automatic reconnection on interruptions**: after stream errors, ended streams, startup timeouts (3.2s), or prolonged rebuffering (8s), the player automatically reconnects to the *same* channel. Retry delays: 1.5s, 3s, 6s, 12s, then max 15s.
- **Auto Next Channel OFF** (default): keep attempting the current channel, without needing manual Retry.
- **Auto Next Channel ON**: retry same channel 3 times, then advance to the next channel. Stop after attempting each channel without success; manual Retry remains available.
- A stream that resumes during short buffering is not interrupted, and normal playback resets retry counters.

## Features
- Import local M3U and M3U8 playlist using Android file picker; remember the last playlist and selected channel.
- Black video background, dark channel panel, orange selection/controls, fullscreen toggle, channel list toggle, Previous/Next/Retry buttons.
- Remembers fullscreen, show/hide channels, and Auto Next Channel settings.
- Per-channel User-Agent and Referer parsing from VLC playlist options.

## Build APK with GitHub Actions
Unzip and upload the **contents inside** this folder to the repository root (replace files). Keep `.github/workflows/build-apk.yml`. Commit changes, open Actions > Build Android APK, select successful run, and download `SimpleM3UPlayer-APK` artifact. Inside: `app-debug.apk`.

## Local build
Requires JDK 17, Gradle 8.11.1, Android SDK platform 36. Run `gradle --no-daemon :app:assembleDebug`.

Android config: compileSdk 36, targetSdk 35, minSdk 23, AGP 8.10.1, Media3 1.11.1.
