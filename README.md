# SwiftScan PDF

A fast, local-only Android document scanner app with precision cropping, a magnifier, and PDF management. No cloud processing.

## Prerequisites

- **Android Studio** Ladybug (2024.2.1) or newer
- **JDK 17** (bundled with Android Studio or install separately)
- **Android SDK 35** (install via Android Studio SDK Manager)
- An Android phone running **Android 10 (API 29)** or higher
- **Google Play Services** on the device (required for ML Kit document scanner)

## Build & Run

### Option 1: Android Studio (recommended)

1. Open Android Studio
2. **File > Open** and select the `quick-scan` folder
3. Wait for Gradle sync to finish (first sync downloads ~1 GB of dependencies)
4. Connect your phone via USB with **USB debugging** enabled (see below)
5. Select your device in the toolbar device dropdown
6. Click the **Run** button (green triangle) or press `Shift+F10`

### Option 2: Command line

```bash
cd quick-scan

# Build the debug APK
./gradlew assembleDebug

# Install directly to a connected device
./gradlew installDebug

# Or install the APK manually
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Enable USB Debugging on your phone

1. Go to **Settings > About phone**
2. Tap **Build number** 7 times to enable Developer options
3. Go back to **Settings > Developer options**
4. Enable **USB debugging**
5. Connect your phone via USB and accept the debugging prompt

### Samsung/OneUI specific path

1. **Settings > About phone > Software information**
2. Tap **Build number** 7 times
3. **Settings > Developer options > USB debugging**

## Permissions

The app will request **Camera** permission on first launch. Grant it to use the scanner.

## Usage

- **Quick Scan** — Uses Google ML Kit's built-in scanner (handles camera, cropping, and enhancement automatically)
- **Manual Scan** — Tap the shutter button, then adjust crop corners with the magnifier for precise control
- **Merge** — Combine multiple saved PDFs into one from the home screen merge button

## License

MIT — see [LICENSE](LICENSE).
