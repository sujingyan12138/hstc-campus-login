package com.hstc.quicklogin.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal fun buildUrl(base: String, path: String, query: Map<String, String>): HttpUrl {
    val builder = base.toHttpUrl().newBuilder()
    val normalizedPath = path.trim('/')
    if (normalizedPath.isNotEmpty()) {
        normalizedPath.split('/').forEach(builder::addPathSegment)
    }
    query.forEach { (key, value) ->
        builder.addQueryParameter(key, value)
    }
    return builder.build()
}

internal fun encodeBase64(input: String): String =
    android.util.Base64.encodeToString(input.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

internal fun sanitizeMac(raw: String): String =
    raw.replace("-", "").replace(":", "").lowercase()

internal fun redactSensitive(text: String): String = text
    .replace(Regex("(?i)(upass=)([^&]+)"), "$1***")
    .replace(Regex("(?i)(password=)([^&]+)"), "$1***")
    .replace(Regex("(?i)(DDDDD=)([^&]+)"), "$1***")

internal fun OkHttpClient.executeString(request: Request): String {
    newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code} ${response.message}")
        }
        return response.body?.string().orEmpty()
    }
}
