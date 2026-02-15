# Repository Guidelines

## 项目概述
BatteryRecorder 是一款 Android 电池功耗记录应用，依赖 Shizuku/root 权限运行独立服务进程，实时采集功耗数据并按充电/放电状态分段存储。

## 项目结构与模块组织
- `app/` 主应用（Compose UI），源码在 `app/src/main`。
- `server/` 独立服务进程，AIDL 在 `server/src/main/aidl`，JNI 在 `server/src/main/jni`。
- `hiddenapi/stub` 与 `hiddenapi/compat` 提供隐藏 API 存根与兼容层。
- `shared/` 共享常量与配置。
- 关键 UI 目录：`app/src/main/java/.../ui/{navigation,screens,viewmodel,components,theme}`。

## 构建与开发命令
- `./gradlew assembleDebug` 生成 Debug APK。
- `./gradlew assembleRelease` 生成 Release APK。
- `./gradlew :server:assembleDebug` 仅编译 server 模块（含 JNI）。

## 核心流程与数据存储
- Server 通过 `ContentProvider.call()` 将 `IService` Binder 传给 App；App 在 `Service.kt` 持有引用。
- 功耗读取：`PowerUtil.kt` → `power_util.c`，读取 `/sys/class/power_supply/battery/*`。
- 数据落盘：`server/DataWriter.kt` 按充/放电分段写入  
  `/data/user/0/yangfentuozi.batteryrecorder/power_data/{charge,discharge}/*.txt`。  
  记录格式：`timestamp,power,packageName,capacity,isDisplayOn`。

## 配置与权限
- Server 读取 `SharedPreferences`：  
  `/data/user/0/yangfentuozi.batteryrecorder/shared_prefs/yangfentuozi.batteryrecorder_preferences.xml`。  
  关键配置：`interval`、`batch_size`、`flush_interval`、`record_screen_off`。
- 需 shell/root 权限（Shizuku），JNI 需要读取系统电池文件。

## 编码风格与命名
- Kotlin/Compose 4 空格缩进，公共方法使用 KDoc。
- 注释只说明“为什么”，避免复述代码。
- 优先命名常量，减少嵌套（早返回、`?.let`、`?.takeIf`）。
- 提交信息采用 Conventional Commits（如 `feat(server)`、`fix`、`refactor`）。

## 测试与 PR 约定
- 当前仓库未发现 `app/src/test` 或 `app/src/androidTest` 测试用例。
- 新增测试时注明运行命令（如 `./gradlew test`）。
- PR 需包含变更摘要、测试说明，UI 改动附截图；涉及 AIDL/JNI 或权限依赖请明确说明。
