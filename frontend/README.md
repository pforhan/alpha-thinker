# Alpha Thinker Frontend (Flutter)

This is the original Flutter implementation of the Alpha Thinker UI. The UI is being migrated to a Kotlin Multiplatform / Compose Multiplatform app, which lives in the repository root under `composeApp/`. This directory contains the legacy frontend and is kept around during the migration.

All development commands below use the Flutter CLI.

## Building

### Prerequisites

- **Flutter SDK**

### Setup

```bash
flutter pub get
```

### Running

Run on a connected device or currently selected device:

```bash
flutter run
```

Run on a specific platform:

```bash
flutter run -d chrome        # web
flutter run -d macos         # macOS desktop
flutter run -d linux         # Linux desktop
flutter run -d windows       # Windows desktop
flutter run -d <ios-device>  # iOS (start a simulator first, e.g. `open -a Simulator`)
flutter run -d <android-id>  # Android (start an emulator or connect a device)
```

### Testing

```bash
flutter test
flutter analyze
```