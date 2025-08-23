# BatteryRecorder 数据文件格式

本文档详细描述了 `BatteryRecorder` 应用所生成的数据文件的格式，旨在为数据文件的使用者提供清晰的解析指南。

## 文件命名规则

数据文件名由两部分组成，格式为 `[第一条记录的时间戳][+/-].txt`。

-   **`[第一条记录的时间戳]`**: 一个长整型（Long）数字，代表该文件中第一条记录的 Unix 时间戳，单位为毫秒 (ms)。
-   **`[+/-]`**: 一个符号，用于标识记录期间的电池状态。
    -   `+`: 表示电池正在**放电**（消耗电量）。
    -   `-`: 表示电池正在**充电**。
-   **新文件创建**: 当电流方向发生改变时（即从充电切换到放电，或从放电切换到充电），应用会创建一个新的数据文件。

## 文件内容结构

-   文件为纯文本格式（`.txt`）。
-   每行包含一条完整的记录。
-   每条记录中的各个字段由英文逗号 (`,`) 分隔。

## 记录字段详解

每行记录包含以下五个字段，顺序固定：

| 字段名称 | 数据类型 | 单位 | 说明 |
| :--- | :--- | :--- | :--- |
| `时间戳` | Long | 毫秒 (ms) | 记录该条数据时的 Unix 时间戳。 |
| `电流` | Integer | 原始值 | 从系统文件中读取的瞬时电流原始值。 |
| `电压` | Integer | 原始值 | 从系统文件中读取的瞬时电压原始值。 |
| `前台包名` | String | 无 | 记录时前台正在运行的应用程序的包名。 |
| `电量` | Integer | % | 电池电量百分比，整数范围为 0-100。 |

## 示例

以下是一个完整的文件名和文件内容示例，用于展示真实的数据格式。

**文件名示例:**

```
1672531200000+.txt
```

> 这表示文件记录的是放电状态的数据，第一条记录的时间戳为 `1672531200000`。

**文件内容示例:**

```
1672531200000,500,4231,com.android.launcher,95
1672531205000,510,4230,com.some.app,95
1672531210000,495,4229,com.some.app,94
```

## 实际功率计算

文件中的 `电流` 和 `电压` 字段记录的均为从系统文件中直接读取的原始整数值，不带单位。

实际功率的计算在 App 端完成，其计算公式如下：

`实际功率 = (文件中的电流值) * (文件中的电压值) * (App中设置的校准值)`

**说明**:
- **`文件中的电流值`**: 对应记录中的 `电流` 字段。
- **`文件中的电压值`**: 对应记录中的 `电压` 字段。
- **`App中设置的校准值`**: 这是一个在 App 设置中由用户定义的浮点数。它可以是正数、负数或小数，用于校准最终的功率值。例如，可以将其设置为 `-1` 来反转功率的正负号，以匹配不同设备的标准。
# 项目规则与开发指南

## 1. 项目概述与技术架构

本项目是一个 Android 电池状态记录工具，旨在精确、持续地监控和记录设备的详细电量信息。

项目采用经典的**客户端-服务器 (Client-Server)** 架构：

*   **客户端 (`app` 模块)**: 作为用户界面 (UI) 和配置控制器。
*   **服务器 (`server` 模块)**: 作为后台核心服务，负责所有的数据采集和存储。

两者通过 **AIDL (Android 接口定义语言)** 进行进程间通信 (IPC)。

**核心技术栈:**

*   **Kotlin**: 主要用于 `app` 模块的 UI 和逻辑。
*   **Java**: 主要用于 `server` 模块的核心服务。
*   **AIDL**: 用于实现 `app` 和 `server` 之间的跨进程通信。
*   **Android Hidden API**: 用于访问系统私有接口，以实现高级功能（如前台应用追踪）。

---

## 2. 代码结构与模块说明

项目包含三个核心模块：`app`, `server`, `hiddenapi`。

*   ### `app` 模块
    *   **职责**:
        1.  提供用户界面，用于展示服务状态和配置项。
        2.  作为配置控制器，允许用户修改采样频率、批量大小等参数，并将变更通知给 `server`。
        3.  接收 `server` 主动推送的 Binder 对象，建立通信。
    *   **关系**: 依赖 `server` 模块提供的 AIDL 接口来发送控制命令。

*   ### `server` 模块
    *   **职责**:
        1.  **核心业务逻辑**: 实现所有的数据采集、处理和存储。
        2.  **高权限运行**: 必须在 `root` 或 `shell` 权限下运行，以便直接读取 `/sys` 文件系统中的原始电池数据。
        3.  **服务调度**: 管理数据采集任务的生命周期，并使用 `ITaskStackListener` 追踪前台应用。
        4.  **通信服务**: 实现 AIDL 接口，响应来自 `app` 的控制请求。
    *   **关系**: 编译时依赖 `hiddenapi` 模块来链接系统私有 API。它不依赖 `app`，但会主动与 `app` 建立通信。

*   ### `hiddenapi` 模块
    *   **职责**:
        1.  一个存根 (Stub) 库，仅包含 Android 系统私有 API 的方法签名。
        2.  其唯一目的是让 `server` 模块在编译时能够成功链接到这些私有 API，而不会在运行时被直接使用（运行时会调用系统真正的实现）。
    *   **关系**: 是 `server` 模块的一个编译时依赖 (`compileOnly`)。

---

## 3. 关键业务逻辑与数据流

### 数据流 (单向)
数据流严格在 `server` 模块内部单向流动并最终落盘：

1.  **数据采集 (`PowerUtil.java`)**:
    *   作为数据的唯一来源。
    *   直接从 `/sys/class/power_supply/battery/` 目录下的文件中读取原始、未经处理的电池数据（如电压、电流）。
    *   此操作需要高权限。

2.  **数据调度 (`Server.java`)**:
    *   作为 `server` 模块的大脑。
    *   根据配置的采样频率，周期性地调用 `PowerUtil` 来获取数据。

3.  **数据存储 (`PowerDataStorage.java`)**:
    *   作为数据的唯一出口。
    *   从 `Server.java` 接收数据。
    *   **批量写入**: 将数据缓存起来，达到一定数量后一次性写入文件，以减少 I/O 开销。
    *   **分段存储**: 根据电池的充电/放电状态，将数据写入到不同的文件中，便于后续分析。

### 控制流 (`app` -> `server`)
控制流由 `app` 发起，通过 AIDL 调用 `server` 的方法：

1.  **通信建立 (`BinderProvider.kt`)**:
    *   `server` 进程启动后，会检测 `app` 进程是否在前台。
    *   一旦检测到，`server` 会主动通过 `ContentProvider` 机制，将自身的 Binder "推送"给 `app`。

2.  **配置变更 (`SettingsFragment.kt` -> `Server.java`)**:
    *   用户在 `app` 的设置界面修改了配置（如采样间隔）。
    *   配置被写入 `SharedPreferences`。
    *   `app` 通过 AIDL 调用 `server` 的 `refreshConfig()` 方法。
    *   `Server.java` 收到调用后，重新从 `SharedPreferences` 加载配置。

3.  **服务停止 (`MainActivity.kt` -> `Server.java`)**:
    *   用户在 `app` 点击停止按钮。
    *   `app` 通过 AIDL 调用 `server` 的 `stopService()` 方法，`server` 进程退出。

---

## 4. 开发注意事项与约束条件 (必须遵守)

1.  **权限**: `server` 模块必须在 `root` 或 `shell` 权限下运行，否则无法工作。
2.  **逻辑分离**: 所有核心业务逻辑（数据采集、处理、存储）**必须**在 `server` 模块中实现。
3.  **`app` 角色**: `app` 模块**严格**作为 UI 和配置控制器，**禁止**包含任何数据处理或文件读写逻辑。
4.  **通信接口**: 模块间的任何新功能或数据交换，**必须**通过扩展现有的 `IService.aidl` 接口来实现。
5.  **数据格式**: 数据存储格式（分段、批量写入）**必须**保持一致，任何修改都需在 `PowerDataStorage.java` 中统一进行。
6.  **配置管理**: 配置由 `app` 修改，存储在 `SharedPreferences` 中，`server` 仅负责读取和响应 `refreshConfig()` 调用来重新加载。

---

## 5. 编码规范与最佳实践

*   **单一职责原则**:
    *   所有文件 I/O 操作应封装在 `PowerDataStorage.java` 中。
    *   所有与系统底层硬件 API (`/sys/`) 的交互应封装在 `PowerUtil.java` 中。
*   **接口清晰**: 保持 AIDL 接口的简洁和明确，只暴露必要的控制方法。
*   **逻辑分离**: 严格分离 UI 交互逻辑 (`app`) 和后台服务逻辑 (`server`)，避免耦合。
# BatteryRecorder UI 框架分析文档

本文档旨在详细解析 `BatteryRecorder` 应用 `app` 模块的 UI 框架、核心组件和实现方式，为后续的开发和维护提供清晰的技术指引。

## 1. UI 整体架构

`app` 模块的 UI 层完全基于传统的 **Android View 系统** 构建，未使用 Jetpack Compose。其架构简洁、经典，遵循了 Google 推荐的现代 Android 开发实践。

-   **技术选型**:
    -   **View 系统**: UI 元素通过 XML 布局文件定义。
    -   **ViewBinding**: 在 `build.gradle.kts` 中启用 (`viewBinding = true`)，用于安全、便捷地访问 XML 中定义的视图，完全替代了 `findViewById`。
    -   **Material Components**: 项目依赖 `com.google.android.material:material`，所有核心 UI 组件如 `MaterialToolbar`, `MaterialAlertDialogBuilder` 等均来自该库，确保了统一的 Material Design 风格。
    -   **RecyclerView**: 用于构建列表界面，是 UI 的核心组件。
    -   **PreferenceFragmentCompat**: 用于构建标准化、易于维护的设置界面。

-   **基本架构**:
    -   UI 逻辑主要分布在 `Activity` 和 `Fragment` 中。
    -   通过显式的 `Intent` 在不同 `Activity` 之间进行导航。
    -   整体架构清晰，UI 控制逻辑与业务逻辑（在 `server` 模块）严格分离。

---

## 2. 核心界面分析

### 2.1. 主屏幕 (`MainActivity`)

主屏幕是应用的入口点，其实现相对简单，目前主要用于展示一个静态的启动按钮。

-   **布局 (`activity_main.xml`)**:
    -   采用垂直的 `LinearLayout` 作为根布局。
    -   顶部是 `com.google.android.material.appbar.MaterialToolbar`，作为应用的标题栏。
    -   主体部分是一个 `androidx.recyclerview.widget.RecyclerView`，用于显示内容卡片。
    -   `RecyclerView` 使用了 `StaggeredGridLayoutManager`，并从 `res/values/values.xml` 中读取 `home_layout_span_count`（值为 2），实现了**两列网格**的布局效果。

-   **实现 (`MainActivity.kt`)**:
    -   继承自 `BaseActivity`。
    -   在 `onCreate` 方法中，通过 `ActivityMainBinding.inflate()` 初始化视图绑定。
    -   设置 `Toolbar` 并处理菜单项的点击事件（`onCreateOptionsMenu`, `onOptionsItemSelected`），负责跳转到设置界面和显示“关于”对话框。
    -   `RecyclerView` 的 `Adapter`（`HomeAdapter`）目前是静态的，只显示一个“启动服务”的卡片。

### 2.2. 设置屏幕 (`SettingsActivity` & `SettingsFragment`)

设置屏幕是应用配置的唯一入口，其实现遵循了 Android 官方的最佳实践。

-   **布局 (`activity_settings.xml`)**:
    -   非常简单，仅包含一个 `FrameLayout` 作为容器，用于承载 `SettingsFragment`。

-   **实现 (`SettingsActivity.kt` & `SettingsFragment.kt`)**:
    -   `SettingsActivity` 的职责是创建并显示 `SettingsFragment`。
    -   `SettingsFragment` 继承自 `PreferenceFragmentCompat`，是设置界面的核心。
    -   **配置加载**: 通过 `addPreferencesFromResource(R.xml.preferences)` 从 `preferences.xml` 文件中加载所有设置项。
    -   **交互处理**: 通过 `findPreference<Preference>(...).setOnPreferenceClickListener` 为每个设置项（如“采样间隔”、“批量大小”）绑定点击事件，弹出 `MaterialAlertDialogBuilder` 对话框供用户修改配置。
    -   **配置持久化**: 修改后的配置通过 `preference.sharedPreferences.edit { ... }` 直接写入 `SharedPreferences`。
    -   **通知服务**: 在配置变更后，通过调用 `Service.service?.refreshConfig()` **主动通知**后台的 `server` 模块重新加载配置，实现了 `app` 作为控制器的角色。

---

## 3. 主题与样式

应用的主题和样式主要定义在 `res/values/styles.xml` 中。

-   **应用主题**: 应用直接在 `AndroidManifest.xml` 使用了 `Theme.Material3.DayNight.NoActionBar` 主题，没有定义全局的自定义主题（如 `Theme.BatteryRecorder`）。
-   **Preference 样式**: `styles.xml` 的主要内容是**对 `Preference` 组件的深度定制**。通过创建一套名为 `Rikka.Material3` 的主题和样式，开发者详细调整了设置界面的字体、边距、颜色等，使其在视觉上贴近 Material 3 的设计规范。
-   **尺寸定义**: `res/values/values.xml` 和 `res/values-sw600dp/values.xml` 中定义了 `home_layout_span_count`，用于实现响应式布局（在不同尺寸的设备上显示不同列数）。