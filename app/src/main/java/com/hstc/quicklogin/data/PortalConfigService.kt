package com.hstc.quicklogin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PortalConfigService(
    private val client: OkHttpClient,
    private val debugLogStore: DebugLogStore
) {
    suspend fun enrich(context: PortalContext): PortalContext = withContext(Dispatchers.IO) {
        if (context.ip.isBlank()) return@withContext context
        runCatching {
            val url = buildUrl(
                base = DEFAULT_EPORTAL_HOST,
                path = "portal/page/loadConfig",
                query = mapOf(
                    "callback" to "androidLoadConfig",
                    "program_index" to context.programIndex,
                    "wlan_vlan_id" to context.vlan.ifBlank { "0" },
                    "wlan_user_ip" to encodeBase64(context.ip),
                    "wlan_user_ipv6" to encodeBase64(context.ipv6),
                    "wlan_user_ssid" to "",
                    "wlan_user_areaid" to "",
                    "wlan_ac_ip" to encodeBase64(context.wlanAcIp),
                    "wlan_ap_mac" to "000000000000",
                    "gw_id" to "000000000000",
                    "page_index" to context.pageIndex,
                    "jsVersion" to context.jsVersion.ifBlank { DEFAULT_JS_VERSION },
                    "v" to (System.currentTimeMillis() % 10000).toString(),
                    "lang" to "zh"
                )
            )
            val body = client.executeString(Request.Builder().url(url).get().build())
            val json = JsonpParser.extractObject(body)
            val data = json.optJSONObject("data")
                ?: throw IllegalStateException(json.optString("msg").ifBlank { "loadConfig 未返回 data" })
            context.copy(
                programIndex = data.optString("program_index").ifBlank { context.programIndex },
                pageIndex = data.optString("page_index").ifBlank { context.pageIndex },
                loginMethod = data.optString("login_method").toIntOrNull() ?: context.loginMethod,
                unbindMacEnabled = data.optString("un_bind_mac") == "1" || context.unbindMacEnabled
            )
        }.onSuccess {
            debugLogStore.add("loadConfig 完成 program=${it.programIndex} page=${it.pageIndex}")
        }.onFailure { error ->
            debugLogStore.add("loadConfig 失败，保留已采集参数: ${error.message.orEmpty()}")
        }.getOrDefault(context)
    }
}
