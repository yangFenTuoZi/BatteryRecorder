# BatteryRecorder 续航预测功能 — 分阶段实现计划

## Context

基于对 `plans1.2.md` 的 review，原计划存在架构适配不足、关键实现细节空白、一次性交付风险高等问题。本计划将功能拆为两个可独立交付的阶段：

- **阶段 A**：场景统计 + 简单续航预测（本次实现）
- **阶段 B**：能效桶训练 + 桶预测（阶段 A 验证后再迭代）

---

## 阶段 A：场景统计 + 简单续航预测

### 目标

1. 分场景（息屏 / 亮屏日常）统计放电平均功耗和时长
2. 基于场景功耗 + 历史掉电速率提供粗略续航预测
3. 支持通过游戏列表排除高负载 App

### 前置决策

| 决策点 | 结论 | 理由 |
|---|---|---|
| 游戏列表存储位置 | App 端 SharedPreferences | Server 只采集原始数据，过滤是分析阶段的事 |
| 场景统计计算位置 | `:app` 模块新建 `SceneStatsComputer` | 避免修改 `:shared` 的 `RecordsStats`，不破坏现有缓存 |
| 预测算法 | 能量比例法（不需要电池容量 Wh） | 简单可靠，冷启动快 |
| 数据范围 | 最近 20 个放电文件（复用现有 `loadSummary` 的 `avgPowerLimit` 模式） | 按文件名时间戳过滤，无需读内容 |

---

### 步骤 1：游戏 App 列表配置

**涉及文件：**
- `shared/.../config/ConfigConstants.kt` — 新增 key
- `app/.../ui/viewmodel/SettingsViewModel.kt` — 游戏列表读写
- `app/.../ui/components/settings/sections/GameListSection.kt` — **新建**
- `app/.../ui/screens/settings/SettingsScreen.kt` — 插入新 section

**实现：**
- `ConfigConstants` 增加 `KEY_GAME_PACKAGES = "game_packages"`
- `SettingsViewModel` 增加 `gamePackages: StateFlow<Set<String>>`，读写 `SharedPreferences.getStringSet()`
- `GameListSection`：使用 `SplicedColumnGroup` 模式，显示当前列表 + 添加/删除按钮
  - 添加方式：弹窗输入包名（TextField）
  - 已有项：长按或左滑删除（复用 `SwipeRevealRow`）
- 不做"从已安装列表选择"（过度设计，首期手动输入足够）

---

### 步骤 2：场景统计计算

**涉及文件：**
- `app/.../data/history/SceneStatsComputer.kt` — **新建**，核心计算逻辑
- `app/.../data/history/HistoryRepository.kt` — 新增 `loadSceneStats()` 方法

**数据模型：**

```kotlin
// 场景统计结果
data class SceneStats(
    val screenOffAvgPowerNw: Double,  // 息屏平均功耗（nW）
    val screenOffTotalMs: Long,       // 息屏总时长
    val screenOnDailyAvgPowerNw: Double, // 亮屏日常平均功耗（nW）
    val screenOnDailyTotalMs: Long,   // 亮屏日常总时长
    val drainRatePerHour: Double,     // 整体掉电速率（%/h），来自 capacity 差值
    val totalDurationMs: Long,        // 统计覆盖总时长
    val fileCount: Int                // 参与统计的文件数
)
```

**计算逻辑（`SceneStatsComputer`）：**

1. 取最近 N 个 discharge 文件（按文件名时间戳降序）
2. 逐文件读取 CSV，逐行分类：
   - `isDisplayOn == 0` → SCREEN_OFF
   - `isDisplayOn == 1 && packageName !in gameList` → SCREEN_ON_DAILY
   - `isDisplayOn == 1 && packageName in gameList` → 跳过（不参与统计）
3. 对连续记录对，sample-and-hold 积分：
   - 异常 Δt 过滤：`dt > 5 × recordIntervalMs` 时丢弃该区间
   - 按场景累加 `energy += power × dt` 和 `time += dt`
4. 各场景 `avgPower = totalEnergy / totalTime`
5. 整体掉电速率：`(首条capacity - 末条capacity) / 总时长hours`，取所有文件平均

**缓存策略：**
- 场景统计依赖游戏列表，游戏列表变化时缓存失效
- 缓存 key = `hash(参与文件列表 + gameList)`
- 缓存存储在 `context.cacheDir/scene_stats/`
- 格式：单行 CSV（与 `RecordsStats` 风格一致）

---

### 步骤 3：简单续航预测

**涉及文件：**
- `app/.../data/history/BatteryPredictor.kt` — **新建**

**预测模型（能量比例法）：**

已知：
- `P_off`（息屏平均功耗），`P_daily`（亮屏日常平均功耗）
- `drainRate`（整体掉电速率 %/h）
- `P_avg`（整体平均功耗 = 按时长加权的混合功耗）
- `soc`（当前电量百分比）
- `soc_cutoff = 5%`

原理：功耗与掉电速率成正比

```
drain_rate_off = drainRate × (P_off / P_avg)
drain_rate_daily = drainRate × (P_daily / P_avg)

remaining_off = (soc - soc_cutoff) / drain_rate_off   (小时)
remaining_daily = (soc - soc_cutoff) / drain_rate_daily (小时)
```

**数据不足判断：**
- 场景总时长 < 30 分钟 → 该场景预测标记"数据不足"
- 参与文件数 < 3 → 全部预测标记"数据不足"

---

### 步骤 4：UI 展示

**涉及文件：**
- `app/.../ui/viewmodel/MainViewModel.kt` — 新增 `sceneStats` 和 `prediction` StateFlow
- `app/.../ui/components/home/PredictionCard.kt` — **新建**，续航预测卡片
- `app/.../ui/screens/home/HomeScreen.kt` — 插入预测卡片

**UI 布局（在 HomeScreen 现有 SplicedColumnGroup 中追加）：**

```
┌──────────────────────────────────┐
│ 当前记录卡片 (已有)               │
├──────────────────────────────────┤
│ 续航预测                         │
│  息屏: 约 XX 小时                │
│  亮屏日常: 约 XX 小时            │
│  (数据不足时显示提示)             │
├────────────────┬─────────────────┤
│ 充电总结 (已有) │ 放电总结 (已有)  │
├────────────────┴─────────────────┤
│ 场景统计                         │
│  息屏平均: X.XX W (覆盖 Xh)      │
│  亮屏日常: X.XX W (覆盖 Xh)      │
│  ⓘ 为场景下整机功耗统计          │
└──────────────────────────────────┘
```

**注意事项：**
- 功率值通过 `FormatUtil.computePowerW()` 转换后显示
- 放电显示正值逻辑通过 `PowerDisplayMapper` 处理
- 数据不足时替换数值为灰色"数据不足"文字
- 底部小字提示："预测基于典型日常功耗，高负载行为会显著缩短续航"

---

### 步骤 5：数据加载流程整合

**`MainViewModel` 修改：**
- `loadStatistics()` 中，在现有 `loadSummary()` 之后，调用 `SceneStatsComputer.compute()` 和 `BatteryPredictor.predict()`
- 结果存入新 StateFlow：`_sceneStats`、`_prediction`
- 当前 SOC 从最新记录的 `capacity` 字段获取

---

### 关键文件索引

| 文件 | 操作 | 说明 |
|---|---|---|
| `shared/.../config/ConfigConstants.kt` | 修改 | 新增 `KEY_GAME_PACKAGES` |
| `app/.../ui/viewmodel/SettingsViewModel.kt` | 修改 | 游戏列表 StateFlow + 读写方法 |
| `app/.../ui/components/settings/sections/GameListSection.kt` | **新建** | 游戏列表管理 UI |
| `app/.../ui/screens/settings/SettingsScreen.kt` | 修改 | 插入 GameListSection |
| `app/.../data/history/SceneStatsComputer.kt` | **新建** | 场景统计计算 + 缓存 |
| `app/.../data/history/BatteryPredictor.kt` | **新建** | 续航预测计算 |
| `app/.../ui/viewmodel/MainViewModel.kt` | 修改 | 集成场景统计和预测 |
| `app/.../ui/components/home/PredictionCard.kt` | **新建** | 续航预测 + 场景统计卡片 |
| `app/.../ui/screens/home/HomeScreen.kt` | 修改 | 插入预测卡片 |
| `app/.../utils/FormatUtil.kt` | 修改 | 新增 `formatRemainingTime(hours: Double): String` |

---

## 阶段 B：能效桶模型（阶段 A 之后）

> 以下仅为架构预留说明，不在本次实现范围。

### 内容
1. 掉 1% 事件抽取（含容量跳变/振荡处理）
2. 功耗分桶 + 能效查表
3. 大功耗阈值计算（含 `P_daily ≈ P_off` 退化处理）
4. 桶预测替换简单预测
5. 30 天滑动窗口

### 对阶段 A 的依赖
- 复用 `SceneStatsComputer` 的 `P_off` / `P_daily` 作为桶训练基础
- 复用游戏列表配置
- 预测 UI 框架不变，仅替换内部算法

---

## 验证方案

1. **游戏列表**：设置页添加/删除包名，重启后配置持久化
2. **场景统计**：手动比对某个 discharge CSV 的计算结果（用脚本验算 nW 积分值）
3. **预测准确性**：对比系统内置电池剩余时间估算，偏差在合理范围内（±30%）
4. **冷启动**：清空数据后验证"数据不足"提示正常显示
5. **游戏过滤**：添加某包名为游戏后，重新加载统计，确认该包名数据被排除
6. 以上所有验证由用户在设备上测试，不自主 build
