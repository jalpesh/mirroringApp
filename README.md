# mirroringApp

A fast screen mirroring controller that prioritises USB-C / DisplayPort Alt Mode mirroring to HDMI while offering modern wireless fallbacks such as Wi-Fi Direct and Miracast. The project targets legacy devices such as the Moto G6 (Android 8+) but scales to the latest Android releases.

## Features

- **USB-C first**: Detects external HDMI/DisplayPort displays, spins up a dedicated `Presentation` hosted `TextureView`, and pipes `MediaProjection` frames straight into the HDMI sink for the lowest possible latency.
- **Wireless fallbacks**: Includes placeholders for Wi-Fi Direct and Miracast to compare throughput and latency trade-offs without rebuilding the app.
- **Low-latency tuning**: Switches to two-buffer virtual display pipelines, trusted display flags on Android 13+, and hardware-encoder hints for reduced end-to-end delay, now backed by a background `HandlerThread` to drain buffers promptly.
- **Performance telemetry**: Streams frame cadence and FPS statistics to logcat so you can validate latency gains on devices like the Moto G6.
- **Foreground mirroring service**: Keeps the projection alive even when the app goes to the background.
- **DataStore-backed preferences**: Remembers the last connection profile and performance toggles.
- **Jetpack Compose UI**: Simple interface optimised for one-hand operation while connecting cables.

## Project structure

```
mirroringApp/
├── build.gradle.kts        # Root build configuration
├── settings.gradle.kts     # Gradle settings
└── app/
    ├── build.gradle.kts    # Android application module
    ├── src/main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/example/mirroringapp/
    │   │   ├── MainActivity.kt
    │   │   ├── MirroringUiState.kt
    │   │   ├── MirroringViewModel.kt
    │   │   ├── mirroring/
    │   │   │   ├── ConnectionOption.kt
    │   │   │   ├── ExternalDisplayReceiver.kt
    │   │   │   ├── MirroringController.kt
    │   │   │   ├── MirroringIntentFactory.kt
    │   │   │   ├── MirroringService.kt
    │   │   │   ├── MirroringSession.kt
    │   │   │   └── VideoEncoder.kt
    │   │   └── util/
    │   │       └── PerformanceLogger.kt
    │   └── res/
    │       ├── values/...
    │       └── drawable/ic_stat_name.xml
```

## Building

1. Install Android Studio Iguana (or newer) with the Android Gradle Plugin 8.3+ and Kotlin 1.9.23.
2. Open the root folder (`mirroringApp/`) in Android Studio.
3. Sync the Gradle project and build the `app` module.

## Running

1. Connect the target device (e.g. Moto G6) via USB debugging.
2. Install and launch the application.
3. Grant the screen capture permission when prompted.
4. Select the desired connection type (USB-C recommended for LG QNED and other HDMI displays) and toggle low-latency options.
5. Tap **Start mirroring** to begin the foreground service and output to the external display.

> **Tip:** For the lowest latency over USB-C, use high-quality USB-C to HDMI cables that support DisplayPort 1.4 Alt Mode and ensure the external display is set to its native refresh rate.

## Notes

- The wireless pathways (Wi-Fi Direct / Miracast) act as extension points. Implementations can plug into `MirroringSession` to provide dedicated sinks.
- Moto G6-class hardware may require USB-C to HDMI adapters with external power for stable throughput.
- Ensure the device's USB-C port supports DisplayPort Alt Mode; some variants may require DisplayLink docks instead.
