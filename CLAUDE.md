# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

BatteryRecorder 是一个 Android 电池功耗记录应用，通过 Shizuku/root 权限运行独立服务进程，实时采集电池功耗数据并按充电/放电状态分段存储。

## 构建命令

```bash
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK
./gradlew :server:assembleDebug  # 仅编译 server 模块（含 JNI）
```

## 技术栈

- Kotlin 2.3 + Jetpack Compose
- Android SDK 31-36 (Android 12+)
- NDK 29 + CMake 3.22.1 (JNI 读取功耗文件)
- AIDL 跨进程通信

## 架构

### 模块结构

```
:app                    # 主应用 (Compose UI)
:server                 # 独立服务进程 (以 shell/root 身份运行)
:hiddenapi:stub         # Android 隐藏 API 存根 (编译时依赖)
:hiddenapi:compat       # 隐藏 API 兼容层
```

### 核心通信机制

Server 进程通过 `ContentProvider.call()` 向 App 传递 Binder：
1. Server 监听前台应用变化 (`ITaskStackListener`)
2. 当 App 进入前台时，Server 通过 `IActivityManager.getContentProviderExternal()` 获取 `BinderProvider`
3. 调用 `provider.call("setBinder", ...)` 传递 `IService` Binder
4. App 通过 `Service.kt` 单例持有 Binder 引用

### 数据采集流程

```
Server.kt (主循环)
  ↓ 定时采集 (mIntervalMillis)
PowerUtil.kt → power_util.c (JNI)
  ↓ 读取 /sys/class/power_supply/battery/*
DataWriter.kt
  ↓ 按充电/放电分段写入
/data/user/0/yangfentuozi.batteryrecorder/power_data/{charge,discharge}/*.txt
```

### UI 层结构 (app 模块)

```
ui/
├── navigation/          # NavRoute, BatteryRecorderNavHost
├── screens/
│   ├── home/           # HomeScreen + 卡片组件
│   ├── history/        # HistoryListScreen, RecordDetailScreen
│   └── settings/       # SettingsScreen + dialogs/sections
├── viewmodel/          # MainViewModel, SettingsViewModel, HistoryViewModel
├── components/         # 通用组件 (charts, global)
└── theme/              # Theme, Type, AppShape
```

### 关键类

| 类 | 职责 |
|---|---|
| `server/Server.kt` | 服务主入口，注册系统回调，协调数据采集 |
| `server/DataWriter.kt` | 数据分段写入，缓冲批量刷盘 |
| `server/PowerUtil.kt` | JNI 桥接，读取电池状态 |
| `app/Service.kt` | App 端 Binder 持有者 |
| `app/provider/BinderProvider.kt` | 接收 Server 传递的 Binder |
| `app/util/HistoryRepository.kt` | 历史记录数据读取 |

## 数据存储

路径：`/data/user/0/yangfentuozi.batteryrecorder/power_data/`
- `charge/{timestamp}.txt` - 充电记录
- `discharge/{timestamp}.txt` - 放电记录

记录格式（每行）：`timestamp,power,packageName,capacity,isDisplayOn`

分段规则：
- 充电：超过 24h 或中断 >30s 开新段，<1min 的段丢弃
- 放电：超过 24h 或中断 >10min 开新段，<15min 的段丢弃

## 配置

Server 读取 App 的 SharedPreferences：
`/data/user/0/yangfentuozi.batteryrecorder/shared_prefs/yangfentuozi.batteryrecorder_preferences.xml`

配置项：`interval`, `batch_size`, `flush_interval`, `record_screen_off`

## AIDL 接口

`IService.aidl`：
- `refreshConfig()` - 重新加载配置
- `stopService()` - 停止服务
- `writeToDatabaseImmediately()` - 立即刷盘

## 权限要求

- Server 进程需以 `shell` 或 `root` 身份运行（通过 Shizuku）
- JNI 需读取 `/sys/class/power_supply/battery/*` 系统文件
