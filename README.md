# Simple M3U TV v1.1 — Media3/ExoPlayer

Minimal Android IPTV player, Java, Android 6.0+ (API 23), Media3 1.11.1.

## Features

- Import local M3U/M3U8 via Android file picker (Storage Access Framework)
- List channels, previous/next, automatic next channel if initial playback does not reach READY within ~3.2 seconds or errors
- Stop after one complete unsuccessful pass through the playlist (avoid infinite cycles)
- Retain playlist URI access and last selected channel across launches
- HTTP/HTTPS IPTV including HLS; per-channel `#EXTVLCOPT:http-user-agent` and `http-referrer`
- No account, ads, or server

**Limits**: Android's installed decoders still determine codec compatibility. RTSP/RTMP/UDP and DRM streams are not supported by this project. Three seconds is deliberately aggressive for slow streams; modify `LOAD_TIMEOUT_MS` if necessary. Playlist names parse from `#EXTINF`, including malformed `... group-title="..." - Channel Name` metadata.

## Compile without Android Studio: GitHub Actions

1. Create a new GitHub repository (private is fine) without initializing README.
2. Unzip this archive on your computer.
3. On GitHub use **Add file > Upload files** to upload the project files/folders, including hidden `.github/workflows/build-apk.yml` (a desktop Git client or git CLI is more reliable for directories).
4. Commit into the `main` branch.
5. Open **Actions > Build Android APK > Run workflow**, or let the push trigger the build.
6. Open the completed build, click the `SimpleM3UPlayer-debug` artifact, unzip it, install `app-debug.apk` on your Android device.

IMPORTANT: GitHub download is a ZIP artifact, not the APK itself. `app-debug.apk` will be inside it.

## Compile locally on Debian 13

Recommended: install Android Studio, open this folder, wait for Gradle sync, choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

CLI (requires JDK 17, Android SDK cmdline-tools and SDK platform 35, Gradle 8.9):

```bash
sdkmanager "platforms;android-35" "build-tools;35.0.0"
gradle :app:assembleDebug
```

APK location: `app/build/outputs/apk/debug/app-debug.apk`.

For a reproducible command-line build, the project uses GitHub Actions installing Gradle 8.9; this source ZIP does not include a Gradle wrapper JAR. Debug APK is automatically signed using the Android debug key. A production build needs a separately managed release keystore.

## Parser smoke test (JDK only, no Android SDK)

```bash
mkdir -p tests/out
javac -d tests/out app/src/main/java/com/brave/simplem3uplayer/{Channel,M3uParser}.java tests/ParserTest.java
java -cp tests/out com.brave.simplem3uplayer.ParserTest /path/to/TV_BG_IT.m3u
```

The included ParserTest expects the user-supplied 92-channel sample. No user playlist is packaged inside this project in order not to upload stream URLs or private credentials to GitHub.

## Permissions

INTERNET only. `usesCleartextTraffic=true` is needed for legacy HTTP streams. File access uses Android document-picker persistent grants, without broad storage permission.
