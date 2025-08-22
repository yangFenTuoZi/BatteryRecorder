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

-   **应用主题**: 应用似乎直接使用了 `MaterialComponents` 库的默认主题，没有定义全局的自定义主题（如 `Theme.BatteryRecorder`）。
-   **Preference 样式**: `styles.xml` 的主要内容是**对 `Preference` 组件的深度定制**。通过创建一套名为 `Rikka.Material3` 的主题和样式，开发者详细调整了设置界面的字体、边距、颜色等，使其在视觉上贴近 Material 3 的设计规范。
-   **尺寸定义**: `res/values/values.xml` 和 `res/values-sw600dp/values.xml` 中定义了 `home_layout_span_count`，用于实现响应式布局（在不同尺寸的设备上显示不同列数）。