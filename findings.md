# Findings & Decisions

## Requirements
- Target app is an Android phone-side quick-login tool for HS_WIFI.
- Campus has South, Central, and West zones; South is split into South 1, South 2, South 3.
- Each zone has teaching, dormitory, and cafeteria areas.
- Campus network account policy: monthly paid account allows one PC and two mobile devices online; extra devices displace earlier sessions.
- Without recharge, free usage is only available in teaching areas.
- User moves between classes, cafeterias, and dormitories, and different areas require re-login.
- Existing app currently works in the user's South 3 dormitory and supports quick login from multiple devices there.
- Existing "capture authentication parameters" fails outside South 3 dormitory, including teaching/cafeteria areas and other zones.
- Goal is campus-wide quick login while preserving the already working South 3 dormitory behavior.

## Research Findings
- Initial repo scan shows an Android/Kotlin app under `app/src/main/java/com/hstc/quicklogin`.
- Relevant files likely include `AuthRepository.kt`, `NetworkEnvCollector.kt`, `CampusAuthService.kt`, `PortalConfigService.kt`, and UI/ViewModel files.
- Worktree already has modified files: `AuthRepository.kt`, `NetworkEnvCollector.kt`, and `AppScreen.kt`.
- Current `OkHttpClient` disables redirects, so redirect `Location` headers can be inspected directly.
- Current probe UI loads only `http://www.msftconnecttest.com/redirect`; this may miss campus areas that intercept different HTTP destinations or render a portal page without changing the final URL.
- Current parser already supports `rz.hstc.edu.cn` query params and a CAS `service=...state=...` URL, but it is strict about parameter names and uses basic Base64 decoding only.
- `PortalConfigService.enrich()` currently assumes `loadConfig` always returns a `data` object; if a different zone returns an error or variant response, capture can fail after parameters have already been extracted.
- In the dorm test after the first campus-wide changes, tapping "采集环境并检测状态" stayed on "正在处理请求" for roughly 10+ seconds before succeeding. The slow path was caused by external redirect probes running before faster campus `chkstatus` and local Wi-Fi collection.
- The pulled PCAPdroid capture did not contain HSTC portal domains or auth APIs; parsed traffic was mostly WeChat/Xiaomi/Google keepalive traffic, so it did not explain the campus auth flow.

## Technical Decisions
| Decision | Rationale |
|----------|-----------|
| Preserve existing modified files and inspect before editing | Avoid overwriting user changes in dirty worktree |
| Focus first on portal discovery/capture logic | Reported failure occurs when clicking "capture authentication parameters" outside one area |
| Add tolerant parsing and multi-probe capture | Different campus areas can vary in redirect destination, query names, or captive portal interception behavior |
| Keep captured context when config enrichment fails | Partial context with IP/MAC/AC is more useful than treating the entire capture as failed |
| Run `chkstatus` and local Wi-Fi collection before external redirect probes | Restores fast dorm status checks while keeping redirect probing as a fallback for missing parameters |

## Issues Encountered
| Issue | Resolution |
|-------|------------|
| Gradle failed with Windows socket error 10106 until `SystemRoot` and `WINDIR` were set | Retried tests with `$env:SystemRoot='C:\WINDOWS'; $env:WINDIR='C:\WINDOWS'` |
| Campus portal capture was too strict and single-entry | Added shared tolerant parser, multi-probe destinations, HTML extraction, CAS state parsing, and WebView auto-click of unified identity entry |
| Dorm status check became slow after adding multi-probe collection | Reordered `NetworkEnvCollector.collect()` so external probes only run if `chkstatus`/local Wi-Fi do not provide enough context |
| PCAPdroid captured traffic but did not include the Android system captive-portal login flow | Verified PCAPdroid was set to PCAP file mode and all-app capture, completed a successful HS_WIFI login through the system notification, then parsed the resulting pcap; it contained unrelated app traffic but no HSTC portal domains or auth parameters |

## Resources
- Project root: `D:\PyCharm\CODE\HSTC`
- Planning plugin source: `C:\Users\Chen\.claude\plugins\cache\planning-with-files\planning-with-files\2.38.1`
- New parser: `app/src/main/java/com/hstc/quicklogin/data/PortalRedirectParser.kt`
- Tests: `app/src/test/java/com/hstc/quicklogin/data/JsonpParserTest.kt`

## Visual/Browser Findings
- Screenshot 1: phone is connected to Wi-Fi SSID `HS_WIFI`.
- Screenshot 2: Android captive portal notification says `登录到 WLAN 网络 HS_WIFI`.
- Screenshot 3: portal page title `登录HS_WIFI`, with Hanshan Normal University branding and tabs including `统一身份认证` and `账号密码登录`; the user taps `统一身份认证`.
- Screenshot 4: final login form has account/password fields and a blue `登录` button; tabs include `账号登录` and `手机动态码登录`.

## PCAPdroid Findings
- PCAPdroid package on the phone: `com.emanuelef.remote_capture`.
- Settings observed through ADB: capture mode was `PCAP 文件`, target app was `捕获所有应用的流量`, status was `就绪`.
- Manual flow completed through Android captive portal notification: notification opened `登录HS_WIFI`, unified identity entry opened the account/password login form, and login reached `登录成功页`.
- New phone-side capture file was created under `/storage/emulated/0/Download/PCAPdroid/`.
- Local pcap inspection found other app traffic but no `rz.hstc.edu.cn`, `hscas.hstc.edu.cn`, `hsportal.hstc.edu.cn`, `wlanuserip`, `usermac`, `wlanacip`, `state`, or `service` strings.
- Conclusion: the user's PCAPdroid settings were not the main problem. For this phone/ROM path, the Android captive portal/system browser traffic is not visible enough in PCAPdroid's plain pcap output to debug campus auth parameters.
- A later PCAPdroid capture triggered from the app's embedded WebView did contain useful portal traffic:
  - DNS resolved `rz.hstc.edu.cn` to `192.168.2.34`.
  - DNS resolved `hscas.hstc.edu.cn` to `210.38.208.247`.
  - The WebView probe to `www.gstatic.com/generate_204` was intercepted and returned a script redirect to `http://192.168.2.34/a79.htm?...`.
  - The redirect URL contained portal parameters including client IP, client MAC, VLAN/user VID, AC IP, AC name, NAS port, and a generated userid.
  - After CAS login, the callback hit `http://192.168.2.34:801/eportal/portal/cas/login?state=...&ticket=...`, and the `state` decoded to the same portal environment values.
- Implementation follow-up: broadened parser and WebView capture predicates to accept IP-hosted portal URLs and direct CAS callback `state` URLs, not only `rz.hstc.edu.cn` / `hscas.hstc.edu.cn`.
- Additional robustness follow-up: the capture dialog now also reads loaded WebView page HTML and extracts portal parameters from HTML/JavaScript content. This covers areas where the URL itself does not expose the parameters but the portal page contains a redirect script or embedded config.
- Capture strategy follow-up: since the visible manual flow is stable across campus areas, the probe dialog now treats the manual flow itself as a fallback discovery path. If direct probe URL/content capture fails, it attempts the unified identity entry, auto-fills the saved CAS account form, and waits for the callback URL/content where `state` can expose the required portal parameters.
- 2026-06-04 new screenshots show the embedded probe WebView failing on `http://www.msftconnecttest.com/redirect?hstc_probe_ts=...` with `net::ERR_CONNECTION_RESET`, while Android also reports the current WLAN cannot access the Internet. This points to the captive portal or gateway resetting a probe destination before the app reaches a usable portal page.
- Implementation follow-up: the probe dialog should treat main-frame WebView load errors as a failed probe and immediately advance to the next destination. Direct `rz.hstc.edu.cn` and the previously observed IP-hosted portal `192.168.2.34` are useful fallbacks when external connectivity-check hosts are reset.
- HAR analysis confirms the unified identity path can be reduced to HTTP requests: `eportal/portal/cas/create` returns a CAS login URL, CAS eventually redirects back to `/eportal/portal/cas/login?...&ticket=ST-...`, and the eportal callback brings the device online.
- CAS password submission is not plain text. The login page fetches `/cas/jwt/publicKey`, then submits `password=__RSA__` plus RSA/PKCS1-encrypted password, together with `username`, `execution`, `_eventId=submit`, `currentMenu=1`, and related hidden fields.
- 2026-06-04 phone field test: after upgrading the connected phone, logging out the current phone reduced the visible device list to two devices. Pressing `统一身份认证登录` then completed without showing WebView and returned to `状态: 已在线` / `消息: 统一认证登录成功`.
- UI refinement direction: Apple-like minimal polish works well for this utility app when the status hero is visually dominant, secondary data is grouped into compact metric tiles, and destructive/secondary actions are kept as soft rounded controls.
- Debug page usability follow-up: log output must stay in a bounded, independently scrollable console-style panel. Otherwise long diagnostic sessions make the whole page hard to navigate and bury the raw-response section.
