# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

BatteryRecorder — 一个 Android 电池功率记录 App，通过独立 Server 进程（root/shell 权限）以 JNI 读取 sysfs 电池数据，以低 CPU 开销记录精确功率，并提供续航预测。

## 构建命令

不要自主构建，告诉用户写完了，让用户测试

- 签名配置从根目录 `signing.properties` 读取，缺失时自动 fallback 到 debug keystore
- 产物路径：`app/build/outputs/apk/{release,debug}/batteryrecorder-v*-{variant}.apk`
- versionCode 由 git commit 数量动态生成，需要完整 git history（`fetch-depth: 0`）

## 技术栈

| 项 | 值 |
|---|---|
| 语言 | Kotlin (主), Java (hiddenapi), C (JNI) |
| UI | Jetpack Compose + Material 3，无 XML Layout |
| 架构 | MVVM (ViewModel + StateFlow + Compose) |
| 构建 | Gradle 8.13 Kotlin DSL, AGP 8.13.2, Kotlin 2.3.0 |
| JDK | 21 (app/server/shared), 11 (hiddenapi) |
| NDK | 29.0.14206865, CMake 3.22.1 |
| 最低 SDK | 31 (Android 12) |
| 依赖管理 | Version Catalog (`gradle/libs.versions.toml`) |

## 模块结构与依赖

```
:app                 → 主应用，UI + IPC 客户端
:server              → 独立 Server 进程，JNI 电池数据采集 + 文件写入
:shared              → 公共数据类、配置、同步协议（Parcelize）
:hiddenapi:stub      → Android Hidden API 的 stub 声明（compileOnly）
:hiddenapi:compat    → Hidden API 兼容性封装
```

依赖方向：`app → shared, server`、`server → shared, hiddenapi:compat`、`shared → hiddenapi:compat`

## 核心架构

### 双进程 IPC 模型

```
App 进程 (UI)  ←─ AIDL Binder ─→  Server 进程 (root/shell)
```

- **Server** 不是 Android Service，而是直接启动 `Looper.loop()` 的独立进程
- Server 启动后通过 `ActivityManagerCompat.contentProviderCall()` 将 Binder 推送给 App 的 `BinderProvider`（ContentProvider）
- App 通过 `IService` AIDL 接口调用 Server（停止、注册监听、更新配置、同步数据）
- Server 读取 App 配置：root 下直接读 SharedPreferences XML，shell 下通过 `ConfigProvider`（ContentProvider）

### 数据采集链路

1. `power_reader.c` — 缓存打开 `/sys/class/power_supply/battery/` 下 5 个 sysfs 文件（voltage_now, current_now, capacity, status, temp），通过 JNI 返回
2. `Monitor.kt` — 按可配置间隔循环采集，监听 TaskStack 和 Display 事件
3. `PowerRecordWriter.kt` — 分充电/放电两路写入 CSV，延迟批量 flush，按时间分段
4. 数据格式：`timestamp,power,packageName,capacity,isDisplayOn,temp`

### 数据同步

Server 以 shell 权限运行时数据写入 `com.android.shell` 数据目录。App 通过 `sync()` AIDL 调用获取 ParcelFileDescriptor 管道，使用自定义二进制协议（`PfdFileSender`/`PfdFileReceiver`）传输文件。

## 关键路径索引

| 功能 | 路径 |
|---|---|
| AIDL 接口 | `server/src/main/aidl/` (`IService.aidl`, `IRecordListener.aidl`) |
| JNI 原生代码 | `server/src/main/jni/power_reader.c` |
| Server 入口 | `server/.../Server.kt` |
| IPC 绑定 | `app/.../ipc/BinderProvider.kt`, `app/.../ipc/Service.kt` |
| 配置系统 | `shared/.../config/Config.kt`, `ConfigConstants.kt`, `ConfigUtil.kt` |
| 导航路由 | `app/.../ui/navigation/NavRoute.kt` (Home, Settings, HistoryList, RecordDetail) |
| ViewModel | `app/.../ui/viewmodel/` (MainViewModel, SettingsViewModel, LiveRecordViewModel, HistoryViewModel) |
| 数据同步 | `shared/.../sync/PfdFileSender.kt`, `PfdFileReceiver.kt` |
| 统计计算 | `app/.../data/history/StatisticsUtil.kt` |

## 编码约定

- 所有注释、提交信息、文档使用简体中文
- 代码标识符遵循现有命名约定（英文）
- 新增依赖必须添加到 `gradle/libs.versions.toml` Version Catalog
- AIDL 文件变更需同时检查 `:server` 和 `:shared` 模块
- `hiddenapi:stub` 模块中的类只做声明（compileOnly），不包含实现
