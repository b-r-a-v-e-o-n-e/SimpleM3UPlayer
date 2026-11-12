# Simple M3U TV v1.7.0

Android 6.0+ (minSdk 23). Media3 1.11.1.

## New in 1.7.0
- All action buttons on **one horizontal row at the bottom**; scrollable on small screens. No top action buttons.
- Main application background `#0a0e17`. Button and playlist panel background `#0d0d0d`. Selected channel `#ffa300`.
- Button outlines exactly **2 device pixels**, centered icons.
- D-pad-capable built-in folder browser for Android 7 (API 24/25) and older TV ROMs; works without external DocumentsUI.
- Requests legacy read permission on Android 6-12; system picker remains available as an alternate.
- Remembers selected playlist, restores it after restart (provided it remains readable).
- Fullscreen and automatic hiding of bottom control row after 3 seconds; tap video to restore controls.
- Preserves channels, retry/auto-next settings, and Media3 streaming.

## Android 7 TV Box
Click the **Open playlist** icon in the bottom row. Grant storage access when prompted. Browse *Internal storage*, *Downloads*, or a USB volume from the built-in list. Use the D-pad arrows and OK. Only `.m3u` and `.m3u8` files are shown. The **System picker** option is kept for devices with a working file manager.

If an external drive is not readable, copy the M3U playlist into `/sdcard/Download/` using the box file manager or ADB, then open **Downloads** in the built-in browser.

## Build
Upload repository files (not the enclosing ZIP) to GitHub. The included `.github/workflows/build-apk.yml` builds a debug APK. This source has **not been built in this environment**; verify the CI build before installation.
