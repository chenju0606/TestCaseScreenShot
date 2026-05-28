# CaseShot Native APK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lightweight native Kotlin Android APK for CaseShot that preserves the current floating capture workflow, naming rules, state machine, and configuration semantics.

**Architecture:** Add a new `android/` subproject beside the existing AutoX.js MVP so both implementations can coexist during migration. Keep pure business rules in testable Kotlin classes, isolate Android platform code in `MainActivity`, `CaptureService`, `OverlayController`, and `ScreenCaptureEngine`, and use app-managed local storage for the MVP.

**Tech Stack:** Kotlin, Android SDK, Android Gradle Plugin, MediaProjection, foreground service, overlay window, SharedPreferences, JUnit.

---

## Environment Notes

Local inspection found JDK 17 at `D:\Program Files\jdk-17.0.2\bin\java.exe`, but no global `gradle`, `ANDROID_HOME`, or `ANDROID_SDK_ROOT` in the current shell.

Implementation can still create the Android project files. Building the APK requires either:

- Android Studio opening `android/` and syncing the Gradle project.
- A configured Android SDK plus Gradle wrapper or local Gradle installation.

Use `./gradlew test` and `./gradlew assembleDebug` when a Gradle wrapper is available. On Windows PowerShell, use `.\gradlew.bat test` and `.\gradlew.bat assembleDebug`.

## File Structure

- Create `android/settings.gradle.kts`: Gradle project name and app module include.
- Create `android/build.gradle.kts`: Android Gradle Plugin and Kotlin plugin declarations.
- Create `android/app/build.gradle.kts`: Android app config, SDK levels, Kotlin/JVM config, and unit test dependency.
- Create `android/app/src/main/AndroidManifest.xml`: app permissions, `MainActivity`, and `CaptureService`.
- Create `android/app/src/main/java/com/caseshot/MainActivity.kt`: native configuration screen and permission flow.
- Create `android/app/src/main/java/com/caseshot/CaptureService.kt`: foreground service, MediaProjection lifecycle, capture/done commands.
- Create `android/app/src/main/java/com/caseshot/OverlayController.kt`: floating `截图` / `完成` controls.
- Create `android/app/src/main/java/com/caseshot/ScreenCaptureEngine.kt`: MediaProjection capture to bitmap/PNG bytes.
- Create `android/app/src/main/java/com/caseshot/FileStore.kt`: output directory resolution and PNG file writing.
- Create `android/app/src/main/java/com/caseshot/NamingService.kt`: filename rules.
- Create `android/app/src/main/java/com/caseshot/StateRepository.kt`: state persistence and transitions.
- Create `android/app/src/main/java/com/caseshot/ConfigRepository.kt`: config persistence and normalization.
- Create `android/app/src/main/java/com/caseshot/Models.kt`: config/state data classes.
- Create `android/app/src/test/java/com/caseshot/NamingServiceTest.kt`: naming unit tests.
- Create `android/app/src/test/java/com/caseshot/StateRepositoryTest.kt`: state transition unit tests.
- Create `android/app/src/test/java/com/caseshot/ConfigRepositoryTest.kt`: config normalization unit tests.
- Create `android/app/src/test/java/com/caseshot/FileStoreTest.kt`: target conflict behavior unit test.
- Modify `README.md`: add native APK setup and keep AutoX.js as legacy/MVP script path.

## Task 1: Android Project Skeleton

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create Gradle settings**

Write `android/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CaseShotNative"
include(":app")
```

- [ ] **Step 2: Create root Gradle build file**

Write `android/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
```

- [ ] **Step 3: Create app Gradle build file**

Write `android/app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.caseshot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.caseshot"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 4: Create Android manifest**

Write `android/app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="CaseShot"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".CaptureService"
            android:exported="false"
            android:foregroundServiceType="mediaProjection" />
    </application>
</manifest>
```

- [ ] **Step 5: Add minimal theme**

Create `android/app/src/main/res/values/styles.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:colorAccent">#205C52</item>
    </style>
</resources>
```

- [ ] **Step 6: Run skeleton build check**

Run from `android/`: `.\gradlew.bat tasks`

Expected when Gradle wrapper exists: Gradle lists available tasks. If no wrapper exists yet, record that Android Studio/Gradle setup is required before command verification.

- [ ] **Step 7: Commit skeleton**

```bash
git add android/settings.gradle.kts android/build.gradle.kts android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml android/app/src/main/res/values/styles.xml
git commit -m "chore: add native Android project skeleton"
```

## Task 2: Domain Models And Naming Rules

**Files:**
- Create: `android/app/src/main/java/com/caseshot/Models.kt`
- Create: `android/app/src/main/java/com/caseshot/NamingService.kt`
- Create: `android/app/src/test/java/com/caseshot/NamingServiceTest.kt`

- [ ] **Step 1: Write naming tests**

Write `android/app/src/test/java/com/caseshot/NamingServiceTest.kt`:

```kotlin
package com.caseshot

import org.junit.Assert.assertEquals
import org.junit.Test

class NamingServiceTest {
    private val service = NamingService()

    @Test
    fun formatsCaseIndexWithPadding() {
        assertEquals("0001", service.formatCaseIndex(1, 4))
        assertEquals("0049", service.formatCaseIndex(49, 4))
        assertEquals("12345", service.formatCaseIndex(12345, 4))
    }

    @Test
    fun buildsCaseIdWithoutPrefix() {
        assertEquals("0001", service.buildCaseId("", 1, 4))
        assertEquals("0012", service.buildCaseId("   ", 12, 4))
    }

    @Test
    fun buildsCaseIdWithPrefix() {
        assertEquals("049-0001", service.buildCaseId("049", 1, 4))
        assertEquals("case-012", service.buildCaseId(" case ", 12, 3))
    }

    @Test
    fun buildsFilenames() {
        assertEquals("0001.png", service.buildFilename("", 1, 0, 4))
        assertEquals("0001-2.png", service.buildFilename("", 1, 1, 4))
        assertEquals("049-0001.png", service.buildFilename("049", 1, 0, 4))
        assertEquals("049-0001-3.png", service.buildFilename("049", 1, 2, 4))
    }
}
```

- [ ] **Step 2: Run tests and verify red**

Run from `android/`: `.\gradlew.bat testDebugUnitTest`

Expected: FAIL because `NamingService` does not exist.

- [ ] **Step 3: Create data models**

Write `android/app/src/main/java/com/caseshot/Models.kt`:

```kotlin
package com.caseshot

data class CaseShotConfig(
    val prefix: String = "",
    val caseDigits: Int = 4,
    val outputDir: String = "",
    val captureDelayMs: Long = 300L,
    val hideFloatingWindowBeforeCapture: Boolean = true,
    val imageFormat: String = "png",
    val enableSmb: Boolean = false,
    val smbUrl: String = ""
)

data class CaseShotState(
    val prefix: String = "",
    val caseIndex: Int = 1,
    val shotIndex: Int = 0
)
```

- [ ] **Step 4: Implement naming service**

Write `android/app/src/main/java/com/caseshot/NamingService.kt`:

```kotlin
package com.caseshot

class NamingService {
    fun normalizePrefix(prefix: String?): String = prefix.orEmpty().trim()

    fun formatCaseIndex(caseIndex: Int, caseDigits: Int): String {
        val safeIndex = caseIndex.coerceAtLeast(1)
        val safeDigits = caseDigits.coerceAtLeast(1)
        return safeIndex.toString().padStart(safeDigits, '0')
    }

    fun buildCaseId(prefix: String?, caseIndex: Int, caseDigits: Int): String {
        val cleanPrefix = normalizePrefix(prefix)
        val paddedCase = formatCaseIndex(caseIndex, caseDigits)
        return if (cleanPrefix.isEmpty()) paddedCase else "$cleanPrefix-$paddedCase"
    }

    fun buildFilename(prefix: String?, caseIndex: Int, shotIndex: Int, caseDigits: Int): String {
        val base = buildCaseId(prefix, caseIndex, caseDigits)
        val safeShot = shotIndex.coerceAtLeast(0)
        return if (safeShot == 0) "$base.png" else "$base-${safeShot + 1}.png"
    }
}
```

- [ ] **Step 5: Run tests and verify green**

Run from `android/`: `.\gradlew.bat testDebugUnitTest`

Expected: PASS for `NamingServiceTest`.

- [ ] **Step 6: Commit domain naming**

```bash
git add android/app/src/main/java/com/caseshot/Models.kt android/app/src/main/java/com/caseshot/NamingService.kt android/app/src/test/java/com/caseshot/NamingServiceTest.kt
git commit -m "feat: add native CaseShot naming rules"
```

## Task 3: Config And State Persistence

**Files:**
- Create: `android/app/src/main/java/com/caseshot/ConfigRepository.kt`
- Create: `android/app/src/main/java/com/caseshot/StateRepository.kt`
- Create: `android/app/src/test/java/com/caseshot/ConfigRepositoryTest.kt`
- Create: `android/app/src/test/java/com/caseshot/StateRepositoryTest.kt`

- [ ] **Step 1: Write repository tests**

Write `android/app/src/test/java/com/caseshot/ConfigRepositoryTest.kt`:

```kotlin
package com.caseshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigRepositoryTest {
    @Test
    fun normalizesMissingConfigValues() {
        val config = ConfigRepository.normalize(CaseShotConfig())
        assertEquals("", config.prefix)
        assertEquals(4, config.caseDigits)
        assertEquals("", config.outputDir)
        assertEquals(300L, config.captureDelayMs)
        assertTrue(config.hideFloatingWindowBeforeCapture)
        assertEquals("png", config.imageFormat)
        assertFalse(config.enableSmb)
        assertEquals("", config.smbUrl)
    }

    @Test
    fun normalizesProvidedConfigValues() {
        val config = ConfigRepository.normalize(
            CaseShotConfig(
                prefix = " 049 ",
                caseDigits = 3,
                outputDir = " /sdcard/MyCases ",
                captureDelayMs = 500L,
                hideFloatingWindowBeforeCapture = false,
                imageFormat = ".PNG",
                enableSmb = true,
                smbUrl = " smb://192.168.1.10/Cases "
            )
        )

        assertEquals("049", config.prefix)
        assertEquals(3, config.caseDigits)
        assertEquals("/sdcard/MyCases", config.outputDir)
        assertEquals(500L, config.captureDelayMs)
        assertFalse(config.hideFloatingWindowBeforeCapture)
        assertEquals("png", config.imageFormat)
        assertTrue(config.enableSmb)
        assertEquals("smb://192.168.1.10/Cases", config.smbUrl)
    }
}
```

Write `android/app/src/test/java/com/caseshot/StateRepositoryTest.kt`:

```kotlin
package com.caseshot

import org.junit.Assert.assertEquals
import org.junit.Test

class StateRepositoryTest {
    @Test
    fun alignsStatePrefixWithConfigPrefix() {
        val state = StateRepository.normalize(CaseShotState(prefix = "old", caseIndex = 3, shotIndex = 2), "049")
        assertEquals(CaseShotState(prefix = "049", caseIndex = 3, shotIndex = 2), state)
    }

    @Test
    fun captureSuccessIncrementsShotIndex() {
        val state = StateRepository.afterCaptureSuccess(CaseShotState(prefix = "049", caseIndex = 1, shotIndex = 0))
        assertEquals(CaseShotState(prefix = "049", caseIndex = 1, shotIndex = 1), state)
    }

    @Test
    fun doneMovesToNextCaseAndClearsShotIndex() {
        val state = StateRepository.nextCase(CaseShotState(prefix = "", caseIndex = 1, shotIndex = 3))
        assertEquals(CaseShotState(prefix = "", caseIndex = 2, shotIndex = 0), state)
    }
}
```

- [ ] **Step 2: Run tests and verify red**

Run from `android/`: `.\gradlew.bat testDebugUnitTest`

Expected: FAIL because repositories do not exist.

- [ ] **Step 3: Implement config repository**

Write `android/app/src/main/java/com/caseshot/ConfigRepository.kt`:

```kotlin
package com.caseshot

import android.content.Context

class ConfigRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("caseshot_config", Context.MODE_PRIVATE)

    fun load(): CaseShotConfig = normalize(
        CaseShotConfig(
            prefix = prefs.getString("prefix", "").orEmpty(),
            caseDigits = prefs.getInt("caseDigits", 4),
            outputDir = prefs.getString("outputDir", "").orEmpty(),
            captureDelayMs = prefs.getLong("captureDelayMs", 300L),
            hideFloatingWindowBeforeCapture = prefs.getBoolean("hideFloatingWindowBeforeCapture", true),
            imageFormat = prefs.getString("imageFormat", "png").orEmpty(),
            enableSmb = prefs.getBoolean("enableSmb", false),
            smbUrl = prefs.getString("smbUrl", "").orEmpty()
        )
    )

    fun save(config: CaseShotConfig): CaseShotConfig {
        val normalized = normalize(config)
        prefs.edit()
            .putString("prefix", normalized.prefix)
            .putInt("caseDigits", normalized.caseDigits)
            .putString("outputDir", normalized.outputDir)
            .putLong("captureDelayMs", normalized.captureDelayMs)
            .putBoolean("hideFloatingWindowBeforeCapture", normalized.hideFloatingWindowBeforeCapture)
            .putString("imageFormat", normalized.imageFormat)
            .putBoolean("enableSmb", normalized.enableSmb)
            .putString("smbUrl", normalized.smbUrl)
            .apply()
        return normalized
    }

    companion object {
        fun normalize(config: CaseShotConfig): CaseShotConfig {
            val imageFormat = config.imageFormat.trim().removePrefix(".").lowercase().ifEmpty { "png" }
            return config.copy(
                prefix = config.prefix.trim(),
                caseDigits = config.caseDigits.coerceAtLeast(1),
                outputDir = config.outputDir.trim(),
                captureDelayMs = config.captureDelayMs.coerceAtLeast(0L),
                imageFormat = imageFormat,
                smbUrl = config.smbUrl.trim()
            )
        }
    }
}
```

- [ ] **Step 4: Implement state repository**

Write `android/app/src/main/java/com/caseshot/StateRepository.kt`:

```kotlin
package com.caseshot

import android.content.Context

class StateRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("caseshot_state", Context.MODE_PRIVATE)

    fun load(configPrefix: String): CaseShotState = normalize(
        CaseShotState(
            prefix = prefs.getString("prefix", "").orEmpty(),
            caseIndex = prefs.getInt("caseIndex", 1),
            shotIndex = prefs.getInt("shotIndex", 0)
        ),
        configPrefix
    )

    fun save(state: CaseShotState, configPrefix: String): CaseShotState {
        val normalized = normalize(state, configPrefix)
        prefs.edit()
            .putString("prefix", normalized.prefix)
            .putInt("caseIndex", normalized.caseIndex)
            .putInt("shotIndex", normalized.shotIndex)
            .apply()
        return normalized
    }

    companion object {
        fun normalize(state: CaseShotState, configPrefix: String): CaseShotState =
            state.copy(
                prefix = configPrefix.trim(),
                caseIndex = state.caseIndex.coerceAtLeast(1),
                shotIndex = state.shotIndex.coerceAtLeast(0)
            )

        fun afterCaptureSuccess(state: CaseShotState): CaseShotState =
            state.copy(
                caseIndex = state.caseIndex.coerceAtLeast(1),
                shotIndex = state.shotIndex.coerceAtLeast(0) + 1
            )

        fun nextCase(state: CaseShotState): CaseShotState =
            state.copy(
                caseIndex = state.caseIndex.coerceAtLeast(1) + 1,
                shotIndex = 0
            )
    }
}
```

- [ ] **Step 5: Run tests and verify green**

Run from `android/`: `.\gradlew.bat testDebugUnitTest`

Expected: PASS for naming, config, and state tests.

- [ ] **Step 6: Commit repositories**

```bash
git add android/app/src/main/java/com/caseshot/ConfigRepository.kt android/app/src/main/java/com/caseshot/StateRepository.kt android/app/src/test/java/com/caseshot/ConfigRepositoryTest.kt android/app/src/test/java/com/caseshot/StateRepositoryTest.kt
git commit -m "feat: add native config and state repositories"
```

## Task 4: File Store

**Files:**
- Create: `android/app/src/main/java/com/caseshot/FileStore.kt`
- Create: `android/app/src/test/java/com/caseshot/FileStoreTest.kt`

- [ ] **Step 1: Write file store test**

Write `android/app/src/test/java/com/caseshot/FileStoreTest.kt`:

```kotlin
package com.caseshot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileStoreTest {
    @Test
    fun writesPngBytesAndRejectsConflicts() {
        val dir = createTempDir(prefix = "caseshot-test")
        val fileStore = FileStore()
        val bytes = byteArrayOf(1, 2, 3)
        val target = fileStore.writePng(dir, "0001.png", bytes)

        assertTrue(target.exists())
        assertArrayEquals(bytes, target.readBytes())

        try {
            fileStore.writePng(dir, "0001.png", bytes)
            throw AssertionError("Expected conflict")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("Target file already exists"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
```

- [ ] **Step 2: Run tests and verify red**

Run from `android/`: `.\gradlew.bat testDebugUnitTest`

Expected: FAIL because `FileStore` does not exist.

- [ ] **Step 3: Implement file store**

Write `android/app/src/main/java/com/caseshot/FileStore.kt`:

```kotlin
package com.caseshot

import android.content.Context
import java.io.File

class FileStore {
    fun resolveOutputDir(context: Context, config: CaseShotConfig): File {
        val configured = config.outputDir.trim()
        val dir = if (configured.isEmpty()) {
            File(context.getExternalFilesDir(null), "Cases")
        } else {
            File(configured)
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Unable to create output directory: ${dir.absolutePath}")
        }
        return dir
    }

    fun writePng(outputDir: File, filename: String, pngBytes: ByteArray): File {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IllegalStateException("Unable to create output directory: ${outputDir.absolutePath}")
        }
        val target = File(outputDir, filename)
        if (target.exists()) {
            throw IllegalStateException("Target file already exists: ${target.absolutePath}")
        }
        target.writeBytes(pngBytes)
        return target
    }
}
```

- [ ] **Step 4: Run tests and verify green**

Run from `android/`: `.\gradlew.bat testDebugUnitTest`

Expected: PASS for all unit tests.

- [ ] **Step 5: Commit file store**

```bash
git add android/app/src/main/java/com/caseshot/FileStore.kt android/app/src/test/java/com/caseshot/FileStoreTest.kt
git commit -m "feat: add native file store"
```

## Task 5: Main Activity Permission And Config UI

**Files:**
- Create: `android/app/src/main/java/com/caseshot/MainActivity.kt`

- [ ] **Step 1: Implement main activity**

Write `android/app/src/main/java/com/caseshot/MainActivity.kt`:

```kotlin
package com.caseshot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var configRepository: ConfigRepository
    private lateinit var stateRepository: StateRepository
    private lateinit var namingService: NamingService
    private lateinit var projectionManager: MediaProjectionManager

    private lateinit var prefixInput: EditText
    private lateinit var caseDigitsInput: EditText
    private lateinit var outputDirInput: EditText
    private lateinit var captureDelayInput: EditText
    private lateinit var hideOverlayCheck: CheckBox
    private lateinit var targetPreview: TextView
    private lateinit var permissionStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configRepository = ConfigRepository(this)
        stateRepository = StateRepository(this)
        namingService = NamingService()
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        buildUi()
        loadConfigIntoUi()
        refreshPreview()
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 32, 32, 32)
        }

        targetPreview = TextView(this)
        permissionStatus = TextView(this)
        prefixInput = EditText(this).apply { hint = "前缀，可留空，例如 049" }
        caseDigitsInput = EditText(this).apply { hint = "用例编号位数"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        outputDirInput = EditText(this).apply { hint = "保存目录，留空使用应用目录" }
        captureDelayInput = EditText(this).apply { hint = "截屏延迟毫秒"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        hideOverlayCheck = CheckBox(this).apply { text = "截图前隐藏悬浮窗" }

        val overlayButton = Button(this).apply {
            text = "打开悬浮窗权限"
            setOnClickListener { openOverlaySettings() }
        }
        val saveButton = Button(this).apply {
            text = "保存配置"
            setOnClickListener {
                saveConfigFromUi()
                refreshPreview()
                Toast.makeText(this@MainActivity, "配置已保存", Toast.LENGTH_SHORT).show()
            }
        }
        val startButton = Button(this).apply {
            text = "开始服务"
            setOnClickListener { startCaptureFlow() }
        }
        val stopButton = Button(this).apply {
            text = "停止服务"
            setOnClickListener { stopService(Intent(this@MainActivity, CaptureService::class.java)) }
        }

        listOf(
            targetPreview,
            permissionStatus,
            prefixInput,
            caseDigitsInput,
            outputDirInput,
            captureDelayInput,
            hideOverlayCheck,
            overlayButton,
            saveButton,
            startButton,
            stopButton
        ).forEach { root.addView(it) }

        setContentView(root)
    }

    private fun loadConfigIntoUi() {
        val config = configRepository.load()
        prefixInput.setText(config.prefix)
        caseDigitsInput.setText(config.caseDigits.toString())
        outputDirInput.setText(config.outputDir)
        captureDelayInput.setText(config.captureDelayMs.toString())
        hideOverlayCheck.isChecked = config.hideFloatingWindowBeforeCapture
    }

    private fun saveConfigFromUi(): CaseShotConfig {
        val config = CaseShotConfig(
            prefix = prefixInput.text.toString(),
            caseDigits = caseDigitsInput.text.toString().toIntOrNull() ?: 4,
            outputDir = outputDirInput.text.toString(),
            captureDelayMs = captureDelayInput.text.toString().toLongOrNull() ?: 300L,
            hideFloatingWindowBeforeCapture = hideOverlayCheck.isChecked
        )
        return configRepository.save(config)
    }

    private fun refreshPreview() {
        val config = configRepository.load()
        val state = stateRepository.load(config.prefix)
        val filename = namingService.buildFilename(state.prefix, state.caseIndex, state.shotIndex, config.caseDigits)
        targetPreview.text = "下一张: $filename"
        permissionStatus.text = if (Settings.canDrawOverlays(this)) "悬浮窗权限: 已允许" else "悬浮窗权限: 未允许"
    }

    private fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun startCaptureFlow() {
        saveConfigFromUi()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许悬浮窗权限", Toast.LENGTH_SHORT).show()
            openOverlaySettings()
            return
        }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            startForegroundService(intent)
        } else if (requestCode == REQUEST_CAPTURE) {
            Toast.makeText(this, "未获得截屏权限", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val REQUEST_CAPTURE = 1001
    }
}
```

- [ ] **Step 2: Build compile check**

Run from `android/`: `.\gradlew.bat assembleDebug`

Expected: compilation reaches missing `CaptureService` if it has not been created yet, or succeeds after Task 6.

- [ ] **Step 3: Commit main activity**

```bash
git add android/app/src/main/java/com/caseshot/MainActivity.kt
git commit -m "feat: add native main activity"
```

## Task 6: Foreground Capture Service And Overlay

**Files:**
- Create: `android/app/src/main/java/com/caseshot/CaptureService.kt`
- Create: `android/app/src/main/java/com/caseshot/OverlayController.kt`

- [ ] **Step 1: Implement overlay controller**

Write `android/app/src/main/java/com/caseshot/OverlayController.kt`:

```kotlin
package com.caseshot

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout

class OverlayController(
    private val context: Context,
    private val onScreenshot: () -> Unit,
    private val onDone: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    fun show() {
        if (view != null) return
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(0xCC202124.toInt())
        }
        layout.addView(Button(context).apply {
            text = "截图"
            setOnClickListener { onScreenshot() }
        })
        layout.addView(Button(context).apply {
            text = "完成"
            setOnClickListener { onDone() }
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 220
        }

        windowManager.addView(layout, params)
        view = layout
    }

    fun hideForCapture() {
        view?.visibility = View.INVISIBLE
    }

    fun restoreAfterCapture() {
        view?.visibility = View.VISIBLE
    }

    fun remove() {
        view?.let { windowManager.removeView(it) }
        view = null
    }
}
```

- [ ] **Step 2: Implement capture service shell**

Write `android/app/src/main/java/com/caseshot/CaptureService.kt`:

```kotlin
package com.caseshot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast

class CaptureService : Service() {
    private lateinit var configRepository: ConfigRepository
    private lateinit var stateRepository: StateRepository
    private val namingService = NamingService()
    private val fileStore = FileStore()
    private var overlayController: OverlayController? = null
    private var mediaProjection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        configRepository = ConfigRepository(this)
        stateRepository = StateRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (intent?.hasExtra(EXTRA_RESULT_CODE) == true) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            if (resultData != null) {
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = manager.getMediaProjection(resultCode, resultData)
            }
        }
        ensureOverlay()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        overlayController?.remove()
        overlayController = null
        mediaProjection?.stop()
        mediaProjection = null
        super.onDestroy()
    }

    private fun ensureOverlay() {
        if (overlayController != null) return
        overlayController = OverlayController(
            context = this,
            onScreenshot = { captureCurrentScreen() },
            onDone = { markDone() }
        ).also { it.show() }
    }

    private fun captureCurrentScreen() {
        Toast.makeText(this, "截屏引擎将在下一任务接入", Toast.LENGTH_SHORT).show()
    }

    private fun markDone() {
        val config = configRepository.load()
        val current = stateRepository.load(config.prefix)
        val next = StateRepository.nextCase(current)
        stateRepository.save(next, config.prefix)
        Toast.makeText(this, "进入用例 ${namingService.buildCaseId(next.prefix, next.caseIndex, config.caseDigits)}", Toast.LENGTH_SHORT).show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "CaseShot", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("CaseShot 运行中")
            .setContentText("悬浮窗已开启")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "com.caseshot.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.caseshot.RESULT_DATA"
        private const val CHANNEL_ID = "caseshot_capture"
        private const val NOTIFICATION_ID = 49
    }
}
```

- [ ] **Step 3: Build compile check**

Run from `android/`: `.\gradlew.bat assembleDebug`

Expected: APK compiles, though screenshot saving is not wired until Task 7.

- [ ] **Step 4: Commit service and overlay**

```bash
git add android/app/src/main/java/com/caseshot/CaptureService.kt android/app/src/main/java/com/caseshot/OverlayController.kt
git commit -m "feat: add native foreground service and overlay"
```

## Task 7: MediaProjection Capture Engine Integration

**Files:**
- Create: `android/app/src/main/java/com/caseshot/ScreenCaptureEngine.kt`
- Modify: `android/app/src/main/java/com/caseshot/CaptureService.kt`

- [ ] **Step 1: Implement screen capture engine**

Write `android/app/src/main/java/com/caseshot/ScreenCaptureEngine.kt`:

```kotlin
package com.caseshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream

class ScreenCaptureEngine(private val context: Context) {
    fun capturePng(mediaProjection: MediaProjection): ByteArray {
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        val display = mediaProjection.createVirtualDisplay(
            "CaseShotCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            Handler(Looper.getMainLooper())
        )

        try {
            Thread.sleep(150)
            val image = imageReader.acquireLatestImage() ?: throw IllegalStateException("Unable to acquire screen image")
            image.use {
                val plane = it.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * metrics.widthPixels
                val bitmap = Bitmap.createBitmap(
                    metrics.widthPixels + rowPadding / pixelStride,
                    metrics.heightPixels,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, metrics.widthPixels, metrics.heightPixels)
                val output = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.PNG, 100, output)
                bitmap.recycle()
                cropped.recycle()
                return output.toByteArray()
            }
        } finally {
            display.release()
            imageReader.close()
        }
    }
}
```

- [ ] **Step 2: Wire capture in service**

Modify `CaptureService.kt` so `captureCurrentScreen()` becomes:

```kotlin
private fun captureCurrentScreen() {
    val projection = mediaProjection
    if (projection == null) {
        Toast.makeText(this, "未获得截屏权限", Toast.LENGTH_SHORT).show()
        return
    }

    val config = configRepository.load()
    val current = stateRepository.load(config.prefix)
    val filename = namingService.buildFilename(current.prefix, current.caseIndex, current.shotIndex, config.caseDigits)
    val outputDir = fileStore.resolveOutputDir(this, config)

    try {
        if (config.hideFloatingWindowBeforeCapture) {
            overlayController?.hideForCapture()
            Thread.sleep(config.captureDelayMs)
        }
        val pngBytes = ScreenCaptureEngine(this).capturePng(projection)
        val target = fileStore.writePng(outputDir, filename, pngBytes)
        val next = StateRepository.afterCaptureSuccess(current)
        stateRepository.save(next, config.prefix)
        Toast.makeText(this, "已保存: ${target.name}", Toast.LENGTH_SHORT).show()
    } catch (error: Exception) {
        Toast.makeText(this, "截图失败，状态未推进: ${error.message}", Toast.LENGTH_LONG).show()
    } finally {
        if (config.hideFloatingWindowBeforeCapture) {
            overlayController?.restoreAfterCapture()
        }
    }
}
```

- [ ] **Step 3: Build compile check**

Run from `android/`: `.\gradlew.bat assembleDebug`

Expected: APK compiles.

- [ ] **Step 4: Manual Android verification**

Install debug APK on Android device and verify:

- App starts.
- Overlay permission flow works.
- Screen capture prompt appears.
- Floating `截图` saves a PNG.
- Floating `完成` advances state.

- [ ] **Step 5: Commit capture integration**

```bash
git add android/app/src/main/java/com/caseshot/ScreenCaptureEngine.kt android/app/src/main/java/com/caseshot/CaptureService.kt
git commit -m "feat: wire native screen capture saving"
```

## Task 8: Documentation And Migration Notes

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update README native section**

Add this section near the top of `README.md`:

```md
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
```

- [ ] **Step 2: Run available verification**

Run root Node tests:

```powershell
npm.cmd test
```

Run Android tests when Gradle is available:

```powershell
cd android
.\gradlew.bat testDebugUnitTest
```

Expected: Node tests pass. Android tests pass when Android build environment is configured.

- [ ] **Step 3: Commit README update**

```bash
git add README.md
git commit -m "docs: document native APK direction"
```

## Task 9: Final Verification

**Files:**
- Verify all Android project files and README.

- [ ] **Step 1: Run root tests**

Run: `npm.cmd test`

Expected: existing AutoX MVP pure tests pass, 12 tests passing.

- [ ] **Step 2: Run Android unit tests**

Run from `android/`: `.\gradlew.bat testDebugUnitTest`

Expected when Android build environment is configured: all Android unit tests pass.

- [ ] **Step 3: Build debug APK**

Run from `android/`: `.\gradlew.bat assembleDebug`

Expected when Android build environment is configured: debug APK at `android/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Check git status**

Run: `git status --short`

Expected: no uncommitted changes.

- [ ] **Step 5: Record device verification status**

If no Android device is available, report that device-level MediaProjection and overlay behavior still requires manual verification. Do not claim APK runtime behavior is fully verified without installing and testing on a device.

## Self-Review

Spec coverage:

- Kotlin native APK project is covered by Tasks 1 and 2.
- Floating overlay is covered by Task 6.
- MediaProjection foreground service is covered by Tasks 1, 6, and 7.
- Naming rules are covered by Task 2.
- State and config persistence are covered by Task 3.
- Local numbered image saving and conflict behavior are covered by Tasks 4 and 7.
- README migration and manual verification are covered by Task 8.
- SMB, OCR, index generation, ZIP export, remote upload, AI features, and system screenshot folder watching remain deferred.

Placeholder scan:

- The plan contains no placeholder markers or unspecified implementation steps.

Type consistency:

- Config fields match `CaseShotConfig`, `ConfigRepository`, the native design spec, and README update.
- State fields match `CaseShotState`, `StateRepository`, and naming flow.
- `CaptureService`, `OverlayController`, `ScreenCaptureEngine`, `FileStore`, and `NamingService` names match across tasks.

Known implementation risk:

- This plan creates Android Gradle project files, but local build verification depends on Android SDK/Gradle availability. If the current machine still lacks Android SDK or Gradle wrapper during execution, implementation should create files and report build verification as blocked by environment rather than claiming APK build success.
