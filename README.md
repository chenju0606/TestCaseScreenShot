# CaseShot

## Native APK Direction

CaseShot is migrating from the AutoX.js script MVP to a lightweight native Android APK because the AutoX.js / Auto.js licensing and runtime dependency story is not clear enough for long-term use.

The native APK keeps the same product behavior:

- Floating `截图` captures the current screen.
- Floating `完成` advances to the next case.
- Naming rules stay the same.
- State stays `prefix / caseIndex / shotIndex`.
- Config fields stay aligned with the script MVP.

The native APK lives under `android/`.

### Build

Open `android/` in Android Studio and sync Gradle. If a Gradle wrapper is available, run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

### Android Permissions

The native APK needs:

- Overlay permission for the floating controls.
- Screen capture consent through Android MediaProjection.
- Foreground service notification permission on Android versions that require notification runtime approval.

### Native Manual Verification

- First launch shows current target filename preview.
- Overlay permission button opens Android settings.
- Start asks for screen capture permission.
- Floating `截图` saves `0001.png`.
- Repeated `截图` saves `0001-2.png` and `0001-3.png`.
- Floating `完成` moves the next target to `0002.png`.
- Prefix `049` saves `049-0001.png`, then `049-0001-2.png`.
- Existing target filename does not overwrite and does not advance state.