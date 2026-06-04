package com.hstc.quicklogin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

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

    suspend fun loginWithCasDirect(
        credentials: SavedCredentials,
        context: PortalContext
    ): LoginResult = withContext(Dispatchers.IO) {
        val cookieJar = MemoryCookieJar()
        val casClient = client.newBuilder()
            .cookieJar(cookieJar)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val authorizeUrl = createCasAuthorizeUrl(context)
        debugLogStore.add("尝试纯请求统一认证: ${redactSensitive(authorizeUrl)}")

        val first = casClient.execute(Request.Builder().url(authorizeUrl).get().build())
        followCasTicketRedirects(casClient, first, context)?.let { return@withContext it }

        val loginHtml = first.bodyText
        if (!loginHtml.contains("id=\"fm1\"", ignoreCase = true) &&
            !loginHtml.contains("name=\"execution\"", ignoreCase = true)
        ) {
            return@withContext LoginResult(
                success = false,
                message = "统一认证没有返回可识别的登录表单",
                rawResponse = loginHtml.take(1200)
            )
        }

        val execution = loginHtml.htmlInputValue("execution")
        if (execution.isBlank()) {
            return@withContext LoginResult(
                success = false,
                message = "统一认证登录页缺少 execution，无法纯请求登录",
                rawResponse = loginHtml.take(1200)
            )
        }

        val publicKey = casClient.executeText(
            Request.Builder()
                .url(CAS_PUBLIC_KEY_URL)
                .get()
                .header("Referer", authorizeUrl)
                .build()
        )
        val encryptedPassword = "__RSA__" + encryptPasswordForCas(credentials.password, publicKey)
        val postBody = FormBody.Builder()
            .add("username", credentials.username)
            .add("password", encryptedPassword)
            .add("captcha", "")
            .add("currentMenu", "1")
            .add("failN", "0")
            .add("mfaState", "")
            .add("execution", execution)
            .add("_eventId", "submit")
            .add("geolocation", "")
            .add("submit", "登录")
            .build()
        val post = casClient.execute(
            Request.Builder()
                .url(authorizeUrl)
                .post(postBody)
                .header("Origin", CAS_ORIGIN)
                .header("Referer", authorizeUrl)
                .header("User-Agent", MOBILE_USER_AGENT)
                .build()
        )
        followCasTicketRedirects(casClient, post, context)?.let { return@withContext it }

        val raw = post.bodyText
        val message = when {
            raw.contains("captcha", ignoreCase = true) || raw.contains("验证码") ->
                "统一认证需要验证码，已回退到网页登录"
            raw.contains("mfa", ignoreCase = true) || raw.contains("安全验证") ->
                "统一认证需要二次验证，已回退到网页登录"
            raw.contains("密码", ignoreCase = true) || raw.contains("账号", ignoreCase = true) ->
                "统一认证账号或密码未通过，已回退到网页登录"
            else -> "纯请求统一认证未拿到 ticket，已回退到网页登录"
        }
        debugLogStore.add("纯请求统一认证失败: $message")
        LoginResult(
            success = false,
            message = message,
            rawResponse = raw.take(2000)
        )
    }

    private suspend fun followCasTicketRedirects(
        casClient: OkHttpClient,
        first: CasResponse,
        context: PortalContext
    ): LoginResult? {
        var current = first
        repeat(8) {
            val nextUrl = current.redirectTargetOrNull() ?: return null
            if (nextUrl.contains("ticket=", ignoreCase = true) &&
                nextUrl.contains("/eportal/portal/cas/login", ignoreCase = true)
            ) {
                debugLogStore.add("纯请求统一认证拿到 ticket，回调 eportal")
            }
            current = casClient.execute(
                Request.Builder()
                    .url(nextUrl)
                    .get()
                    .header("User-Agent", MOBILE_USER_AGENT)
                    .build()
            )
            if (current.url.contains("/3.htm") || current.url.contains("login_success", ignoreCase = true)) {
                val (online, account) = checkStatus(context)
                return LoginResult(
                    success = online,
                    message = if (online) "统一认证登录成功" else "已完成统一认证回调，但状态暂未同步",
                    code = if (online) "1" else "0",
                    rawResponse = "account=$account url=${current.url}"
                )
            }
        }
        return null
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
        private const val CAS_PUBLIC_KEY_URL = "https://hscas.hstc.edu.cn/cas/jwt/publicKey"
        private const val CAS_ORIGIN = "https://hscas.hstc.edu.cn"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
    }
}

private data class CasResponse(
    val code: Int,
    val url: String,
    val location: String,
    val bodyText: String
)

private fun OkHttpClient.execute(request: Request): CasResponse {
    newCall(request).execute().use { response ->
        return CasResponse(
            code = response.code,
            url = response.request.url.toString(),
            location = response.header("Location").orEmpty(),
            bodyText = response.body?.string().orEmpty()
        )
    }
}

private fun OkHttpClient.executeText(request: Request): String {
    newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code} ${response.message}")
        }
        return response.body?.string().orEmpty()
    }
}

private fun CasResponse.redirectTargetOrNull(): String? {
    if (code !in 300..399 || location.isBlank()) return null
    val base = url.toHttpUrlOrNullCompat() ?: return location
    return base.resolve(location)?.toString() ?: location
}

private fun String.htmlInputValue(name: String): String {
    val escaped = Regex.escape(name)
    val inputRegex = Regex("""(?is)<input\b(?=[^>]*\bname=["']$escaped["'])[^>]*>""")
    val input = inputRegex.find(this)?.value.orEmpty()
    return Regex("""(?is)\bvalue=["']([^"']*)["']""")
        .find(input)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        .orEmpty()
}

private fun encryptPasswordForCas(password: String, pem: String): String {
    val base64Key = pem
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace(Regex("\\s"), "")
    val keyBytes = android.util.Base64.decode(base64Key, android.util.Base64.DEFAULT)
    val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)
    return android.util.Base64.encodeToString(
        cipher.doFinal(password.toByteArray(Charsets.UTF_8)),
        android.util.Base64.NO_WRAP
    )
}

private class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies.removeAll { old -> cookies.any { it.name == old.name && it.domain == old.domain } }
        this.cookies.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookies.filter { it.matches(url) }
    }
}

private fun String.toHttpUrlOrNullCompat(): HttpUrl? =
    toHttpUrlOrNull()
