package com.hstc.quicklogin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class DeviceManageService(
    private val client: OkHttpClient,
    private val debugLogStore: DebugLogStore
) {
    suspend fun loadBoundDevices(
        credentials: SavedCredentials,
        context: PortalContext
    ): List<BoundDevice> = withContext(Dispatchers.IO) {
        val variants = listOfNotNull(
            context.loginMethod.takeIf { it != 0 }?.toString() to "0",
            null to "0",
            "0" to "0",
            "1" to "0",
            context.loginMethod.takeIf { it != 0 }?.toString() to "1",
            null to "1"
        ).distinct()

        val merged = buildList {
            variants.forEachIndexed { index, (loginMethod, findMac) ->
                val devices = fetchBoundDevices(
                    credentials = credentials,
                    context = context,
                    loginMethod = loginMethod,
                    findMac = findMac,
                    label = "尝试 ${index + 1}"
                )
                addAll(devices)
            }
        }

        merged.distinctBy { sanitizeMac(it.mac) }.sortedWith(
            compareByDescending<BoundDevice> { it.isCurrentDevice }
                .thenByDescending { it.onlineIp.isNotBlank() }
                .thenBy { it.mac }
        ).also {
            debugLogStore.add("已加载绑定设备 ${it.size} 台")
        }
    }

    suspend fun unbindDevice(
        credentials: SavedCredentials,
        context: PortalContext,
        device: BoundDevice
    ): LoginResult = withContext(Dispatchers.IO) {
        unbindByMac(
            credentials = credentials,
            context = context,
            mac = device.mac,
            isCurrentDevice = device.isCurrentDevice
        ).also {
            debugLogStore.add("解绑设备 ${device.mac} success=${it.success}")
        }
    }

    suspend fun logoutCurrentDevice(
        credentials: SavedCredentials,
        context: PortalContext,
        mac: String
    ): LoginResult = withContext(Dispatchers.IO) {
        unbindByMac(
            credentials = credentials,
            context = context,
            mac = mac,
            isCurrentDevice = true
        ).also {
            debugLogStore.add("当前设备强制下线 ${sanitizeMac(mac).uppercase()} success=${it.success}")
        }
    }

    private suspend fun unbindByMac(
        credentials: SavedCredentials,
        context: PortalContext,
        mac: String,
        isCurrentDevice: Boolean
    ): LoginResult = withContext(Dispatchers.IO) {
        val url = buildUrl(
            base = DEFAULT_EPORTAL_HOST,
            path = "portal/mac/unbind",
            query = mapOf(
                "callback" to "androidMacUnbind",
                "user_account" to credentials.username,
                "wlan_user_mac" to sanitizeMac(mac).uppercase(),
                "wlan_user_ip" to context.ip,
                "wlan_user_ipv6" to context.ipv6,
                "unbind_type" to (if (isCurrentDevice) "1" else "2"),
                "jsVersion" to context.jsVersion
            )
        )
        val raw = client.executeString(Request.Builder().url(url).get().build())
        val json = JsonpParser.extractObject(raw)
        val success = json.optInt("result", 0) == 1 || json.optString("result") == "ok"
        val rawMessage = json.optString("msg")
        LoginResult(
            success = success,
            message = when {
                success && isCurrentDevice -> "当前设备已下线"
                success -> "解绑成功"
                rawMessage.isNotBlank() -> rawMessage
                else -> if (isCurrentDevice) "当前设备下线失败" else "解绑失败"
            },
            code = json.opt("result")?.toString().orEmpty(),
            rawResponse = raw
        )
    }

    private fun extractMac(item: JSONObject): String {
        return sequenceOf(
            item.optString("online_mac"),
            item.optString("user_mac"),
            item.optString("mac"),
            item.optString("dev_mac"),
            item.optString("terminal_mac"),
            item.optString("bind_mac"),
            item.optString("wlan_user_mac")
        )
            .map(::sanitizeMac)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private suspend fun fetchBoundDevices(
        credentials: SavedCredentials,
        context: PortalContext,
        loginMethod: String?,
        findMac: String,
        label: String
    ): List<BoundDevice> {
        val query = linkedMapOf(
            "callback" to "androidMacFind",
            "user_account" to credentials.username,
            "find_mac" to findMac,
            "wlan_user_ip" to context.ip,
            "wlan_user_mac" to context.mac,
            "jsVersion" to context.jsVersion
        )
        if (!loginMethod.isNullOrBlank()) {
            query["login_method"] = loginMethod
        }
        val url = buildUrl(
            base = DEFAULT_EPORTAL_HOST,
            path = "portal/mac/find",
            query = query
        )
        val raw = runCatching {
            client.executeString(Request.Builder().url(url).get().build())
        }.getOrElse { error ->
            debugLogStore.add("$label 设备列表查询失败 login_method=${loginMethod ?: "<empty>"} find_mac=$findMac error=${error.message.orEmpty()}")
            return emptyList()
        }
        val json = JsonpParser.extractObject(raw)
        debugLogStore.add("$label 设备列表原始响应 login_method=${loginMethod ?: "<empty>"} find_mac=$findMac: ${redactSensitive(raw).take(320)}")
        val list = json.optJSONArray("list") ?: return emptyList()
        return buildList {
            for (index in 0 until list.length()) {
                val item = list.getJSONObject(index)
                val mac = extractMac(item)
                if (mac.isNotBlank()) {
                    val onlineIp = item.optString("online_ip")
                        .ifBlank { item.optString("ip") }
                        .ifBlank { item.optString("user_ip") }
                    val name = item.optString("device_name")
                        .ifBlank { item.optString("device_alias") }
                        .ifBlank { item.optString("dev_name") }
                        .ifBlank { item.optString("terminal_name") }
                    val status = item.optString("status")
                        .ifBlank { item.optString("online_status") }
                        .ifBlank { if (onlineIp.isNotBlank()) "在线" else "已绑定" }
                    add(
                        BoundDevice(
                            mac = mac.uppercase(),
                            onlineIp = onlineIp,
                            name = name,
                            status = status,
                            isCurrentDevice = sanitizeMac(mac) == sanitizeMac(context.mac) ||
                                (context.ip.isNotBlank() && onlineIp == context.ip)
                        )
                    )
                } else {
                    debugLogStore.add("$label 设备列表第 ${index + 1} 项缺少 MAC，已跳过: ${item.toString().take(200)}")
                }
            }
        }
    }
}
