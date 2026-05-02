# Sari-Sari Store

Offline-first Android app for daily sari-sari store operations. The app starts empty on first install and works without internet after installation.

## Stack

- Kotlin
- Jetpack Compose
- Room
- MVVM + repository pattern
- CameraX
- Material 3

## Key Features

- Empty first launch with default store title `My Store`
- Local product management with optional product photos
- Separate GCash cash in / cash out records with required receipt image and optional signature
- Utang customer tracking with active and paid entry history
- Local backup and restore using a user-selected device folder
- Local settings for store title, low stock threshold, theme, passcode, and backup location
- Offline-first local database and app-private image storage

## Project Structure

```text
app/src/main/java/com/example/sarisaristore
|-- data/local
|-- data/repository
|-- camera
|-- security
|-- ui/components
|-- ui/feature
|-- ui/navigation
|-- ui/theme
`-- util
```

## Build And Run

1. Install Android Studio with the Android SDK and JDK 17 support.
2. Open this folder in Android Studio.
3. Let Gradle sync the project.
4. Run the `app` configuration on an Android phone or emulator with API 24+.

CLI build once Java and the Android SDK are available in your environment:

```powershell
.\gradlew.bat assembleDebug
```

## Permissions

- `android.permission.CAMERA` for product and receipt capture

No storage permission is required because images are stored in app-private storage.

## Architecture Notes

- Room is the single source of truth.
- Repositories coordinate validation, transactions, and image cleanup.
- ViewModels expose screen state as `StateFlow`.
- Compose screens react to local database updates in real time through Room `Flow`.
- Passcodes are stored locally as salted hashes, never plaintext.

## Current v1 Limits

- No cart or checkout flow yet.
- No cloud sync, login, or backend integration.
- Historical sales tables are still kept for compatibility, but there is no active sales screen in the current UI.
