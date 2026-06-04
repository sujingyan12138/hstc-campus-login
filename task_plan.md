# Task Plan: Campus-Wide HS_WIFI Quick Login

## Goal
完善现有手机端校园网快速登录软件，使其不只在南三区宿舍可用，也能在南三区教学/食堂区域以及其他校区区域通过相同认证流程快速登录 HS_WIFI。

## Current Phase
Phase 5

## Phases

### Phase 1: Requirements & Discovery
- [x] Capture campus network behavior and user goal
- [x] Inspect current Android implementation
- [x] Identify why authentication parameter capture is location-specific
- **Status:** complete

### Phase 2: Technical Approach
- [x] Determine robust portal discovery/capture strategy
- [x] Decide minimal code changes that fit the current app
- [x] Document decisions and risks
- **Status:** complete

### Phase 3: Implementation
- [x] Update capture/login logic for campus-wide portal variants
- [x] Preserve existing successful South Zone 3 dorm behavior
- [x] Improve diagnostics for failed captures
- **Status:** complete

### Phase 4: Testing & Verification
- [x] Run unit/build checks available in the repo
- [x] Verify parsing logic with representative portal URLs/responses if present
- [x] Document remaining field-test steps for campus areas
- **Status:** complete

### Phase 5: Delivery
- [x] Summarize changed files and behavior
- [x] Note tests run and any limitations
- **Status:** complete

## Key Questions
1. 当前“抓取认证参数”依赖哪些固定 IP、域名、URL、HTML 字段或重定向模式？
2. 不同区域失败时，是 portal 探测入口不同、重定向参数不同、网络检测方式不同，还是登录 API 参数缺失？
3. 如何在不破坏南三区宿舍已可用流程的前提下，增加全校 portal 自动发现和失败诊断？

## Answers So Far
1. 现有抓取主要依赖 `rz.hstc.edu.cn` URL 中的 `wlanuserip/usermac/wlanacip`，以及用户已有改动里 CAS `service.state` 的固定分段格式。
2. 高概率失败点是探测入口过少、只看 URL 不看 HTML、参数名过窄、CAS state Base64 兼容性不足，以及 `loadConfig` 失败会中断已抓到的上下文。
3. 保留原有南三区宿舍路径，同时新增多探测 URL、HTML URL 抽取、同义参数名、URL-safe Base64、WebView 自动点击统一身份认证、以及 enrich 失败回退。

## Decisions Made
| Decision | Rationale |
|----------|-----------|
| Treat planning files as project working memory | User explicitly asked to use planning-with-files workflow |
| Inspect existing modified files before editing | Worktree is dirty; existing user changes must be preserved |
| Use multi-probe HTTP destinations and HTML extraction | Different campus areas may intercept different connectivity-check hosts or render portal pages instead of redirecting |
| Use tolerant portal parser shared by OkHttp and tests | Avoid duplicating brittle URL parameter parsing across network collection and capture flows |
| Let `loadConfig` failure fall back to captured context | Some zones may return variant config responses; captured IP/MAC/AC should still be retained for CAS login |

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
| PowerShell failed to start `gradlew.bat` with ResourceUnavailable / 找不到指定的模块 | 1 | Retry through `cmd /c gradlew.bat ...` |
| Gradle failed before build with Windows Sockets error 10106 | 2 | Retry with `--no-daemon` to avoid daemon/socket reuse |
| Gradle daemon still failed with Windows Sockets error 10106 | 3 | User provided `SystemRoot` and `WINDIR`; retry with those set |

## Notes
- User's screenshots show Android HS_WIFI captive portal flow: connect Wi-Fi, tap WLAN login notification, choose unified identity authentication, then enter account/password on the Hanshan Normal University login form.
- User reports current implementation works in South Zone 3 dormitory, but "capture auth parameters" fails in South Zone 3 or other-zone teaching areas and cafeterias.
- 2026-06-04 screenshots show a multi-device switching case where the embedded WebView probe reaches `msftconnecttest.com/redirect?hstc_probe_ts=...` and fails with `net::ERR_CONNECTION_RESET`; Android reports the current WLAN cannot access the Internet. Treat this as a probe-entry failure and a possible stale/over-quota device session, not as proof that the account is permanently blocked.
- 2026-06-04 follow-up: WebView probe now advances on main-frame connection reset/HTTP errors and tries direct campus portal fallbacks before exhausting capture.
- 2026-06-04 direct CAS follow-up: unified identity login now first performs the CAS flow through OkHttp: create authorize URL, preserve CAS cookies, fetch public key, RSA-encrypt password, submit login form, follow `ticket` callback. WebView remains as fallback for captcha/MFA/page variants.
- 2026-06-04 delivery follow-up: final phone build includes the Apple-like UI refresh plus bounded scrollable debug log/raw-response panels so long diagnostic output no longer stretches the page.
