package com.hstc.quicklogin.data

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.NetworkInterface

class NetworkEnvCollector(
    private val context: Context,
    private val client: OkHttpClient,
    private val debugLogStore: DebugLogStore
) {
    private val probeUrls = listOf(
        "http://connectivitycheck.gstatic.com/generate_204",
        "http://www.msftconnecttest.com/redirect",
        "http://connect.rom.miui.com/generate_204"
    )

    suspend fun collect(): PortalContext = withContext(Dispatchers.IO) {
        val redirectContext = collectFromRedirectProbe()
        val statusContext = collectFromChkStatus()
        val localContext = collectFromLocalInterface()
        val wifiContext = collectFromWifiManager()
        PortalContext(
            ip = redirectContext.ip.ifBlank { statusContext.ip }.ifBlank { localContext.ip }.ifBlank { wifiContext.ip },
            ipv6 = redirectContext.ipv6.ifBlank { statusContext.ipv6 }.ifBlank { localContext.ipv6 }.ifBlank { wifiContext.ipv6 },
            mac = redirectContext.mac.ifBlank { statusContext.mac }.ifBlank { localContext.mac }.ifBlank { wifiContext.mac },
            vlan = redirectContext.vlan.ifBlank { statusContext.vlan },
            wlanAcIp = redirectContext.wlanAcIp.ifBlank { statusContext.wlanAcIp },
            wlanAcName = redirectContext.wlanAcName.ifBlank { statusContext.wlanAcName },
            redirectUrl = redirectContext.redirectUrl.ifBlank { statusContext.redirectUrl }
        ).also {
            debugLogStore.add("环境采集完成 ip=${it.ip.ifBlank { "?" }} mac=${it.mac.ifBlank { "?" }}")
        }
    }

    fun parsePortalRedirect(urlString: String): PortalContext? {
        val url = urlString.toHttpUrlOrNull() ?: return null
        if (!url.host.contains("rz.hstc.edu.cn")) return null
        val context = PortalContext(
            ip = url.queryParameter("wlanuserip").orEmpty(),
            mac = sanitizeMac(url.queryParameter("usermac").orEmpty()),
            vlan = url.queryParameter("uservid").orEmpty(),
            wlanAcIp = url.queryParameter("wlanacip").orEmpty(),
            wlanAcName = url.queryParameter("wlanacname").orEmpty(),
            redirectUrl = urlString
        )
        return if (context.ip.isNotBlank() && context.mac.isNotBlank() && context.wlanAcIp.isNotBlank()) {
            context
        } else {
            null
        }
    }

    private fun collectFromRedirectProbe(): PortalContext {
        probeUrls.forEach { probeUrl ->
            try {
                val request = Request.Builder().url(probeUrl).get().build()
                client.newCall(request).execute().use { response ->
                    val location = response.header("Location").orEmpty()
                    if (location.contains("rz.hstc.edu.cn")) {
                        debugLogStore.add("探测到重定向入口: ${redactSensitive(location)}")
                        val url = location.toHttpUrlOrNull() ?: return@use
                        return PortalContext(
                            ip = url.queryParameter("wlanuserip").orEmpty(),
                            mac = sanitizeMac(url.queryParameter("usermac").orEmpty()),
                            vlan = url.queryParameter("uservid").orEmpty(),
                            wlanAcIp = url.queryParameter("wlanacip").orEmpty(),
                            wlanAcName = url.queryParameter("wlanacname").orEmpty(),
                            redirectUrl = location
                        )
                    }
                }
            } catch (error: Exception) {
                debugLogStore.add("探测地址失败: $probeUrl ${error.message.orEmpty()}")
            }
        }
        return PortalContext()
    }

    private fun collectFromChkStatus(): PortalContext {
        return try {
            val url = buildUrl(
                base = DEFAULT_DOMAIN,
                path = "drcom/chkstatus",
                query = mapOf(
                    "callback" to "androidChk",
                    "program_index" to "",
                    "page_index" to "",
                    "jsVersion" to DEFAULT_JS_VERSION,
                    "v" to System.currentTimeMillis().toString(),
                    "lang" to "zh"
                )
            )
            val body = client.executeString(Request.Builder().url(url).get().build())
            val json = JsonpParser.extractObject(body)
            debugLogStore.add("chkstatus result=${json.optInt("result", -1)}")
            PortalContext(
                ip = json.optString("v46ip").ifBlank { json.optString("ss5") },
                ipv6 = json.optString("myv6ip"),
                mac = sanitizeMac(json.optString("ss4")),
                vlan = json.opt("vid")?.toString().orEmpty().ifBlank { "0" },
                wlanAcIp = json.optString("ss6").ifBlank { json.optString("v4serip") }
            )
        } catch (error: Exception) {
            debugLogStore.add("chkstatus 失败: ${error.message}")
            PortalContext()
        }
    }

    private fun collectFromLocalInterface(): PortalContext {
        return try {
            val iface = NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.firstOrNull { it.name.equals("wlan0", ignoreCase = true) && it.isUp }
                ?: NetworkInterface.getNetworkInterfaces()
                    ?.toList()
                    ?.firstOrNull { it.name.contains("wlan", ignoreCase = true) && it.isUp }
            if (iface == null) {
                PortalContext()
            } else {
                val ipv4 = iface.inetAddresses.toList()
                    .firstOrNull { !it.isLoopbackAddress && !it.hostAddress.contains(":") }
                    ?.hostAddress
                    .orEmpty()
                val ipv6 = iface.inetAddresses.toList()
                    .firstOrNull { !it.isLoopbackAddress && it.hostAddress.contains(":") }
                    ?.hostAddress
                    ?.substringBefore('%')
                    .orEmpty()
                val mac = iface.hardwareAddress?.joinToString("") { "%02x".format(it) }.orEmpty()
                if (ipv4.isNotBlank() || mac.isNotBlank()) {
                    debugLogStore.add("本地网卡信息 ip=${ipv4.ifBlank { "?" }} mac=${mac.ifBlank { "?" }}")
                }
                PortalContext(ip = ipv4, ipv6 = ipv6, mac = mac)
            }
        } catch (error: Exception) {
            debugLogStore.add("本地网卡采集失败: ${error.message.orEmpty()}")
            PortalContext()
        }
    }

    @Suppress("DEPRECATION")
    private fun collectFromWifiManager(): PortalContext {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return PortalContext()
            val info = wifiManager.connectionInfo ?: return PortalContext()
            val mac = sanitizeMac(info.macAddress.orEmpty()).takeUnless { it == "020000000000" }.orEmpty()
            val ipInt = info.ipAddress
            val ipv4 = if (ipInt == 0) {
                ""
            } else {
                listOf(
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                ).joinToString(".")
            }
            if (ipv4.isNotBlank() || mac.isNotBlank()) {
                debugLogStore.add("WifiManager 信息 ip=${ipv4.ifBlank { "?" }} mac=${mac.ifBlank { "?" }}")
            }
            PortalContext(ip = ipv4, mac = mac)
        } catch (error: Exception) {
            debugLogStore.add("WifiManager 采集失败: ${error.message.orEmpty()}")
            PortalContext()
        }
    }
}
