# Simple M3U TV v1.5.0

## Changes
- Responsive compact phone interface: reduced header, separate status line and compact buttons; channel sidebar adapts to width, supports increased system font scale.
- TV remote: focusable controls and channel rows, highlighted D-pad focus, OK selects, Back closes settings/channel panel, MENU opens Settings, CHANNEL_UP/DOWN and MEDIA_NEXT/PREVIOUS change channel.
- Android TV launcher category supported; no touchscreen requirement.
- Maintains Media3 playback, saved playlist, channel switching and stream retry logic from v1.4.
- English UI and Android 6.0+ support.

## Build
Upload the **contents** of this directory (not the ZIP itself) to your GitHub repository and run the supplied `.github/workflows/build-apk.yml` workflow. Download the `app-debug.apk` artifact.

## Test on real devices
Verify arrow navigation / OK / Back on your Android TV remote, file selection with Android's installed document provider, increased display/font scaling on phone, and live-stream reconnect. This environment has no Android SDK to run a full APK build.
