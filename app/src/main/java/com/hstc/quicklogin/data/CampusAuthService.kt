package com.hstc.quicklogin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class CampusAuthService(
    private val client: OkHttpClient,
    private val debugLogStore: DebugLogStore
) {
    suspend fun createCasAuthorizeUrl(context: PortalContext): String = withContext(Dispatchers.IO) {
        val url = buildUrl(
            base = DEFAULT_EPORTAL_HOST,
            path = "portal/cas/create",
            query = linkedMapOf(
                "callback" to "androidCasCreate",
                "login_method" to "1",
                "wlan_user_ip" to context.ip,
                "wlan_user_ipv6" to context.ipv6,
                "wlan_user_mac" to context.mac,
                "wlan_ac_ip" to context.wlanAcIp,
                "wlan_ac_name" to context.wlanAcName,
                "authex_enable" to "",
                // Android app should consume a mobile-device slot rather than the PC slot.
                "mac_type" to "1",
                "jsVersion" to context.jsVersion,
                "program_index" to context.programIndex,
                "page_index" to context.pageIndex,
                "v" to (System.currentTimeMillis() % 10000).toString(),
                "lang" to "zh"
            )
        )
        debugLogStore.add("发起统一身份认证入口生成")
        val raw = client.executeString(Request.Builder().url(url).get().build())
        val json = JsonpParser.extractObject(raw)
        val authorizeUrl = json.optString("authorize_uri")
        if (json.optInt("result", 0) == 1 || json.optString("result") == "ok") {
            if (authorizeUrl.isBlank()) {
                throw IllegalStateException("统一身份认证入口返回成功，但没有 authorize_uri")
            }
            debugLogStore.add("统一身份认证入口已生成")
            authorizeUrl
        } else {
            val message = json.optString("msg").ifBlank { "统一身份认证入口生成失败" }
            debugLogStore.add("统一身份认证入口失败: $message")
            throw IllegalStateException(message)
        }
    }

    suspend fun checkStatus(context: PortalContext): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val url = buildUrl(
            base = DEFAULT_DOMAIN,
            path = "drcom/chkstatus",
            query = mapOf(
                "callback" to "androidStatus",
                "program_index" to context.programIndex,
                "page_index" to context.pageIndex,
                "jsVersion" to context.jsVersion,
                "v" to (System.currentTimeMillis() % 10000).toString(),
                "lang" to "zh"
            )
        )
        val response = client.executeString(Request.Builder().url(url).get().build())
        val json = JsonpParser.extractObject(response)
        val online = json.optInt("result", 0) == 1
        val account = json.optString("uid")
        debugLogStore.add("状态检测 online=$online account=${account.ifBlank { "?" }}")
        online to account
    }

    suspend fun login(credentials: SavedCredentials, context: PortalContext): LoginResult =
        withContext(Dispatchers.IO) {
            val url = buildUrl(
                base = DEFAULT_DOMAIN,
                path = "drcom/login",
                query = linkedMapOf(
                    "callback" to "androidLogin",
                    "DDDDD" to credentials.username,
                    "upass" to credentials.password,
                    "0MKKey" to "123456",
                    "R1" to "0",
                    "R2" to "0",
                    "R3" to "0",
                    "R6" to "0",
                    "para" to "00",
                    "v4ip" to context.ip,
                    "v6ip" to context.ipv6,
                    "terminal_type" to "2",
                    "lang" to "zh-cn",
                    "operate" to "portal_login",
                    "jsVersion" to context.jsVersion,
                    "v" to (System.currentTimeMillis() % 10000).toString()
                )
            )
            debugLogStore.add("发起登录: ${redactSensitive(url.toString())}")
            val raw = client.executeString(Request.Builder().url(url).get().build())
            val json = JsonpParser.extractObject(raw)
            toLoginResult(json, raw)
        }

    suspend fun logoutCurrent(context: PortalContext): LoginResult = withContext(Dispatchers.IO) {
        val url = buildUrl(
            base = DEFAULT_EPORTAL_HOST,
            path = "portal/cas/logout",
            query = linkedMapOf(
                "callback" to "androidCasLogout",
                "wlan_user_ip" to context.ip,
                "jsVersion" to context.jsVersion
            )
        )
        debugLogStore.add("发起当前设备注销")
        val raw = client.executeString(Request.Builder().url(url).get().build())
        val json = JsonpParser.extractObject(raw)
        val success = json.optInt("result", 0) == 1 || json.optString("result") == "ok"
        val switchUrl = json.optString("switch_uri")
        if (success && switchUrl.isNotBlank()) {
            runCatching {
                client.newCall(Request.Builder().url(switchUrl).get().build()).execute().use { }
            }
            debugLogStore.add("已跟进注销跳转")
        }
        LoginResult(
            success = success,
            message = json.optString("msg").ifBlank { if (success) "当前设备已注销" else "当前设备注销失败" },
            code = json.opt("result")?.toString().orEmpty(),
            rawResponse = raw
        ).also {
            debugLogStore.add("当前设备注销 success=${it.success}")
            if (!it.success) {
                debugLogStore.add("注销原始响应: ${redactSensitive(raw)}")
            }
        }
    }

    suspend fun drcomLogout(context: PortalContext): LoginResult = withContext(Dispatchers.IO) {
        val sanitizedMac = sanitizeMac(context.mac)
        if (context.ip.isBlank() || sanitizedMac.isBlank()) {
            return@withContext LoginResult(
                success = false,
                message = "缺少当前设备 IP 或 MAC，无法执行 drcom 注销"
            )
        }
        val url = buildUrl(
            base = DEFAULT_DOMAIN,
            path = "drcom/logout",
            query = linkedMapOf(
                "callback" to "androidDrcomLogout",
                "ip" to context.ip,
                "mac" to sanitizedMac
            )
        )
        debugLogStore.add("发起 drcom 注销: ${redactSensitive(url.toString())}")
        val raw = runCatching {
            client.executeString(Request.Builder().url(url).get().build())
        }.getOrElse { error ->
            debugLogStore.add("drcom 注销异常: ${error.message.orEmpty()}")
            return@withContext LoginResult(
                success = false,
                message = error.message ?: "drcom 注销异常"
            )
        }
        val json = JsonpParser.extractObject(raw)
        val success = json.optInt("result", 0) == 1 || json.optString("result") == "ok"
        LoginResult(
            success = success,
            message = json.optString("msg").ifBlank { if (success) "drcom 注销成功" else "drcom 注销失败" },
            code = json.opt("result")?.toString().orEmpty(),
            rawResponse = raw
        ).also {
            debugLogStore.add("drcom 注销 success=${it.success}")
            if (!it.success) {
                debugLogStore.add("drcom 注销原始响应: ${redactSensitive(raw)}")
            }
        }
    }

    private fun toLoginResult(json: JSONObject, raw: String): LoginResult {
        val success = json.optInt("result", 0) == 1 || json.optString("result") == "ok"
        val rawMessage = json.optString("msg")
            .ifBlank { json.optString("message") }
            .ifBlank { if (success) "登录成功" else "登录失败" }
        val message = when {
            !success && raw.contains("userid error2", ignoreCase = true) ->
                "当前账号不支持普通账号密码直登，可能需要走统一身份认证"
            !success && rawMessage == "1" ->
                "门户返回错误码 1，当前账号或登录方式可能不匹配"
            else -> rawMessage
        }
        val needsDeviceAction = !success && DEVICE_ACTION_HINTS.any {
            message.contains(it, ignoreCase = true) || raw.contains(it, ignoreCase = true)
        }
        return LoginResult(
            success = success,
            message = message,
            code = json.optString("ret_code").ifBlank { json.opt("result")?.toString().orEmpty() },
            rawResponse = raw,
            requiresDeviceAction = needsDeviceAction
        ).also {
            debugLogStore.add("登录结果 success=${it.success} msg=${it.message}")
            if (!it.success) {
                debugLogStore.add("登录原始响应: ${redactSensitive(raw)}")
            }
        }
    }

    companion object {
        private val DEVICE_ACTION_HINTS = listOf("绑定", "终端", "设备", "数量", "上限", "mac", "解绑")
    }
}
