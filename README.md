# Offline Music Player

A simple Android offline music player built with Kotlin, Jetpack Compose and AndroidX Media3.

## Features
- Reads music stored on the device
- Search by title or artist
- Play/pause/next
- Background playback through MediaSessionService
- Media notification / lock-screen controls
- No streaming or account required

## Build
1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Connect an Android phone or start an emulator.
4. Build and run the `app` configuration.
5. Grant music permission when prompted.

## Note
The app uses Android's MediaStore, so songs need to be visible to the Android media library.
