# Simple M3U TV — v1.4.0

A lightweight IPTV viewer for Android 6.0+ using AndroidX Media3 / ExoPlayer.

## Interface

Landscape TV-style UI inspired by the supplied concept: dark navy/black header, orange accents, channel browser at the right, video on the left, and an outlined control bar along the bottom. The **Settings** button opens a real interactive overlay.

- **Previous / Next** — move between channels.
- **Channels** — show/hide the right sidebar.
- **Fullscreen** — toggle Android immersive fullscreen; hide the channel list until requested.
- **Retry** — manually restart the current stream.
- **Change playlist** — select an M3U/M3U8 file using Android's file picker. The playlist URI and selected channel persist between sessions.
- **Settings → Auto Next Channel** — switch to the next channel after failed recovery attempts.
- **Settings → Retry on Failure** — automatically retry a failed stream. With Auto Next OFF this keeps attempting the same channel. If both are OFF, it stays on the current channel and shows a manual Retry action.
- **Settings → Retry Attempts** — 1–10 (default 3), number of recovery attempts before automatic channel advance when Auto Next is enabled.
- **Settings → Retry Delay** — 1–15 seconds (default 3), initial delay between reconnect attempts. Subsequent failures use capped exponential backoff.

The automatic reconnect system recognizes player errors, terminated streams, 3.2 s initial load timeout and a 12 s post-playback stall. Short buffering periods do not trigger channel switching. Buffer timing and connectivity depend on device/network conditions; these are application-level deadlines rather than strict network guarantees.

All visible application strings are in English. Sample M3U channel names are supplied by the playlist and are not translated.

## Build with GitHub Actions

Upload the **contents** of this directory (including `.github/workflows/build-apk.yml`) into your repository root, commit and open **Actions → Build Android APK**. Download `SimpleM3UPlayer-APK` from a successful workflow's Artifacts. The archive contains `app-debug.apk`.

Uses Java 17, Android Gradle Plugin 8.10.1, Gradle 8.11.1, compile SDK 36, target SDK 35, min SDK 23, Media3 1.11.1.

## Local compile

With the Android SDK, Java 17 and Gradle 8.11.1 installed:

```bash
sdkmanager "platforms;android-36" "build-tools;35.0.0"
gradle --no-daemon :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Validation

The standalone Java M3U parser can be checked without Android SDK:

```bash
mkdir -p /tmp/m3u-test
javac -d /tmp/m3u-test app/src/main/java/com/brave/simplem3uplayer/{Channel,M3uParser}.java tests/ParserTest.java
java -cp /tmp/m3u-test com.brave.simplem3uplayer.ParserTest /path/to/TV_BG_IT.m3u
```

**Build note:** No compiled APK is bundled here. Android UI compilation and device testing must be completed in Android SDK / GitHub Actions.
