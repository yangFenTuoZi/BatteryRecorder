# CLAUDE.md

本文件用于指导 Claude Code 在本仓库中进行代码检索、修改与交付。

## 项目概述

BatteryRecorder — 一个 Android 电池功率记录 App，通过独立 Server 进程（root/shell 权限）以 JNI 读取 sysfs 电池数据，以低 CPU 开销记录精确功率，并提供续航预测。

## 构建命令

不要自主构建，告诉用户写完了，让用户测试

- 签名配置从根目录 `signing.properties` 读取，缺失时自动 fallback 到 debug keystore
- 产物路径：`app/build/outputs/apk/{release,debug}/batteryrecorder-v*-{variant}.apk`
- Release 混淆产物会额外复制到根目录 `out/apk/`，mapping 会复制到 `out/mapping/`
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
| SDK | minSdk 31, targetSdk/compileSdk 36 |
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

- 原始功率值为记录文件中的原始数值（raw，平台单位可能不一致），统一通过 `FormatUtil.computePowerW()` 做缩放与校准后转换为瓦特用于展示
- 放电记录显示正值的逻辑通过 `PowerDisplayMapper` 在 ViewModel 层统一处理，UI 层不做正负转换
- `PredictionDetailViewModel` 仅暴露原始应用功率统计；`PredictionDetailScreen` 基于 `SettingsViewModel.dischargeDisplayPositive` 做最终正负值展示，避免配置源分叉
- `HistoryViewModel` 统一产出 `RecordDetailChartUiState`，其中：
  - `points` 为详情页原始展示点
  - `trendPoints` 为基于过滤后原始点重新分桶得到的趋势点
  - `minChartTime` / `maxChartTime` / `maxViewportStartTime` / `viewportDurationMs` 承载全屏视口元数据
- `RecordDetailScreen` 直接消费 `recordChartUiState`，不再维护独立的图表数据拼装逻辑

### 图表曲线模式

- **原始模式（Raw）**：显示逐点采样的折线，保留瞬时尖刺，`rawPowerW` 字段
- **趋势模式（Fitted）**：显示时间分桶后的中位数平滑曲线（三次贝塞尔），用于观察低频走势，`fittedPowerW` 字段
- **隐藏模式（Hidden）**：不绘制功率曲线，但保留选中逻辑
- 数据模型 `RecordDetailChartPoint` 同时承载两类点，图表层通过 `PowerCurveMode` 切换显示
- 孤立息屏点过滤：关闭息屏记录显示时，前后均为亮屏的单个息屏点会被视为抖动并过滤
- 固定功率轴：详情页根据记录类型和“放电显示正值”配置切换 `FixedPowerAxisMode.PositiveOnly / NegativeOnly`
- 全屏模式：详情页进入全屏后切横屏，仅展示总时长的 25% 作为初始视口，支持局部浏览
- 图表交互：支持单击选点、拖动选点、双指平移视口、功率曲线 `Raw -> Fitted -> Hidden` 循环切换，以及电量/温度曲线显隐切换
- 图表偏好：`RecordDetailScreen` 通过 `record_detail_chart` SharedPreferences 单独持久化图表展示偏好，不写入业务配置

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
│   │   ├── prediction/
│   │   │   └── PredictionDetailScreen.kt # 应用维度预测详情页
│   │   ├── settings/SettingsScreen.kt # 设置页
│   │   └── history/
│   │       ├── HistoryListScreen.kt   # 历史记录列表
│   │       └── RecordDetailScreen.kt  # 记录详情（图表+统计）
│   ├── viewmodel/
│   │   ├── MainViewModel.kt           # 首页状态：服务连接、摘要统计、同步
│   │   ├── SettingsViewModel.kt       # 设置读写，统一在 BatteryRecorderApp 初始化
│   │   ├── LiveRecordViewModel.kt     # 实时功率数据（IRecordListener 回调）
│   │   ├── HistoryViewModel.kt        # 历史记录列表 + 详情图表数据转换
│   │   ├── PredictionDetailViewModel.kt # 应用预测详情页状态
│   │   └── PowerDisplayMapper.kt      # 放电显示正值映射工具函数（internal）
│   ├── model/
│   │   ├── SettingsUiState.kt         # 设置页 UI 状态
│   │   ├── SettingsUiProps.kt         # 设置页 UI 属性
│   │   └── SettingsActions.kt         # 设置页操作接口
│   ├── components/
│   │   ├── global/
│   │   │   ├── SplicedColumnGroup.kt  # 拼接列分组（自动圆角 DSL）
│   │   │   ├── LazySplicedColumnGroup.kt # LazyColumn 版拼接列分组
│   │   │   ├── SplicedShapes.kt       # 圆角计算辅助
│   │   │   ├── SwipeRevealRow.kt      # 左滑显示操作按钮
│   │   │   ├── SwitchWidget.kt        # M3 开关组件
│   │   │   ├── StatRow.kt             # 统计行组件（label + value）
│   │   │   └── MarkdownText.kt        # Markdown 渲染组件
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
│   │           ├── CalibrationSection.kt    # 校准设置组
│   │           ├── PredictionSection.kt     # 预测设置组
│   │           ├── PredictionGameFilter.kt  # 游戏过滤器（自动检测 + blacklist）
│   │           ├── ServerSection.kt         # 服务端设置组
│   │           └── AppSection.kt            # 应用设置组
│   ├── dialog/
│   │   ├── home/
│   │   │   ├── AboutDialog.kt
│   │   │   ├── AdbGuideDialog.kt      # ADB 启动引导对话框
│   │   │   └── UpdateDialog.kt        # 更新提示对话框
│   │   └── settings/
│   │       ├── CalibrationDialog.kt
│   │       ├── RecordIntervalDialog.kt
│   │       ├── WriteLatencyDialog.kt
│   │       ├── BatchSizeDialog.kt
│   │       ├── SegmentDurationDialog.kt
│   │       ├── CurrentSessionWeightDialog.kt        # 当次加权设置对话框
│   │       └── SceneStatsRecentFileCountDialog.kt   # 场景统计文件数对话框
│   └── theme/
│       ├── Theme.kt
│       ├── Type.kt
│       └── AppShape.kt
├── data/
│   ├── model/
│   │   ├── ChartPoint.kt                  # 图表数据点（通用）
│   │   └── RecordDetailChartPoint.kt      # 记录详情图表数据点（原始/趋势共用）
│   └── history/
│       ├── HistoryRepository.kt        # 历史记录仓库（文件 I/O，纯数据，无 UI 逻辑）
│       ├── AppStatsComputer.kt         # 应用前台耗电统计（供预测详情页使用）
│       ├── DischargeRecordScanner.kt   # 放电文件扫描与时间衰减加权复用工具
│       ├── SceneStatsComputer.kt       # 场景统计计算（息屏/亮屏日常/游戏分类积分 + 缓存）
│       ├── BatteryPredictor.kt         # 续航预测（能量比例法：k = ΔSOC / E_total）
│       ├── StatisticsRequest.kt        # 统计请求数据类
│       └── SyncUtil.kt                 # 数据同步（PfdFileReceiver 封装）
├── startup/
│   ├── BootCompletedReceiver.kt        # 开机广播接收器
│   ├── RootServerStarter.kt            # Root 服务启动器
│   └── BootAutoStartNotification.kt    # 开机自启动通知
├── ipc/
│   ├── Service.kt                      # IPC Binder 持有 + ServiceConnection 回调
│   ├── BinderProvider.kt               # ContentProvider 接收 Server 推送的 Binder
│   └── ConfigProvider.kt               # ContentProvider 供 Server(shell) 读取配置
└── utils/
    ├── FormatUtil.kt                   # 格式化工具（时间、功率转换 computePowerW、formatPower）
    └── UpdateUtil.kt                   # 更新检查工具
```

## 关键路径索引

| 功能 | 路径 |
|---|---|
| AIDL 接口 | `server/src/main/aidl/` (`IService.aidl`, `IRecordListener.aidl`) |
| JNI 原生代码 | `server/src/main/jni/power_reader.c` |
| Server 入口 | `server/.../Server.kt` |
| IPC 绑定 | `app/.../ipc/BinderProvider.kt`, `app/.../ipc/Service.kt` |
| 配置系统 | `shared/.../config/Config.kt`, `ConfigConstants.kt`, `ConfigUtil.kt` |
| 开机自启动 | `app/.../startup/BootCompletedReceiver.kt`, `RootServerStarter.kt`, `BootAutoStartNotification.kt` |
| App 入口 Composable | `app/.../ui/BatteryRecorderApp.kt` |
| 导航路由 | `app/.../ui/navigation/NavRoute.kt` (Home, Settings, PredictionDetail, HistoryList, RecordDetail) |
| ViewModel | `app/.../ui/viewmodel/` (MainViewModel, SettingsViewModel, LiveRecordViewModel, HistoryViewModel, PredictionDetailViewModel) |
| 预测详情页 | `app/.../ui/screens/prediction/PredictionDetailScreen.kt`, `app/.../ui/viewmodel/PredictionDetailViewModel.kt` |
| 详情图表状态 | `app/.../ui/viewmodel/HistoryViewModel.kt` (`RecordDetailChartUiState`) |
| 设置页 UI 模型 | `app/.../ui/model/` (SettingsUiState, SettingsUiProps, SettingsActions) |
| 图表数据模型 | `app/.../data/model/ChartPoint.kt`, `RecordDetailChartPoint.kt` |
| 功率显示映射 | `app/.../ui/viewmodel/PowerDisplayMapper.kt` |
| 功率转换/格式化 | `app/.../utils/FormatUtil.kt` (`computePowerW`, `formatPower`, `formatRelativeTime`, `formatRemainingTime`, `formatDurationHours`) |
| 数据同步 | `shared/.../sync/PfdFileSender.kt`, `PfdFileReceiver.kt` |
| 统计请求 | `app/.../data/history/StatisticsRequest.kt` |
| 场景统计 | `app/.../data/history/SceneStatsComputer.kt`（SceneStats 数据类 + compute()） |
| 应用统计 | `app/.../data/history/AppStatsComputer.kt`, `DischargeRecordScanner.kt` |
| 续航预测 | `app/.../data/history/BatteryPredictor.kt`（PredictionResult 数据类 + predict()） |
| 历史记录仓库 | `app/.../data/history/HistoryRepository.kt` |
| 图表组件 | `app/.../ui/components/charts/PowerCapacityChart.kt` |
| 公共 UI 组件 | `app/.../ui/components/global/` (SplicedColumnGroup, StatRow, SwipeRevealRow, SwitchWidget, MarkdownText) |
| 更新检查 | `app/.../utils/UpdateUtil.kt`, `app/.../ui/dialog/home/UpdateDialog.kt` |

## 架构约定

- **功率转换**：所有原始功率值 → 瓦特的转换必须通过 `FormatUtil.computePowerW()`，禁止各处重复写 `cellMultiplier * calibrationValue * (power / 1e9)`
- **放电显示正值**：在 ViewModel 层通过 `PowerDisplayMapper` 函数处理，UI 组件不关心正负转换逻辑
- **应用预测详情页功率显示**：`PredictionDetailViewModel` 暴露原始 `averagePowerRaw`，由 `PredictionDetailScreen` 结合 `SettingsViewModel.dischargeDisplayPositive` 做展示映射
- **SettingsViewModel 初始化**：统一在 `BatteryRecorderApp` 中通过 `LaunchedEffect` 调用 `init(context)`，各 Screen 不再重复调用
- **UI 组件解耦 IPC**：UI 组件不直接引用 `Service.service`，通过参数传入 `serviceConnected: Boolean`
- **HistoryRepository**：纯数据仓库，只做文件 I/O 和统计计算，不包含 UI 显示逻辑（如正负值转换）
- **ViewModel 创建**：`MainViewModel` 和 `SettingsViewModel` 在 `BatteryRecorderApp` 创建后通过参数传递；`HistoryViewModel` 和 `LiveRecordViewModel` 在各 Screen 内局部创建
- **记录详情图表状态**：详情页统一由 `RecordDetailChartUiState` 承载原始点、趋势点和视口元数据，Screen 层不再自行派生图表数据
- **趋势曲线生成**：趋势点必须基于过滤后的展示点按时间分桶后取中位数生成，绘制平滑样式属于图表层职责，不在仓库层做“平滑后数据落地”
- **图表本地偏好**：`PowerCurveMode`、电量/温度曲线显隐属于详情页本地展示偏好，持久化到独立 SharedPreferences，不进入 `SettingsViewModel`
- **启动日志与启动来源**：ROOT 启动统一经 `RootServerStarter.start(context, source)`，来源使用 `RootServerStarter.Source` 常量，日志保持统一语义

### 续航预测

- **预测算法**：能量比例法，`k = ΔSOC_total / E_total`，`drain_scene = k × P_scene`，不依赖电池容量 Wh
- **场景分类**：息屏（displayOn=0）、亮屏日常（displayOn=1 且非游戏）、游戏（displayOn=1 且在游戏列表）；三场景均参与 E_total 和 ΔSOC_total 统计，保证 k 口径一致
- **当次记录加权（时间衰减）**：在不改变"典型息屏/典型日常"两条预测语义的前提下，仅对**当前放电文件**引入指数衰减权重，强化"最近行为"对预测的影响；其余历史文件固定 `w=1`
  - **当前放电文件定义**：以 `Service.currRecordsFile` 对应的 `HistoryRecord` 为准，且必须 `type == Discharging`；未命中则不启用当次加权
  - **加权对象**：只对当前放电文件内每个采样区间（`dt`）同时加权 `energyRawMs`、`dt` 与 `capDelta`（`ΔSOC`），保证 `k` 与 `P_scene` 口径一致
  - **门槛**：当前放电文件需满足"记录时长 ≥ 10 分钟 且 掉电 ≥ 1%"才启用，避免短样本噪声导致预测抖动
  - **权重函数**：`w = 1 + (max - 1) * exp(-ln(2) * age / halfLife)`，其中 `age` 为区间时间点到当次文件末尾 `endTs` 的时间差（越新权重越高）
  - **endTs 来源**：`endTs` 来自"当次文件末尾的最后一条有效记录 timestamp"（通过读取文件尾部并解析最后一行），不使用 `System.currentTimeMillis()`，保证统计可复现
  - **相关设置**：`ConfigConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_ENABLED`、`ConfigConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_MAX_X100`（倍率 x100）、`ConfigConstants.KEY_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN`（分钟）
- **口径字段**：`SceneStats` 同时保留"原始时长（记录时长，用于展示/门槛判断）"与"加权时长（用于计算加权平均功耗）"；并保留 `rawTotalSocDrop` 用于整体异常校验，避免加权放大导致误判
- **缓存兼容**：`SceneStatsComputer` 的 `cacheKey` 包含 `CACHE_VERSION` 与加权参数；`SceneStats.fromString()` 需兼容旧格式缓存（字段增量时不致解析失败）
- **历史统计缓存版本**：`AppStatsComputer` 与 `SceneStatsComputer` 共用 `HistoryCacheVersions.HISTORY_STATS_CACHE_VERSION`；任一历史统计缓存格式或 key 组成变化时统一提升该版本
- **应用预测缓存**：`AppStatsComputer` 的 `cacheKey` 需包含文件名、`lastModified`、`length` 与加权参数
- **游戏检测规则**（`PredictionGameFilter.isGameApp()`）：FLAG_IS_GAME / CATEGORY_GAME、游戏引擎 so（Unity/UE/Cocos）、启动 Activity 横屏方向
- **游戏 Blacklist**：预设 `PRESET_BLACKLIST`（硬编码误判包名）+ 用户 `gameBlacklist`（取消勾选的自动检测游戏），自动检测时排除两者
- **数据范围**：最近 N 个放电文件（可配置，默认 20），最少 3 个有效文件才出预测（异常文件会被跳过）
- **异常值校验**：按文件与全局两层过滤，反推掉电速率超过 50%/h 时判定 SOC 跳变等异常并跳过/拒绝输出

## 编码约定

- 所有注释、提交信息、文档使用简体中文
- 基本提交信息格式示例：feat(app): 添加 xxx 功能
- 代码保证连贯性和可读性，不要提取非必要函数或变量，不要过于抽象化
- 禁止引入无职责的包装层或中间模型；如果某个字段没有消费方，应直接删除该层而不是保留“预留结构”
- 代码标识符遵循现有命名约定（英文）
- 新增依赖必须添加到 `gradle/libs.versions.toml` Version Catalog
- AIDL 文件变更需同时检查 `:server` 和 `:shared` 模块
- `hiddenapi:stub` 模块中的类只做声明（compileOnly），不包含实现
- 时间格式化统一使用 `FormatUtil` 中的函数，禁止在 Screen/Chart 中定义局部格式化函数
- 公共 UI 组件放在 `ui/components/global/`，业务特定组件放在对应的 `ui/components/{feature}/`

## 日志约定

- 业务代码统一使用 `LoggerX`，禁止直接调用 `android.util.Log`
- Kotlin 优先使用 reified 封装：`LoggerX.i<Foo>("...")`、`LoggerX.w<Foo>("...")`、`LoggerX.e<Foo>("...", tr = e)`
- Java 或无法使用 reified 的场景，使用 `LoggerX.i("Tag", "...")` 这组重载，不自行封装 `Log.i/e/w`
- 日志内容保持可检索的结构化前缀，如 `[BOOT]`、`[启动请求]`、`[SYNC]`
- 日志落盘、Logcat 输出、日志级别控制统一交给 `LoggerX`，不要绕过其封装直接写底层实现
