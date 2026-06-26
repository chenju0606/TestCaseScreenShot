# CaseShot — 测试证迹助手

一款轻量级原生 Android APK，用于在手工测试过程中快速截取测试证迹（证据截图）。

最初以 AutoX.js 脚本原型开发，现已重构为原生 Kotlin Android 应用，以消除 AutoX.js / Auto.js 在许可和运行时依赖方面的长期不确定性。

APK 源码位于 `android/` 目录下。

## 功能特性

- **悬浮操作面板** — `截图` 和 `下一个` 两个按钮，长按可拖拽移动
- **自动截屏** — 基于 Android AccessibilityService (`takeScreenshot`，需 API 34+)
- **可配置命名规则** — 前缀、用例编号位数、起始用例编号
- **状态持久化** — 跨会话保持 `前缀 / 用例编号 / 截图编号`
- **文件冲突处理** — 检测到已存在的截图文件时，支持跳过 / 覆盖 / 询问三种模式
- **原子写入** — 先写入临时文件，再原子移动到目标文件，防止截图损坏
- **输出目录选择** — 通过 SAF（存储访问框架）选择自定义保存位置
- **通知反馈** — 截图保存、失败和用例切换均有通知提示

## 技术栈

| 层次 | 选择 |
|---|---|
| 语言 | Kotlin |
| 构建 | Gradle KTS (AGP 8.7.3, Kotlin 2.0.21) |
| 最低 / 目标 SDK | 26 / 35 |
| 截图方式 | `AccessibilityService.takeScreenshot()` (API 34+，无需 MediaProjection) |
| 悬浮窗口 | `TYPE_ACCESSIBILITY_OVERLAY` (无需 `SYSTEM_ALERT_WINDOW` 权限) |
| 持久化 | `SharedPreferences` |
| 测试 | JUnit 4 (命名规则、状态机、配置、文件存储的单元测试) |

## 项目结构

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/caseshot/
│   │   │   ├── MainActivity.kt           — 主配置界面
│   │   │   ├── CaptureAccessibilityService.kt — 前台截图服务
│   │   │   ├── OverlayController.kt       — 悬浮面板 (截图/完成/冲突弹窗)
│   │   │   ├── OverlayWindowFlags.kt      — 悬浮窗口标记常量
│   │   │   ├── NamingService.kt           — 文件名生成
│   │   │   ├── FileStore.kt               — 原子化 PNG 写入 + 冲突处理
│   │   │   ├── ConfigRepository.kt        — 配置持久化 + 规范化
│   │   │   ├── StateRepository.kt         — 状态机 (前缀/用例编号/截图编号)
│   │   │   └── Models.kt                  — CaseShotConfig, CaseShotState, 枚举
│   │   ├── res/                           — 布局、图标、颜色、字符串资源
│   │   └── AndroidManifest.xml
│   └── src/test/java/com/caseshot/        — 单元测试
├── build.gradle.kts                       — 根构建脚本
└── settings.gradle.kts                    — 项目设置
```

### 关键设计决策

- **AccessibilityService 而非 MediaProjection**：MVP 版本使用 Android 14 引入的 `AccessibilityService.takeScreenshot()`，而非最初设计的 `MediaProjection` 方案。这避免了复杂的 MediaProjection 权限申请流程和前台服务类型声明，同时仍能在 Android 14+ 设备上提供可靠的截图能力。
- **`TYPE_ACCESSIBILITY_OVERLAY` 而非 `TYPE_APPLICATION_OVERLAY`**：由于截图服务是 AccessibilityService，悬浮面板使用 `TYPE_ACCESSIBILITY_OVERLAY`，限制更少且无需 `SYSTEM_ALERT_WINDOW` 权限。
- **原子化文件写入**：截图先写入 `.tmp` 临时文件，再原子移动到目标文件名，防止意外中断导致文件损坏。

## 构建

在 Android Studio 中打开 `android/` 目录并同步 Gradle，或通过命令行构建：

```powershell
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

调试版 APK 输出位置：`android/app/build/outputs/apk/debug/app-debug.apk`

## Android 权限

APK 需要以下权限：

| 权限 | 用途 |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | 通过无障碍服务截取屏幕，用户需在系统设置中手动开启 |
| `POST_NOTIFICATIONS` | 显示截图结果通知 (Android 13+) |
| `FOREGROUND_SERVICE` | 保持截图服务在后台运行 |

注：**无需** `SYSTEM_ALERT_WINDOW`（悬浮窗）权限，因为悬浮面板使用 `TYPE_ACCESSIBILITY_OVERLAY`。

## 手动验证流程

1. **首次启动** — 显示当前目标文件名预览
2. **打开无障碍设置** — 点击按钮跳转系统无障碍设置；启用"测试证迹助手服务"
3. **授予通知权限** — Android 13+ 需要以接收截图结果通知
4. **保存配置** — 持久化前缀、用例位数、起始编号和输出目录
5. **悬浮 `截图`** — 截取并保存 `001.png`
6. **连续 `截图`** — 依次保存 `001-2.png`、`001-3.png`
7. **悬浮 `完成`** — 切换到下一个用例：`002.png`
8. **前缀 `049`** — 生成 `049-001.png`，再生成 `049-001-2.png`
9. **文件已存在** — 不覆盖，弹出冲突面板，提供跳过/覆盖选择
