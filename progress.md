# Progress Log

## Session: 2026-05-25

### Phase 1: Requirements & Discovery
- **Status:** complete
- **Started:** 2026-05-25
- Actions taken:
  - Confirmed requested use of planning-with-files workflow.
  - Captured the campus network behavior, failure mode, and screenshot flow in persistent planning files.
  - Scanned repository file list and git status.
  - Inspected repository implementation, current local diffs, network collector, portal config service, WebView capture dialog, and app README.
  - Implemented a shared tolerant portal redirect parser.
  - Expanded native network probes and WebView probe destinations.
  - Made capture WebView auto-click the `统一身份认证` entry when it lands on the portal page.
  - Made `loadConfig` failures non-fatal so already captured IP/MAC/AC context is preserved.
  - Added parser tests for alternate parameter names, CAS URL-safe Base64 state, and HTML-contained redirect URLs.
  - Ran `testDebugUnitTest` successfully after setting `SystemRoot` and `WINDIR`.
  - Pulled PCAPdroid capture from `/storage/emulated/0/Download/PCAPdroid/`.
  - Reproduced slow dorm status check through ADB: the first version stayed on "正在处理请求" for 10+ seconds and eventually succeeded.
  - Fixed slow path by moving external redirect probes behind fast `chkstatus` and local Wi-Fi collection.
  - Rebuilt, tested, installed the updated APK, and verified by ADB that status check completes within a few seconds in the dorm.
- Files created/modified:
  - `task_plan.md` created
  - `findings.md` created
  - `progress.md` created
  - `app/src/main/java/com/hstc/quicklogin/data/PortalRedirectParser.kt` created
  - `app/src/main/java/com/hstc/quicklogin/data/NetworkEnvCollector.kt` modified
  - `app/src/main/java/com/hstc/quicklogin/data/PortalConfigService.kt` modified
  - `app/src/main/java/com/hstc/quicklogin/ui/AppScreen.kt` modified
  - `app/src/test/java/com/hstc/quicklogin/data/JsonpParserTest.kt` modified

## Test Results
| Test | Input | Expected | Actual | Status |
|------|-------|----------|--------|--------|
| Unit tests | `$env:SystemRoot='C:\WINDOWS'; $env:WINDIR='C:\WINDOWS'; cmd /c gradlew.bat testDebugUnitTest` | Build and parser tests pass | BUILD SUCCESSFUL | Pass |
| Build + install | `$env:SystemRoot='C:\WINDOWS'; $env:WINDIR='C:\WINDOWS'; cmd /c gradlew.bat testDebugUnitTest assembleDebug`; `adb install -r -d app-debug.apk` | APK builds and installs | BUILD SUCCESSFUL; install Success | Pass |
| Dorm status check after performance fix | ADB launch app, tap "采集环境并检测状态", inspect UI after 4 seconds | No long spinner; online status visible | Completed and showed online state with IP/AC | Pass |
| PCAPdroid manual campus login capture | Start PCAPdroid, open Android HS_WIFI captive portal notification, tap unified identity, submit login, inspect pcap | Determine whether useful portal/auth traffic is captured | Login succeeded and pcap was created, but HSTC portal/auth traffic was absent from the pcap | Partial |
| PCAPdroid app WebView capture | Start PCAPdroid, trigger portal probe/login from this app, inspect pcap | Determine whether app-originated traffic exposes portal parameters | Captured useful IP-hosted portal redirect and CAS callback state; implemented parser/UI support for these formats | Pass |
| Build after app-flow pcap fix | `$env:SystemRoot='C:\WINDOWS'; $env:WINDIR='C:\WINDOWS'; cmd /c gradlew.bat testDebugUnitTest assembleDebug` | Unit tests and debug APK build pass | BUILD SUCCESSFUL | Pass |
| Install after app-flow pcap fix | `adb install -r -d app-debug.apk` | Updated app is installed on connected phone | Success | Pass |
| WebView HTML capture fallback | Extend portal capture to parse loaded page HTML/JavaScript content, not only URL changes | Areas that hide parameters in page content can still be parsed | Implemented, built, and installed | Pass |
| Manual-flow fallback in probe dialog | Let parameter capture follow the same visible flow as the user: portal page, unified identity, CAS form, callback | Capture can succeed even when the first portal page URL does not expose parameters | Implemented, built, and installed | Pass |
| 2026-06-04 build after reset fallback | `$env:SystemRoot='C:\WINDOWS'; $env:WINDIR='C:\WINDOWS'; cmd /c gradlew.bat testDebugUnitTest assembleDebug` | Unit tests and debug APK build pass | BUILD SUCCESSFUL | Pass |
| 2026-06-04 install check | `adb devices` | Find a connected phone for install | No devices attached | Blocked |
| 2026-06-04 direct CAS build | `$env:SystemRoot='C:\WINDOWS'; $env:WINDIR='C:\WINDOWS'; cmd /c gradlew.bat testDebugUnitTest assembleDebug` | Unit tests and debug APK build pass after direct CAS implementation | BUILD SUCCESSFUL | Pass |
| 2026-06-04 install direct CAS APK | `adb install -r -d app\build\outputs\apk\debug\app-debug.apk` | Updated APK installs on phone | Success | Pass |
| 2026-06-04 phone direct CAS login | ADB tap current-device logout, then tap `统一身份认证登录` | App logs in without requiring WebView | Home page showed `状态: 已在线` and `消息: 统一认证登录成功` | Pass |
| 2026-06-04 UI build | `$env:SystemRoot='C:\WINDOWS'; $env:WINDIR='C:\WINDOWS'; cmd /c gradlew.bat assembleDebug` | Debug APK builds after UI redesign | BUILD SUCCESSFUL | Pass |
| 2026-06-04 UI unit tests | `$env:SystemRoot='C:\WINDOWS'; $env:WINDIR='C:\WINDOWS'; cmd /c gradlew.bat testDebugUnitTest` | Existing unit tests pass | BUILD SUCCESSFUL | Pass |
| 2026-06-04 UI install and screenshot | `adb install -r -d app\build\outputs\apk\debug\app-debug.apk`; `adb exec-out screencap -p > ui_home.png` | Updated UI installs and renders on phone | Install Success; screenshot inspected | Pass |
| 2026-06-04 debug log scroll build/install | `$env:SystemRoot='C:\WINDOWS'; $env:WINDIR='C:\WINDOWS'; cmd /c gradlew.bat assembleDebug`; `adb install -r -d app\build\outputs\apk\debug\app-debug.apk`; `adb shell am start -n com.hstc.quicklogin/.MainActivity` | Debug page compiles after adding bounded scrollable log/raw-response panels and app installs on phone | BUILD SUCCESSFUL; install Success; app launched | Pass |
| 2026-06-04 pre-push unit tests | `$env:SystemRoot='C:\WINDOWS'; $env:WINDIR='C:\WINDOWS'; cmd /c gradlew.bat testDebugUnitTest` | Existing unit tests still pass before committing and pushing | BUILD SUCCESSFUL | Pass |

## Session: 2026-06-04

### Multi-device switching failure analysis
- **Status:** complete
- Actions taken:
  - Re-read `task_plan.md`, `progress.md`, `findings.md`, README, and the planning-with-files skill instructions.
  - Inspected `CampusAuthService`, `NetworkEnvCollector`, `DeviceManageService`, `AuthRepository`, `AuthViewModel`, `AppScreen`, parser tests, and portal parser code.
  - Analyzed new screenshots showing `ERR_CONNECTION_RESET` on `msftconnecttest.com/redirect?hstc_probe_ts=...` and Android's `当前 WLAN 无法访问互联网` notification.
  - Added WebView main-frame error handling in the portal probe dialog so `ERR_CONNECTION_RESET` and HTTP errors immediately advance to the next probe destination.
  - Added direct portal fallback probes for `rz.hstc.edu.cn` and the previously captured IP-hosted portal `192.168.2.34`.
  - Ran unit tests and rebuilt the debug APK successfully.
  - Checked ADB device list; no connected device was available for install or field verification.
  - Reworked unified identity login to attempt a pure OkHttp CAS flow before opening WebView.
  - Implemented CAS public-key fetch, RSA/PKCS1 password encryption, login form POST, cookie retention, and manual redirect following through the `ticket` eportal callback.
  - Installed the APK on connected phone `6c6f7e08`.
  - Field-tested by logging out the current phone and then tapping `统一身份认证登录`; the app logged back in directly and showed `统一认证登录成功`.
  - Used the `frontend-design` skill for a refined, Apple-like visual direction.
  - Updated the app theme to a neutral iOS-style palette with system blue accents, light gray background, and cleaner surface colors.
  - Reworked the home page into a large connection status hero, compact environment metric tiles, and rounded action controls.
  - Reworked device, settings, and debug screens with glass-like panels, pill buttons, quieter text hierarchy, and denser but cleaner information grouping.
  - Built the debug APK, installed it on the connected phone, launched the app, and inspected a screenshot for layout issues.
  - Added bounded scrollable panels with visible slim scroll indicators for long debug logs and raw responses.
  - Rebuilt, installed, and launched the updated debug APK on the connected phone.
- Files modified:
  - `app/src/main/java/com/hstc/quicklogin/data/CampusAuthService.kt`
  - `app/src/main/java/com/hstc/quicklogin/data/AuthRepository.kt`
  - `app/src/main/java/com/hstc/quicklogin/ui/AppScreen.kt`
  - `app/src/main/java/com/hstc/quicklogin/ui/theme/Theme.kt`
  - `findings.md`
  - `progress.md`
  - `task_plan.md`

## Error Log
| Timestamp | Error | Attempt | Resolution |
|-----------|-------|---------|------------|
| 2026-05-25 | PowerShell failed to start `gradlew.bat`: ResourceUnavailable / 找不到指定的模块 | 1 | Retry tests via `cmd /c gradlew.bat testDebugUnitTest` |
| 2026-05-25 | Gradle failed before build with `java.net.SocketException: Unrecognized Windows Sockets error: 10106` | 2 | Retry with `cmd /c gradlew.bat --no-daemon testDebugUnitTest` |
| 2026-05-25 | Gradle daemon still failed with Windows Sockets error 10106 | 3 | User provided `SystemRoot`/`WINDIR` environment fix; retry with those env vars set |

## 5-Question Reboot Check
| Question | Answer |
|----------|--------|
| Where am I? | Phase 5 complete: implementation and verification finished |
| Where am I going? | User field-tests capture/login in other campus areas |
| What's the goal? | Campus-wide HS_WIFI quick login from the Android app |
| What have I learned? | See `findings.md` |
| What have I done? | Implemented robust portal capture and verified unit tests |
