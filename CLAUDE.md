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

### 功率显示链路

- 原始功率值单位为纳瓦 (nW)，统一通过 `FormatUtil.computePowerW()` 转换为瓦特
- 放电记录显示正值的逻辑通过 `PowerDisplayMapper` 在 ViewModel 层统一处理，UI 层不做正负转换
- `HistoryViewModel` 提供预转换的 `displayPoints`（通过 `RecordDetailChartUiState`），Screen 层直接使用

## App 模块包结构

```
app/src/main/java/yangfentuozi/batteryrecorder/
├── ui/
│   ├── BatteryRecorderApp.kt          # 应用入口 Composable，创建 ViewModel 和 NavHost
│   ├── MainActivity.kt                # Activity 壳
│   ├── BaseActivity.kt                # Activity 基类（边到边布局）
│   ├── navigation/
│   │   ├── NavRoute.kt                # 导航路由定义（sealed class NavRoute）
│   │   └── BatteryRecorderNavHost.kt  # NavHost + 页面切换动画
│   ├── screens/
│   │   ├── home/HomeScreen.kt         # 首页
│   │   ├── settings/SettingsScreen.kt # 设置页
│   │   └── history/
│   │       ├── HistoryListScreen.kt   # 历史记录列表
│   │       └── RecordDetailScreen.kt  # 记录详情（图表+统计）
│   ├── viewmodel/
│   │   ├── MainViewModel.kt           # 首页状态：服务连接、摘要统计、同步
│   │   ├── SettingsViewModel.kt       # 设置读写，统一在 BatteryRecorderApp 初始化
│   │   ├── LiveRecordViewModel.kt     # 实时功率数据（IRecordListener 回调）
│   │   ├── HistoryViewModel.kt        # 历史记录列表 + 详情图表数据转换
│   │   └── PowerDisplayMapper.kt      # 放电显示正值映射工具函数（internal）
│   ├── components/
│   │   ├── global/
│   │   │   ├── SplicedColumnGroup.kt  # 拼接列分组（自动圆角 DSL）
│   │   │   ├── SplicedShapes.kt       # 圆角计算辅助
│   │   │   ├── SwipeRevealRow.kt      # 左滑显示操作按钮
│   │   │   ├── SwitchWidget.kt        # M3 开关组件
│   │   │   └── StatRow.kt             # 统计行组件（label + value）
│   │   ├── home/
│   │   │   ├── BatteryRecorderTopAppBar.kt
│   │   │   ├── StartServerCard.kt     # 启动服务引导卡片
│   │   │   ├── CurrentRecordCard.kt   # 当前记录卡片 + 实时功率迷你图
│   │   │   ├── PredictionCard.kt      # 续航预测卡片 + 场景统计卡片
│   │   │   └── StatsCard.kt           # 充电/放电统计摘要卡片（通用）
│   │   ├── charts/
│   │   │   └── PowerCapacityChart.kt  # 功率/电量/温度图表（Canvas 绘制）
│   │   └── settings/
│   │       ├── SettingsItem.kt
│   │       └── sections/
│   │           ├── CalibrationSection.kt  # 校准设置组（参数传入 serviceConnected）
│   │           ├── GameListSection.kt     # 游戏/高负载 App 选择器（自动检测 + blacklist）
│   │           └── ServerSection.kt       # 服务端设置组
│   ├── dialog/
│   │   ├── home/AboutDialog.kt
│   │   └── settings/
│   │       ├── CalibrationDialog.kt
│   │       ├── RecordIntervalDialog.kt
│   │       ├── WriteLatencyDialog.kt
│   │       ├── BatchSizeDialog.kt
│   │       └── SegmentDurationDialog.kt
│   └── theme/
│       ├── Theme.kt
│       ├── Type.kt
│       └── AppShape.kt
├── data/
│   ├── model/ChartPoint.kt            # 图表数据点
│   └── history/
│       ├── HistoryRepository.kt        # 历史记录仓库（文件 I/O，纯数据，无 UI 逻辑）
│       ├── SceneStatsComputer.kt       # 场景统计计算（息屏/亮屏日常/游戏分类积分 + 缓存）
│       ├── BatteryPredictor.kt         # 续航预测（能量比例法：k = ΔSOC / E_total）
│       ├── StatisticsUtil.kt           # 功率统计计算 + 缓存
│       └── SyncUtil.kt                 # 数据同步（PfdFileReceiver 封装）
├── ipc/
│   ├── Service.kt                      # IPC Binder 持有 + ServiceConnection 回调
│   ├── BinderProvider.kt               # ContentProvider 接收 Server 推送的 Binder
│   └── ConfigProvider.kt               # ContentProvider 供 Server(shell) 读取配置
└── utils/
    └── FormatUtil.kt                   # 格式化工具（时间、功率转换 computePowerW、formatPower）
```

## 关键路径索引

| 功能 | 路径 |
|---|---|
| AIDL 接口 | `server/src/main/aidl/` (`IService.aidl`, `IRecordListener.aidl`) |
| JNI 原生代码 | `server/src/main/jni/power_reader.c` |
| Server 入口 | `server/.../Server.kt` |
| IPC 绑定 | `app/.../ipc/BinderProvider.kt`, `app/.../ipc/Service.kt` |
| 配置系统 | `shared/.../config/Config.kt`, `ConfigConstants.kt`, `ConfigUtil.kt` |
| App 入口 Composable | `app/.../ui/BatteryRecorderApp.kt` |
| 导航路由 | `app/.../ui/navigation/NavRoute.kt` (Home, Settings, HistoryList, RecordDetail) |
| ViewModel | `app/.../ui/viewmodel/` (MainViewModel, SettingsViewModel, LiveRecordViewModel, HistoryViewModel) |
| 功率显示映射 | `app/.../ui/viewmodel/PowerDisplayMapper.kt` |
| 功率转换/格式化 | `app/.../utils/FormatUtil.kt` (`computePowerW`, `formatPower`, `formatRelativeTime`, `formatRemainingTime`, `formatDurationHours`) |
| 数据同步 | `shared/.../sync/PfdFileSender.kt`, `PfdFileReceiver.kt` |
| 统计计算 | `app/.../data/history/StatisticsUtil.kt` |
| 场景统计 | `app/.../data/history/SceneStatsComputer.kt`（SceneStats 数据类 + compute()） |
| 续航预测 | `app/.../data/history/BatteryPredictor.kt`（PredictionResult 数据类 + predict()） |
| 历史记录仓库 | `app/.../data/history/HistoryRepository.kt` |
| 图表组件 | `app/.../ui/components/charts/PowerCapacityChart.kt` |
| 公共 UI 组件 | `app/.../ui/components/global/` (SplicedColumnGroup, StatRow, SwipeRevealRow, SwitchWidget) |

## 架构约定

- **功率转换**：所有原始功率值 → 瓦特的转换必须通过 `FormatUtil.computePowerW()`，禁止各处重复写 `cellMultiplier * calibrationValue * (power / 1e9)`
- **放电显示正值**：在 ViewModel 层通过 `PowerDisplayMapper` 函数处理，UI 组件不关心正负转换逻辑
- **SettingsViewModel 初始化**：统一在 `BatteryRecorderApp` 中通过 `LaunchedEffect` 调用 `init(context)`，各 Screen 不再重复调用
- **UI 组件解耦 IPC**：UI 组件不直接引用 `Service.service`，通过参数传入 `serviceConnected: Boolean`
- **HistoryRepository**：纯数据仓库，只做文件 I/O 和统计计算，不包含 UI 显示逻辑（如正负值转换）
- **ViewModel 创建**：`MainViewModel` 和 `SettingsViewModel` 在 `BatteryRecorderApp` 创建后通过参数传递；`HistoryViewModel` 和 `LiveRecordViewModel` 在各 Screen 内局部创建

### 续航预测

- **预测算法**：能量比例法，`k = ΔSOC_total / E_total`，`drain_scene = k × P_scene`，不依赖电池容量 Wh
- **场景分类**：息屏（displayOn=0）、亮屏日常（displayOn=1 且非游戏）、游戏（displayOn=1 且在游戏列表）；三场景均参与 E_total 和 ΔSOC_total 统计，保证 k 口径一致
- **游戏检测规则**（`GameListSection.isGameApp()`）：FLAG_IS_GAME / CATEGORY_GAME、游戏引擎 so（Unity/UE/Cocos）、启动 Activity 横屏方向
- **游戏 Blacklist**：预设 `PRESET_BLACKLIST`（硬编码误判包名）+ 用户 `gameBlacklist`（取消勾选的自动检测游戏），自动检测时排除两者
- **数据范围**：最近 20 个放电文件，最少 3 个文件才出预测
- **k 异常值校验**：反推整体掉电速率超过 50%/h 时判定数据异常

## 编码约定

- 所有注释、提交信息、文档使用简体中文
- 代码标识符遵循现有命名约定（英文）
- 新增依赖必须添加到 `gradle/libs.versions.toml` Version Catalog
- AIDL 文件变更需同时检查 `:server` 和 `:shared` 模块
- `hiddenapi:stub` 模块中的类只做声明（compileOnly），不包含实现
- 时间格式化统一使用 `FormatUtil` 中的函数，禁止在 Screen/Chart 中定义局部格式化函数
- 公共 UI 组件放在 `ui/components/global/`，业务特定组件放在对应的 `ui/components/{feature}/`
