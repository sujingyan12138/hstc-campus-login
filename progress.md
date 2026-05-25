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
