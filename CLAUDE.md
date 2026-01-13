# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**BatteryRecorder** is an Android system-level battery monitoring application that uses a client-server architecture. The server runs with elevated privileges (root/shell) to collect raw battery data from `/sys/class/power_supply/battery/`, while the client app provides UI and configuration.

### Core Architecture

- **`app` module**: Kotlin-based client acting as UI and configuration controller. Uses **Jetpack Compose** with Material 3, Navigation Compose.
- **`server` module**: Kotlin-based backend service that handles all data collection, processing, and storage.
- **`hiddenapi` modules**: Stub library (`hiddenapi:stub`) and compatibility layer (`hiddenapi:compat`) for accessing Android hidden APIs.

### Client-Server Communication

Communication via **AIDL** (`IService.aidl`):
1. Server detects when app is in foreground and pushes its Binder through `BinderProvider` (ContentProvider)
2. App calls server methods: `refreshConfig()`, `stopService()`, `writeToDatabaseImmediately()`

### Critical Constraints

- **Server requires root/shell privileges** to read `/sys` filesystem
- **All data logic MUST be in `server` module** - `app` is strictly for UI and configuration control only
- **No data processing or file I/O in `app` module**
- All new inter-module features MUST extend `IService.aidl` interface

## Build Commands

```bash
# Build entire project
./gradlew build

# Build specific module
./gradlew :app:build
./gradlew :server:build

# Clean build
./gradlew clean

# Install debug build to connected device
./gradlew installDebug

# Generate AIDL bindings
./gradlew :server:compileDebugAidl
```

**Build Configuration**:
- Java 21 (source & target compatibility)
- Target API 36, Min API 31
- Kotlin DSL for Gradle scripts
- Compose compiler plugin enabled in app module

## Module Structure

### `app` module
- `MainActivity.kt`: Entry point with Compose content, extends `ComponentActivity`
- `ui/compose/`: All Compose UI code
  - `BatteryRecorderApp.kt`: Root composable with Material 3 theme
  - `BatteryRecorderNavHost.kt`: Navigation setup (home, settings, about routes)
  - `screens/home/HomeScreen.kt`: Main dashboard with service status cards
  - `screens/settings/SettingsScreen.kt`: Configuration UI for sampling rate, batch size, flush interval, calibration
  - `viewmodel/MainViewModel.kt`: State management for UI
- `Service.kt`: Singleton managing binder connections and lifecycle
- `BinderProvider.kt`: ContentProvider for receiving server's binder

### `server` module
- `Server.kt`: Main service implementing AIDL interface, monitoring loop
- `PowerUtil.kt`: Direct system interface for battery metrics, includes `BatteryStatus` enum and power calculation (voltage × current)
- `DataWriter.kt`: Data storage with hybrid batch/timed flushing and segmentation by charge/discharge state
- `IService.aidl`: AIDL interface for IPC

### `hiddenapi:stub` module
Compiler-only stub for hidden API references (compileOnly dependency for server)

### `hiddenapi:compat` module
Runtime compatibility layer for hidden API access

## Data Flow

1. **PowerUtil** reads from `/sys/class/power_supply/battery/`:
   - `voltage_now` (μV)
   - `current_now` (μA)
   - `capacity` (%)
   - `status` (charging/discharging/full/unknown)

2. **Power calculation**: Power (W) = voltage (V) × current (A) - positive for charging, negative for discharging

3. **Server** samples at configurable intervals (default: 900ms) via `HandlerThread`

4. **DataWriter** handles storage with hybrid strategy:
   - Flush when batch size reached (default: 200 records)
   - Flush after timeout (default: 30 seconds)
   - Segmentation by charge/discharge state (creates new file when current direction changes with 30s gap)
   - Automatic file rotation (24-hour segments)
   - State detection based on `PowerUtil.BatteryStatus` with noise filtering during transitions

## Data Storage Format

**Location**: `/data/user/0/yangfentuozi.batteryrecorder/power_data/`

**File naming**: `[timestamp].txt` (no +/- suffix, state determined by directory)

**CSV format**: `timestamp,power,packageName,capacity,isDisplayOn`

Example:
```
1672531200000,2100000,com.android.launcher,95,1
1672531205000,2150000,com.some.app,95,1
```

**Field descriptions**:
- `timestamp`: Unix timestamp in milliseconds
- `power`: Power in μW (microwatts), calculated as voltage × current (positive = charging, negative = discharging)
- `packageName`: Foreground app package name
- `capacity`: Battery capacity percentage
- `isDisplayOn`: `1` = screen on, `0` = screen off

## Configuration

- Stored in `SharedPreferences` at `/data/user/0/yangfentuozi.batteryrecorder/shared_prefs/yangfentuozi.batteryrecorder_preferences.xml`
- Modified by `app` via `SettingsScreen.kt` Compose UI
- Server reloads via `refreshConfig()` AIDL call
- Key settings: sampling interval (`interval`), batch size (`batch_size`), flush interval (`flush_interval`), calibration values

## Display State Tracking

Server tracks display state using `IDisplayManager` and `IDisplayManagerCallback`:
- `isInteractive` reflects whether device is interactive (screen on)
- Changes are detected via `displayCallback` which calls `iPowerManager.isInteractive`
- State is included in each power record as the last field

## DataWriter Hybrid Flushing Logic

The `BaseWriter` class implements a hybrid flushing strategy:
1. **Immediate flush**: When `batchCount >= batchSize` (reaches threshold)
2. **Timed flush**: After `flushIntervalMs` elapses (timeout)
3. **Auto-stop**: Timer stops when buffer is empty, restarts on new data

Implementation details:
- Each `BaseWriter` has its own `Handler` posting to the shared `Looper`
- `postDelayedWriting()` starts the delayed flush if not already running
- When batch threshold is hit, flush immediately and cancel pending timer
- When timer fires, flush and reschedule only if new data arrived during async write

## UI Framework

- **Jetpack Compose** with Material 3 components
- **Navigation Compose** for screen routing
- **ViewModel** with Compose state management
- Material 3 theming with system theme following (no manual dark/light toggle)
- Uses `Theme.Material3.DayNight.NoActionBar` (follows system theme automatically)

## Development Rules

1. **Single Responsibility**: All file I/O in `DataWriter`, all `/sys` interactions in `PowerUtil`
2. **Logic Separation**: UI (`app`) vs. backend (`server`) - no coupling
3. **Interface Clarity**: Keep AIDL interface minimal, expose only necessary control methods
4. **Thread Safety**: Server uses `HandlerThread` for monitoring and data writing operations
5. **File Ownership**: All created files must have ownership changed via `changeOwner()` to app UID

## System Services Used

Server requires and waits for these system services:
- `activity`: For `IActivityManager` (binder exchange, content provider access)
- `activity_task`: For `IActivityTaskManager` (task/focus tracking)
- `display`: For `IDisplayManager` (display state tracking)
- `power`: For `IPowerManager` (interactive state queries)
- `package`: For `IPackageManager` (app info lookup)
