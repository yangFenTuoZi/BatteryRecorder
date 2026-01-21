# Repository Guidelines

## Project Structure & Module Organization
- `app/` is the Android app (Kotlin + Jetpack Compose). UI lives under `app/src/main/java/yangfentuozi/batteryrecorder/ui/compose/`, resources in `app/src/main/res/`.
- `server/` is the standalone service process (Kotlin/Java + JNI). Native code is in `server/src/main/jni/`, AIDL in `server/src/main/aidl/`.
- `hiddenapi/stub/` provides hidden API stubs for compilation; `hiddenapi/compat/` wraps compatibility helpers.
- Shared configuration is via Gradle Kotlin scripts: `build.gradle.kts`, module `*/build.gradle.kts`, and `gradle/libs.versions.toml`.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` builds the debug APK for the app.
- `./gradlew assembleRelease` builds the release APK.
- `./gradlew :server:assembleDebug` builds only the server module (including JNI).
- `./gradlew test` runs local JVM unit tests (if present).
- `./gradlew connectedAndroidTest` runs instrumented tests on a device/emulator.

## Coding Style & Naming Conventions
- Use Kotlin standard style (4-space indentation, trailing commas where it improves diffs).
- Class names `UpperCamelCase`, functions/variables `lowerCamelCase`.
- Resource names are `snake_case` (e.g., `ic_launcher_foreground.xml`).
- AIDL files follow `IService.aidl`-style naming in `server/src/main/aidl/`.

## Testing Guidelines
- There are no dedicated test directories in this repo yet; add unit tests under `app/src/test/` and instrumented tests under `app/src/androidTest/`.
- Prefer naming tests `*Test.kt` and keep feature scopes aligned with UI/viewmodel boundaries.

## Commit & Pull Request Guidelines
- Recent commits use conventional prefixes like `feat:` and `refactor:` with short, descriptive summaries (often in Chinese). Follow that pattern.
- PRs should include: a clear description, verification steps (commands or manual checks), and UI screenshots or recordings when UI changes are involved.

## Security & Configuration Tips
- The service process relies on Shizuku/root permissions to read battery sysfs data; document any new permission requirements explicitly.
- Runtime settings are stored in app preferences; avoid hardcoding values that should be configurable.
