# CaseShot

A lightweight native Android APK for capturing test evidence screenshots during manual testing.

Originally prototyped as an AutoX.js script, CaseShot has been rebuilt as a native Kotlin Android application to eliminate licensing and runtime dependency concerns with AutoX.js / Auto.js.

The APK lives under `android/`.

## Features

- **Floating overlay** with `Screenshot` and `Next` buttons — draggable via long-press.
- **Automatic screenshot capture** using Android AccessibilityService (`takeScreenshot`, API 34+).
- **Configurable naming** — prefix, case digit width, starting case index.
- **State persistence** — tracks `prefix / caseIndex / shotIndex` across sessions.
- **Conflict handling** — existing screenshot file detected; supports Skip / Overwrite / Ask modes.
- **Atomic file writes** — temp file + atomic move, no partial PNGs.
- **Output directory picker** — custom save location via SAF (Storage Access Framework).
- **Notification feedback** — capture result, failure, and case-switch notifications.

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| Build | Gradle KTS (AGP 8.7.3, Kotlin 2.0.21) |
| Min / Target SDK | 26 / 35 |
| Screenshot | `AccessibilityService.takeScreenshot()` (API 34+, no MediaProjection required) |
| Floating window | `TYPE_ACCESSIBILITY_OVERLAY` (no `SYSTEM_ALERT_WINDOW` permission needed) |
| Persistence | `SharedPreferences` |
| Tests | JUnit 4 (unit tests on naming, state, config, file store) |

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/caseshot/
│   │   │   ├── MainActivity.kt           — Configuration UI
│   │   │   ├── CaptureAccessibilityService.kt — Foreground capture service
│   │   │   ├── OverlayController.kt       — Floating overlay (Screenshot / Done / Conflict panel)
│   │   │   ├── OverlayWindowFlags.kt      — Overlay window flag constants
│   │   │   ├── NamingService.kt           — Filename generation
│   │   │   ├── FileStore.kt               — Atomic PNG write + conflict resolution
│   │   │   ├── ConfigRepository.kt        — Config persistence + normalization
│   │   │   ├── StateRepository.kt         — State machine (prefix/caseIndex/shotIndex)
│   │   │   └── Models.kt                  — CaseShotConfig, CaseShotState, enums
│   │   ├── res/                           — Layouts, drawables, colors, strings
│   │   └── AndroidManifest.xml
│   └── src/test/java/com/caseshot/        — Unit tests
├── build.gradle.kts                       — Root build script
└── settings.gradle.kts                    — Project settings
```

### Key Design Decisions

- **AccessibilityService over MediaProjection**: The MVP uses `AccessibilityService.takeScreenshot()` (introduced in Android 14) instead of the originally planned `MediaProjection` API. This avoids the complex `MediaProjection` permission flow and foreground service type declarations, while still providing reliable screen capture on Android 14+ devices.
- **`TYPE_ACCESSIBILITY_OVERLAY` over `TYPE_APPLICATION_OVERLAY`**: Because the capture service is an AccessibilityService, the floating overlay uses `TYPE_ACCESSIBILITY_OVERLAY` which has fewer restrictions and does not require `SYSTEM_ALERT_WINDOW` permission.
- **Atomic file writes**: Screenshots are written to a `.tmp` file first, then atomically moved to the target filename to prevent corrupted files on unexpected interruption.

## Build

Open `android/` in Android Studio and sync Gradle, or build from command line:

```powershell
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

The debug APK will be output at `android/app/build/outputs/apk/debug/app-debug.apk`.

## Android Permissions

The APK requests the following permissions:

| Permission | Purpose |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Capture screen via AccessibilityService; user must enable in system settings |
| `POST_NOTIFICATIONS` | Display capture result notifications (Android 13+) |
| `FOREGROUND_SERVICE` | Keep the capture service alive |

Note: `SYSTEM_ALERT_WINDOW` (overlay) permission is **not required** because the floating controls use `TYPE_ACCESSIBILITY_OVERLAY`.

## Manual Verification

1. **First launch** — Shows current target filename preview.
2. **Open accessibility settings** — Button navigates to system accessibility settings; enable "测试证迹助手服务".
3. **Grant notification permission** — Required on Android 13+ for capture result feedback.
4. **Save configuration** — Persists prefix, case digits, start index, and output directory.
5. **Floating `Screenshot`** — Captures and saves `001.png`.
6. **Repeated `Screenshot`** — Saves `001-2.png`, `001-3.png`.
7. **Floating `Done`** — Advances to next case: `002.png`.
8. **Prefix `049`** — Produces `049-001.png`, then `049-001-2.png`.
9. **Existing file** — Does not overwrite; shows conflict panel with Skip / Overwrite choices.