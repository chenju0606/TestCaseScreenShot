# CaseShot Native APK Design

## Goal

Rebuild CaseShot as a lightweight native Android APK to avoid unclear AutoX.js / Auto.js licensing and runtime dependency concerns.

The native APK must preserve the existing CaseShot product behavior:

- Floating controls remain required.
- The user captures the screen by tapping floating `Screenshot`.
- The user advances to the next case by tapping floating `Done`.
- Naming rules stay unchanged.
- State fields stay unchanged.
- Configuration fields stay aligned with the current MVP.

## Key Platform Choice

Use Kotlin with the native Android SDK.

Do not use:

- Flutter
- React Native
- AutoX.js
- Auto.js
- Other script automation runtimes

Reasons:

- Kotlin is the first-class Android language.
- MediaProjection and foreground services are Android platform APIs.
- The app can be distributed as a normal APK without bundling an automation runtime.
- The codebase stays small and auditable.

## Android Platform Constraints

Screen capture:

- Use Android `MediaProjection` APIs.
- The app must request screen capture consent from the user through the system projection prompt.
- For Android 14+ target behavior, the capture foreground service must declare media projection foreground-service support.
- Declare `android.permission.FOREGROUND_SERVICE` and `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION` in the manifest.
- Declare `android:foregroundServiceType="mediaProjection"` on the capture service.
- The service should run as a foreground service while capture controls are active.

Floating window:

- Use an overlay window for the floating controls.
- Declare `android.permission.SYSTEM_ALERT_WINDOW` in the manifest.
- The app must request overlay permission from Android settings before showing the floating controls.
- If overlay permission is missing, the app should guide the user to the system settings screen.

Storage:

- MVP should save locally.
- Use an app-managed default directory first.
- Provide a configurable output directory only if it can be implemented safely on modern Android storage rules.
- If broad external path writing is blocked by the device, the UI should explain that the user needs to choose a writable directory.

## MVP Scope

Included:

- Native Android APK project.
- Kotlin source code.
- Main configuration screen.
- Foreground capture service.
- MediaProjection permission flow.
- Floating overlay with `Screenshot` and `Done`.
- Local numbered image saving.
- Persistent config.
- Persistent state.
- Manual verification documentation.

Deferred:

- SMB upload.
- WebDAV, NAS, MinIO, or other remote upload targets.
- OCR validation.
- HTML index generation.
- ZIP export.
- AI classification or test-step generation.
- System screenshot folder watching.

## User Flow

First launch:

1. User opens CaseShot.
2. User reviews or edits config.
3. App checks overlay permission.
4. If overlay permission is missing, app opens the Android overlay permission settings.
5. User returns to CaseShot.
6. User taps `Start`.
7. App launches the screen capture consent prompt.
8. User grants capture permission.
9. App starts `CaptureService` as a foreground service.
10. Floating controls appear.

Capture:

1. User taps floating `Screenshot`.
2. App optionally hides the floating controls.
3. App waits `captureDelayMs`.
4. App captures the current screen through `MediaProjection`.
5. App saves the image into the configured output location.
6. App increments `shotIndex` only after the file is saved successfully.
7. App restores the floating controls.

Done:

1. User taps floating `Done`.
2. App increments `caseIndex`.
3. App resets `shotIndex` to `0`.
4. App persists state.

Stop:

1. User taps `Stop` in the app or notification.
2. App removes floating controls.
3. App stops the foreground service.

## Naming Rules

The native APK must preserve the current naming rules.

State:

```json
{
  "prefix": "",
  "caseIndex": 1,
  "shotIndex": 0
}
```

When `prefix` is empty:

- First screenshot in case 1: `0001.png`
- Second screenshot in case 1: `0001-2.png`
- Third screenshot in case 1: `0001-3.png`

When `prefix` is `049`:

- First screenshot in case 1: `049-0001.png`
- Second screenshot in case 1: `049-0001-2.png`
- Third screenshot in case 1: `049-0001-3.png`

Generation rule:

- Build base case id:
  - Empty prefix: `{caseIndexPadded}`
  - Non-empty prefix: `{prefix}-{caseIndexPadded}`
- Build filename:
  - `shotIndex == 0`: `{base}.png`
  - `shotIndex > 0`: `{base}-{shotIndex + 1}.png`

## Configuration

The native app should preserve these MVP config fields:

```json
{
  "prefix": "",
  "caseDigits": 4,
  "outputDir": "",
  "captureDelayMs": 300,
  "hideFloatingWindowBeforeCapture": true,
  "imageFormat": "png",
  "enableSmb": false,
  "smbUrl": ""
}
```

Notes:

- `prefix` is optional.
- `caseDigits` defaults to `4`.
- `outputDir` should be empty by default if the native app uses an app-managed directory.
- `imageFormat` is fixed to `png` in the MVP.
- `enableSmb` and `smbUrl` are retained for future compatibility but not active in the MVP.

## Architecture

`MainActivity`:

- Shows the configuration screen.
- Checks overlay permission.
- Starts screen capture permission flow.
- Starts and stops `CaptureService`.
- Displays current state preview.

`CaptureService`:

- Runs as a foreground service.
- Owns active `MediaProjection` session.
- Owns overlay lifecycle while running.
- Handles capture and done commands from overlay buttons.
- Shows persistent notification while active.

`OverlayController`:

- Creates and removes the floating window.
- Shows two buttons: `Screenshot` and `Done`.
- Can hide/show itself around capture timing.
- Sends actions to `CaptureService`.

`ScreenCaptureEngine`:

- Captures the current screen from `MediaProjection`.
- Converts the captured frame to PNG bytes.
- Reports capture errors without changing state.

`FileStore`:

- Resolves output directory.
- Checks target file conflicts.
- Writes PNG files.
- Returns saved file path.

`NamingService`:

- Formats case number.
- Builds base case id.
- Builds final filename.

`StateRepository`:

- Persists `prefix`, `caseIndex`, and `shotIndex`.
- Advances `shotIndex` after successful save.
- Advances `caseIndex` and resets `shotIndex` on done.

`ConfigRepository`:

- Persists config.
- Normalizes empty or invalid values.
- Keeps SMB fields saved but inactive.

## Data Flow

Capture button:

```text
OverlayController
-> CaptureService
-> NamingService builds target filename from current state
-> OverlayController hides controls if configured
-> ScreenCaptureEngine captures current screen
-> FileStore writes PNG
-> StateRepository increments shotIndex
-> OverlayController restores controls
-> CaptureService updates notification/state preview
```

Done button:

```text
OverlayController
-> CaptureService
-> StateRepository nextCase()
-> CaptureService updates notification/state preview
```

## Error Handling

- If overlay permission is missing, do not start floating controls. Show a clear action to open settings.
- If screen capture permission is denied, do not start capture service. Keep the app on the main screen.
- If foreground service startup fails, show an error and keep state unchanged.
- If capture fails, show a toast/notification message and keep state unchanged.
- If target filename already exists, do not overwrite. Show a conflict message and keep state unchanged.
- If output directory is unavailable, show a message and keep state unchanged.
- Always restore the floating window after a capture attempt when it was hidden.

## UI Scope

Main screen:

- Current target filename preview.
- Prefix input.
- Case digits input.
- Capture delay input.
- Hide floating window toggle.
- Output location display.
- `Start` button.
- `Stop` button.
- Overlay permission status.
- Capture permission status.

Floating overlay:

- `截图`
- `完成`

Notification:

- Shows CaseShot is running.
- Shows current case id.
- Provides a `Stop` action if practical in the MVP.

## Testing Strategy

Unit tests:

- Naming rules with and without prefix.
- State transitions for capture success and done.
- Config normalization.
- File conflict behavior using a temporary directory.

Instrumented or manual Android verification:

- Overlay permission flow.
- MediaProjection permission flow.
- Foreground service notification appears.
- Floating `截图` captures and saves `0001.png`.
- Repeated `截图` saves `0001-2.png`, then `0001-3.png`.
- Floating `完成` moves target to `0002.png`.
- Prefix `049` produces `049-0001.png`, then `049-0001-2.png`.
- Existing target filename does not overwrite and does not advance state.
- Hidden overlay mode keeps controls out of screenshots where Android permits.

## Migration From Current AutoX MVP

Reuse conceptually:

- Naming rules.
- State machine.
- Config fields.
- README manual verification checklist.

Do not reuse directly:

- AutoX.js runtime code.
- AutoX floating window code.
- AutoX WebView bridge code.
- JavaScript-specific file paths.

The existing AutoX implementation can remain in the repository until the native APK reaches parity. Once native APK is verified, remove or archive AutoX-specific scripts and update README to describe the APK path as primary.

## References

- Android MediaProjection official documentation: https://developer.android.com/media/grow/media-projection
- Android foreground services official documentation: https://developer.android.com/develop/background-work/services/foreground-services
- Android overlay permission API reference: https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW
