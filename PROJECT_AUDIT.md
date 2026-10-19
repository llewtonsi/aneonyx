# VixLauncher project audit

Audit date: 2026-10-19. This report records the repository inspection and changes made in this pass. Runtime measurements are separated from static findings.

## Project inventory

### Modules and maintained files

| Area | Purpose |
| --- | --- |
| `:app` | The only Android application module. Its application ID and namespace are `com.llewtonsi.vixlauncher`. It compiles/targets API 35, supports API 23+, and is currently version 1.0.1 (code 2), using Kotlin 2.0.21, Compose, and Java 17 bytecode. There are no product flavors or separate library modules. |
| `app/src/main/java/com/llewtonsi/vixlauncher/MainActivity.kt` | Application entry point, launcher screen, app discovery, Compose UI, telemetry sampling, terminal commands, filesystem helpers, and arithmetic parser. Most behavior currently lives in this one file. |
| `app/src/main/AndroidManifest.xml` | HOME/LAUNCHER activity, launcher package visibility query, backup policy, and theme. It declares no storage, network, or runtime permissions. |
| `app/src/main/res/mipmap-*` | Five launcher icon density resources packaged by Android. |
| `assets/vixlauncher-icon.png` | Root-level design asset; it is outside Android's app resources and is not packaged into the APK. |
| `app/build.gradle.kts`, root `build.gradle.kts`, `settings.gradle.kts` | Android/Kotlin plugin versions, app SDK levels, Compose dependencies, and the single-module project setup. No version catalog is present. |
| `gradle/wrapper/*`, `gradle.properties` | Gradle 8.9 wrapper and shared AndroidX/JVM settings. |
| `.github/workflows/release.yml` | Builds and publishes the debug-signed APK on `main` and manual workflow dispatch. It does not sign a production release. |
| `README.md`, `LICENSE` | Product documentation and PolyForm Noncommercial license. |
| `app/src/test/...` | Added local JVM tests for the arithmetic parser. There were no tests before this audit. |

Build outputs, Gradle caches, IDE metadata, and local SDK configuration are generated or machine-specific. They are not source files and should not be committed. No signing key is present in the maintained source. There are no Java sources, XML layouts, custom fonts, source native libraries, app assets directory, ProGuard rules beyond the newly added placeholder, or instrumentation/benchmark modules.

### Entry points, state, and data flow

- `MainActivity.onCreate` configures transparent system bars and wallpaper rendering, then calls `setContent { LauncherScreen(...) }`. `onNewIntent` increments a Compose state signal when Home is pressed again.
- `LauncherScreen` owns the app list, query, terminal output/history, current shared-storage path, grid preference, and wallpaper bitmap. Preferences use the existing file and keys `vixlauncher_preferences`, `command_history`, and `grid_layout`; these were preserved.
- `loadApps(PackageManager)` queries launcher activities, loads labels, and sorts the resulting `AppEntry` list. The initial query and reloads run on `Dispatchers.IO`. The UI filters and alphabetizes that cached list; it does not query PackageManager per keystroke.
- A context-registered package receiver triggers reloads for package add/remove/replace and, after this change, package changed events. It is unregistered when the composable leaves composition.
- Compose `remember` state drives screens. The app list and terminal history are bounded by installed app count and 20 persisted commands respectively. Search results and alphabet sections are remembered from their inputs. Lazy list/grid items have stable keys.
- `/apps [query]` reads the cached launcher list; `/refresh` requests the existing asynchronous PackageManager reload; `/battery`, `/uptime`, and `/version` read current Android/app information without network access or a background task.
- Wallpaper loading/rasterization and app-icon lookup run on `Dispatchers.IO`. The wallpaper is sampled to a maximum 2048-pixel dimension and retained while the launcher screen is alive. Grid icons are rasterized at 96×96 and held by composed cells; there is no process-wide icon cache.
- Terminal commands are dispatched from the search IME action. Filesystem listing, reading, traversal, and mutation run on `Dispatchers.IO`; simple calculations and Android service calls run in the calling coroutine. Commands do not invoke a shell.
- `TelemetryBanner` starts sampling only when expanded and the lifecycle is `STARTED`. `repeatOnLifecycle` cancels the sampling body when the launcher stops; collapsing the panel cancels its effect. RAM and device traffic counters refresh every 5 seconds, CPU load every 15 seconds, and battery/disk every 30 seconds. Disk, CPU, and battery work is reused from the previous sample between those intervals. Sampling runs on `Dispatchers.IO` and updates one telemetry state object.
- There are no declared app services, workers, alarms, file watchers, or network tasks. While the launcher is alive, a passive package receiver remains registered and its Compose state retains the app list and wallpaper. No continuous background task remains after lifecycle stop; expanded telemetry cancels with lifecycle stop.

### Dependencies

Direct production dependencies are Compose BOM 2024.12.01, Activity Compose 1.10.0, Compose animation/foundation/UI, Lifecycle Runtime KTX 2.8.7, and Core KTX 1.15.0. Source usage confirms their respective animation, Compose UI, lifecycle, activity, and receiver helper APIs. The resolved release graph is Compose-centric and includes AndroidX lifecycle/startup, emoji, profile installer, and graphics path transitive components. No dependency was removed because source usage and manifest contributions did not demonstrate a safe removal. JUnit 4.13.2 was added in the test-only configuration for the new local tests.

The release dependency tree was inspected. A vulnerability database scan was not run, so dependency vulnerability status is **unverified**. No broad version upgrades were made. The APK includes `libandroidx.graphics.path.so` for multiple ABIs; AGP reported that it could not strip this library and packaged it unchanged. An AAB/ABI-split distribution could reduce per-device download size, but would change the existing APK release workflow and needs distribution review.

## Findings and prioritization

| Priority | Finding and evidence | Expected impact | Risk and verification |
| --- | --- | --- | --- |
| High security/correctness | `/rm` and `/rmdir` mutated shared-storage paths immediately. `resolveExplorerPath` canonicalizes and confines paths to shared storage, but the UI offered no explicit destructive confirmation. | Reduces accidental deletion risk while preserving both commands. | Added `--confirm`, blocks removal of the storage root, and documents the syntax. Build verified; device-level file command behavior was not exercised. |
| High memory/stability | `commandOutput` prepended every result indefinitely and `TerminalOutput` rendered the entire list in a scrolling `Column`. `/find` limited matches but could traverse the full tree if matches were sparse or absent. | Bounds retained terminal strings and UI nodes; caps traversal work. | Added limits of 20 results, 8,000 output characters per result, 512 command characters, and 5,000 visited entries per search. Build verified; runtime allocation profiling unavailable. |
| Correctness | The outer command handler caught `Exception` without preserving coroutine cancellation and hid failures without any diagnostic. | Prevents cancellation from becoming a normal command result and leaves a type-only diagnostic without command input or file paths. | Cancellation is rethrown and unexpected command failures are logged by exception class only. Build verified; cancellation workflow was not device-tested. |
| Moderate performance / correctness | The terminal `/free` and RAM tap directly called `Runtime.gc()`, which can stall work and does not release system caches. | Removes forced GC and keeps `/free` useful as a managed-heap snapshot; RAM tap refreshes telemetry only. | Verified statically that no `System.gc()`/`Runtime.gc()` call remains. Device responsiveness impact was not measured. |
| Moderate correctness | Package refresh watched add/remove/replace but not `ACTION_PACKAGE_CHANGED`, so enabled-state changes could leave stale entries. | Refreshes after package state changes. | Added the event to the existing package-scheme receiver. Device broadcast behavior was not tested. |
| Moderate build/security | Release had no explicit shrinking configuration; only a debug APK workflow existed. | Enables R8 and resource shrinking for a production variant and sets `isDebuggable = false`. | Release R8 build and packaged manifest inspected. Release remains unsigned; no signing material or workflow was changed. |
| Application identity compatibility | The namespace and application ID had a placeholder name. | Changed both to `com.llewtonsi.vixlauncher` and moved Kotlin source/test packages. | This is a distinct Android app identity: earlier installs remain separate and private app preferences do not migrate. Keep this ID stable before public distribution. |
| Low / unverified | Grid icon bitmaps are decoded again when lazy cells leave and re-enter composition. The cache is intentionally not made unbounded without device memory profiling. | A small bounded cache may reduce repeated decode work, at a fixed memory cost. | Hypothesis only; no change made pending profiler evidence. |
| Low / unverified | Wallpaper blur spans the full launcher background and a bounded bitmap remains retained while the activity lives. | Could affect GPU frame time and memory, depending on wallpaper/display. | Static inspection only; no measured change made. |
| Low / unverified | `File.listFiles()` materializes a whole directory before limiting displayed entries to 200. | A directory with a very large number of entries can allocate an array larger than the terminal output. | Existing API behavior is bounded at output level but not allocation level. No alternative was introduced because preserving API 23 support and accurate listing semantics needs a deliberate design. |

## Security and manifest review

- The release APK manifest retains package visibility limited to `MAIN` + `LAUNCHER`; there is no `QUERY_ALL_PACKAGES` permission.
- The app manifest declares no `INTERNET`, broad storage, or other dangerous permission. The release merge contains only the Core KTX-generated `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` plus its corresponding use-permission.
- `MainActivity` is exported because Android must launch it as HOME/DEFAULT/LAUNCHER. It has no separate intent filters for terminal commands. The context receiver is registered `RECEIVER_NOT_EXPORTED`.
- `android:allowBackup="false"` remains in force. The application ID and namespace were intentionally changed to `com.llewtonsi.vixlauncher`; signing material, preference keys, and persisted history/layout formats were not changed. Data under the previous application ID is not migrated.
- Release `aapt dump badging` and manifest inspection show no debuggable marker/attribute. The release Activity is exported as required. AndroidX's merged Startup provider is not exported; its profile installer receiver is protected by `android.permission.DUMP`.
- The file explorer canonicalizes paths and requires them to stay beneath the shared storage root. It does not follow a shell or request all-files access. `/find` has a traversal cap; `/cat`/`/grep` limit input file size. Deletion now additionally requires confirmation and cannot remove the shared-storage root.
- `/kill` reports Android's force-stop restriction and opens App Info; `/uninstall` uses the platform delete confirmation intent. `/clip` reads or clears clipboard only when explicitly invoked. `/dnd` directs the user to notification-policy access settings when not granted.
- Signing configuration is unchanged. Release is unsigned and requires a separately managed key before distribution. The CI debug-signed APK remains suitable for testing, not as a persistent production signing identity.

## Baseline and verification

The available environment had JDK 21 and Android SDK build tools but no connected ADB device. Therefore Android runtime startup, interactive latency, memory, CPU, frames, battery, crashes on device, and Niagara comparison could not be measured. No performance result is claimed from APK size or static review.

| Measure | Before changes | After changes | Status |
| --- | ---: | ---: | --- |
| Debug APK size | 7,017,851 bytes | 7,017,844 bytes | Measured. The final clean build is 7 bytes smaller, effectively unchanged; the application ID changed, so this is not a performance comparison. |
| Optimized release APK size | No prior release variant | 1,080,648 bytes | Measured, unsigned. Not a before/after comparison. |
| Baseline debug build | `assembleDebug` succeeded | — | Measured on the existing Gradle cache; most baseline tasks were up-to-date. |
| Post-change build | — | `clean testDebugUnitTest assembleDebug assembleRelease` succeeded | Clean rebuild verified with Gradle 8.9 and JDK 21 after package ID and command changes. R8 and resource shrinking ran. |
| Unit tests | No tests existed | 5 arithmetic parser tests passed | Verified in `testDebugUnitTest` XML results. |
| Release debuggable flag | No explicit release build configuration | Packaged release has no debuggable attribute | Verified with `aapt`; release build type explicitly sets false. |
| Permissions / exported components | Static manifest review | No new app permissions; release manifest inspected | Verified by packaged manifest inspection. Runtime permission behavior not applicable to declared app permissions. |
| Startup, idle PSS, Java/native heap, allocations, CPU, frame timing, battery, repeat navigation, large-list search | Not available | Not available | Not tested: no connected device or profiler. |
| Niagara comparison | Not available | Not available | Not tested: device/install state unavailable. |
| Vulnerability scan | Not run | Not run | Not tested; dependency graph alone does not establish vulnerability status. |

## Changed files

- `app/src/main/java/com/llewtonsi/vixlauncher/MainActivity.kt` — bounded terminal/search work, explicit deletion confirmation, cancellation-safe command handling, no forced GC, app-change refresh event, added commands, and corresponding user messages.
- `app/build.gradle.kts` — explicit non-debuggable R8/resource-shrunk release and JUnit test dependency.
- `app/proguard-rules.pro` — narrow placeholder; no keep rules are currently needed by the source.
- `app/src/test/java/com/llewtonsi/vixlauncher/ArithmeticParserTest.kt` — precedence, grouping, unary operators, modulo, divide-by-zero, malformed trailing input, non-finite results, and excessive nesting tests.
- `README.md` — updated terminal behavior, confirmation syntax, application ID note, and unsigned optimized release build instructions.
- `PROJECT_AUDIT.md` — this inventory, findings, evidence, and measurement record.

## Acceptance status and remaining work

- **Verified:** Debug and minified/shrunk release builds compile; 5 unit tests pass; release is not debuggable; manifest contains no new broad permissions; file path confinement remains; no forced GC call remains; telemetry is collapsed by default and lifecycle-scoped when expanded.
- **Failed:** None of the build or unit test checks run in this pass failed.
- **Not tested:** Device UI behavior, package broadcasts, destructive command interaction, telemetry cancellation on actual activity transitions, large app lists, allocation/retained heap profiling, startup, CPU/battery/frame metrics, long-run stability, and Niagara comparison.
- **Not applicable:** Database migrations and app-level services/workers; neither exists in the project.

Recommended next work is a repeatable Galaxy A15 4G test pass with release-signed builds and Macrobenchmark/Perfetto, including icon-scroll allocation profiling, wallpaper/blur frame timing, app list refresh behavior, and telemetry enabled versus collapsed. Review the resolved dependency versions with a vulnerability scanner before publishing a production build.

## Release readiness follow-up (2026-10-19)

- Incremented `versionCode` from 1 to 2 and `versionName` from 1.0 to 1.0.1. The built APK and AAB metadata both report package `com.llewtonsi.vixlauncher`, version code 2, and version name 1.0.1.
- Re-ran `testDebugUnitTest assembleDebug assembleRelease bundleRelease`; Gradle completed successfully. The test task was up-to-date from the immediately preceding clean test run (five tests passed); the release APK and App Bundle were packaged successfully.
- Final artifact sizes: debug APK 7,017,852 bytes; optimized unsigned release APK 1,080,652 bytes; unsigned release AAB 2,557,839 bytes. The AAB is not signed (`jarsigner -verify` reports unsigned). These are build sizes, not runtime-memory evidence.
- Inspected the packaged release manifest with `aapt`: it contains package `com.llewtonsi.vixlauncher`, min SDK 23, target SDK 35, the HOME/DEFAULT/LAUNCHER activity, and no `android:debuggable` attribute. No dangerous app permissions or Internet permission were added. A repository text and path search finds no remaining legacy product name (build outputs and Git history excluded).
- The machine has SDK platforms 35 and 37, but no API 36 platform; it uses AGP 8.7.3, which supports API 35. I left compile/target SDK at 35 to keep this project buildable with its installed toolchain. This is **not Google Play submission-ready**: Google Play's requirement effective 31 August 2026 is target API 36 for new apps and updates. Install API 36 and upgrade the compatible Android Gradle Plugin/Gradle pair, then rebuild and retest before Play submission.
- There is no connected device; ADB could not start its server in this environment. Launcher interaction, app listing/launching, storage command behavior, lifecycle transitions, API 36 behavior, startup, PSS/RAM, CPU, frames, battery, and Niagara comparisons remain untested. No performance improvement claim is made.
- The APK/AAB are unsigned for release. Before distribution, configure a privately managed upload/release key, preserve it for future updates, and use Play App Signing for Play distribution. Changing the application ID creates a separate installation; private data from the former ID is not automatically migrated. The currently configured ID must remain stable for updates to this app.

For Google Play, additionally prepare an Android App Bundle, Play App Signing, store listing and content declarations, Data safety answers and a privacy policy as applicable, and satisfy the testing requirement for the developer account type. New personal developer accounts created after 13 November 2023 must complete a closed test with at least 12 opted-in testers continuously for 14 days before applying for production access. See [target API policy](https://support.google.com/googleplay/android-developer/answer/11926878), [closed testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), and [Android app signing](https://developer.android.com/studio/publish/app-signing).
