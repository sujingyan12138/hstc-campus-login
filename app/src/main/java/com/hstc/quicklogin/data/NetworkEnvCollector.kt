package com.hstc.quicklogin.data

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.NetworkInterface

class NetworkEnvCollector(
    private val context: Context,
    private val client: OkHttpClient,
    private val debugLogStore: DebugLogStore
) {
    private val probeUrls = listOf(
        "http://www.gstatic.com/generate_204",
        "http://connectivitycheck.gstatic.com/generate_204",
        "http://www.msftconnecttest.com/redirect",
        "http://connect.rom.miui.com/generate_204",
        "http://captive.apple.com/hotspot-detect.html",
        "http://neverssl.com/",
        "http://1.1.1.1/"
    )

    suspend fun collect(): PortalContext = withContext(Dispatchers.IO) {
        val statusContext = collectFromChkStatus()
        val localContext = collectFromLocalInterface()
        val wifiContext = collectFromWifiManager()
        val baseContext = mergeContexts(statusContext, localContext, wifiContext)
        val redirectContext = if (baseContext.ip.isBlank() || baseContext.wlanAcIp.isBlank()) {
            collectFromRedirectProbe()
        } else {
            PortalContext()
        }
        PortalContext(
            ip = baseContext.ip.ifBlank { redirectContext.ip },
            ipv6 = baseContext.ipv6.ifBlank { redirectContext.ipv6 },
            mac = baseContext.mac.ifBlank { redirectContext.mac },
            vlan = baseContext.vlan.ifBlank { redirectContext.vlan },
            wlanAcIp = baseContext.wlanAcIp.ifBlank { redirectContext.wlanAcIp },
            wlanAcName = baseContext.wlanAcName.ifBlank { redirectContext.wlanAcName },
            redirectUrl = baseContext.redirectUrl.ifBlank { redirectContext.redirectUrl }
        ).also {
            debugLogStore.add("环境采集完成 ip=${it.ip.ifBlank { "?" }} mac=${it.mac.ifBlank { "?" }}")
        }
    }

    fun parsePortalRedirect(urlString: String): PortalContext? {
        return parsePortalRedirectUrl(urlString) { debugLogStore.add(it) }
    }

    fun parsePortalContent(text: String, baseUrl: String): PortalContext? {
        return extractPortalContextFromText(text, baseUrl) { debugLogStore.add(it) }
    }

    private fun collectFromRedirectProbe(): PortalContext {
        probeUrls.forEach { probeUrl ->
            try {
                val request = Request.Builder().url(probeUrl).get().build()
                client.newCall(request).execute().use { response ->
                    val requestUrl = response.request.url.toString()
                    parsePortalRedirect(requestUrl)?.let { return it }

                    val location = response.header("Location")
                    if (!location.isNullOrBlank()) {
                        val resolved = response.request.url.resolve(location)?.toString() ?: location
                        debugLogStore.add("探测到重定向入口: ${redactSensitive(resolved)}")
                        parsePortalRedirect(resolved)?.let { return it }
                    }

                    val body = response.body?.string().orEmpty()
                    extractPortalContextFromText(body, requestUrl) { debugLogStore.add(it) }?.let {
                        return it.copy(redirectUrl = it.redirectUrl.ifBlank { requestUrl })
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
                    .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }
                    ?.hostAddress
                    .orEmpty()
                val ipv6 = iface.inetAddresses.toList()
                    .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == true }
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

    private fun mergeContexts(
        statusContext: PortalContext,
        localContext: PortalContext,
        wifiContext: PortalContext
    ): PortalContext {
        return PortalContext(
            ip = statusContext.ip.ifBlank { localContext.ip }.ifBlank { wifiContext.ip },
            ipv6 = statusContext.ipv6.ifBlank { localContext.ipv6 }.ifBlank { wifiContext.ipv6 },
            mac = statusContext.mac.ifBlank { localContext.mac }.ifBlank { wifiContext.mac },
            vlan = statusContext.vlan.ifBlank { "0" },
            wlanAcIp = statusContext.wlanAcIp,
            wlanAcName = statusContext.wlanAcName,
            redirectUrl = statusContext.redirectUrl
        )
    }

}
