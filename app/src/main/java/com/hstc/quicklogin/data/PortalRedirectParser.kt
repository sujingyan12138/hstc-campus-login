package com.hstc.quicklogin.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Base64

internal fun parsePortalRedirectUrl(
    urlString: String,
    debug: ((String) -> Unit)? = null
): PortalContext? {
    val url = urlString.toHttpUrlOrNull() ?: return null

    parseCasServiceState(urlString, debug)?.let { return it }

    val host = url.host.lowercase()
    val hasPortalParams = url.hasAnyQueryValue(
        "wlanuserip",
        "wlan_user_ip",
        "userip",
        "clientip",
        "v4ip",
        "ip",
        "wlanacip",
        "wlan_ac_ip",
        "acip",
        "wlanac_ip",
        "nasip",
        "v4serip",
        "usermac",
        "wlan_user_mac",
        "wlanusermac",
        "clientmac",
        "mac"
    )
    if (!host.contains("hstc.edu.cn") && !hasPortalParams) return null

    val context = PortalContext(
        ip = url.firstQueryValue("wlanuserip", "wlan_user_ip", "userip", "clientip", "v4ip", "ip"),
        ipv6 = url.firstQueryValue("wlanuseripv6", "wlan_user_ipv6", "useripv6", "v6ip", "ipv6"),
        mac = sanitizeMac(url.firstQueryValue("usermac", "wlan_user_mac", "wlanusermac", "clientmac", "mac")),
        vlan = url.firstQueryValue("uservid", "wlan_vlan_id", "vlan", "vid").ifBlank { "0" },
        wlanAcIp = url.firstQueryValue("wlanacip", "wlan_ac_ip", "acip", "wlanac_ip", "nasip", "v4serip"),
        wlanAcName = url.firstQueryValue("wlanacname", "wlan_ac_name", "acname", "wlanacname"),
        programIndex = url.firstQueryValue("program_index", "programIndex"),
        pageIndex = url.firstQueryValue("page_index", "pageIndex"),
        jsVersion = url.firstQueryValue("jsVersion", "jsversion").ifBlank { DEFAULT_JS_VERSION },
        redirectUrl = urlString
    )
    return if (context.ip.isNotBlank() || context.wlanAcIp.isNotBlank()) context else null
}

internal fun extractPortalContextFromText(
    text: String,
    baseUrl: String,
    debug: ((String) -> Unit)? = null
): PortalContext? {
    val normalized = text
        .replace("&amp;", "&")
        .replace("\\u0026", "&")
        .replace("\\/", "/")

    extractUrls(normalized, baseUrl).forEach { candidate ->
        parsePortalRedirectUrl(candidate, debug)?.let {
            debug?.invoke("从页面内容提取认证参数 URL: ${redactSensitive(candidate)}")
            return it
        }
    }

    val context = PortalContext(
        ip = normalized.firstValue("wlanuserip", "wlan_user_ip", "userip", "clientip", "v4ip", "ip"),
        ipv6 = normalized.firstValue("wlanuseripv6", "wlan_user_ipv6", "useripv6", "v6ip", "ipv6"),
        mac = sanitizeMac(normalized.firstValue("usermac", "wlan_user_mac", "wlanusermac", "clientmac", "mac")),
        vlan = normalized.firstValue("uservid", "wlan_vlan_id", "vlan", "vid").ifBlank { "0" },
        wlanAcIp = normalized.firstValue("wlanacip", "wlan_ac_ip", "acip", "wlanac_ip", "nasip", "v4serip"),
        wlanAcName = normalized.firstValue("wlanacname", "wlan_ac_name", "acname", "wlanacname"),
        programIndex = normalized.firstValue("program_index", "programIndex"),
        pageIndex = normalized.firstValue("page_index", "pageIndex")
    )
    return if (context.ip.isNotBlank() || context.wlanAcIp.isNotBlank()) context else null
}

private fun parseCasServiceState(urlString: String, debug: ((String) -> Unit)?): PortalContext? {
    val url = urlString.toHttpUrlOrNull() ?: return null
    val service = url.queryParameter("service").orEmpty()
    val serviceUrl = service.toHttpUrlOrNull()
    val stateEncoded = serviceUrl?.queryParameter("state").orEmpty().ifBlank {
        url.queryParameter("state").orEmpty()
    }
    if (stateEncoded.isBlank()) return null

    val decoded = decodeBase64Compat(stateEncoded) ?: return null
    debug?.invoke("从统一认证 service/state 解析参数: $decoded")
    val parts = decoded.split("|")
    val ipv4s = IPV4_REGEX.findAll(decoded).map { it.value }.toList()
    val macs = MAC_REGEX.findAll(decoded).map { sanitizeMac(it.value) }.filter { it.length == 12 }.toList()
    val context = PortalContext(
        ip = parts.getOrNull(2).orEmpty().takeIf { IPV4_REGEX.matches(it) } ?: ipv4s.firstOrNull().orEmpty(),
        ipv6 = parts.getOrNull(3).orEmpty().takeIf { it.contains(":") }.orEmpty(),
        mac = sanitizeMac(parts.getOrNull(4).orEmpty()).takeIf { it.length == 12 } ?: macs.firstOrNull().orEmpty(),
        vlan = parts.getOrNull(5).orEmpty().ifBlank { "0" },
        wlanAcIp = parts.getOrNull(6).orEmpty().takeIf { IPV4_REGEX.matches(it) } ?: ipv4s.getOrNull(1).orEmpty(),
        wlanAcName = parts.getOrNull(7).orEmpty(),
        redirectUrl = urlString
    )
    return if (context.ip.isNotBlank()) context else null
}

private fun decodeBase64Compat(value: String): String? {
    val normalized = value.trim().replace(' ', '+')
    val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
    return listOf(
        { Base64.getDecoder().decode(padded) },
        { Base64.getUrlDecoder().decode(padded) }
    ).firstNotNullOfOrNull { decoder ->
        runCatching { String(decoder(), Charsets.UTF_8) }.getOrNull()
    }
}

private fun HttpUrl.firstQueryValue(vararg names: String): String {
    names.forEach { name ->
        queryParameter(name)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return ""
}

private fun HttpUrl.hasAnyQueryValue(vararg names: String): Boolean =
    names.any { name -> !queryParameter(name).isNullOrBlank() }

private fun String.firstValue(vararg names: String): String {
    names.forEach { name ->
        val escaped = Regex.escape(name)
        val queryMatch = Regex("""(?i)(?:[?&])$escaped=([^&"'<>\\\s]+)""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
        if (!queryMatch.isNullOrBlank()) return queryMatch

        val assignmentMatch = Regex("""(?i)\b$escaped\b["']?\s*[:=]\s*["']([^"'&<>\\\s]+)""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
        if (!assignmentMatch.isNullOrBlank()) return assignmentMatch
    }
    return ""
}

private fun extractUrls(text: String, baseUrl: String): List<String> {
    val base = baseUrl.toHttpUrlOrNull()
    val absolute = Regex("""https?://[^\s"'<>]+""")
        .findAll(text)
        .map { it.value.trimEnd(',', ';', ')', ']') }
    val relative = Regex("""(?i)(?:href|src|action|location\.href)\s*=\s*["']([^"']+)["']""")
        .findAll(text)
        .mapNotNull { match ->
            val value = match.groupValues.getOrNull(1).orEmpty()
            if (value.startsWith("http://") || value.startsWith("https://")) {
                value
            } else {
                base?.resolve(value)?.toString()
            }
        }
    return (absolute + relative).distinct().toList()
}

private val IPV4_REGEX = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
private val MAC_REGEX = Regex("""(?i)(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}|\b[0-9a-f]{12}\b""")
