package com.hstc.quicklogin.data

data class SavedCredentials(
    val username: String = "",
    val password: String = "",
    val autoRetry: Boolean = true,
    val loggingEnabled: Boolean = false
)

data class PortalContext(
    val ip: String = "",
    val ipv6: String = "",
    val mac: String = "",
    val vlan: String = "0",
    val wlanAcIp: String = "",
    val wlanAcName: String = "",
    val programIndex: String = "",
    val pageIndex: String = "",
    val redirectUrl: String = "",
    val loginMethod: Int = 0,
    val unbindMacEnabled: Boolean = false,
    val jsVersion: String = DEFAULT_JS_VERSION
) {
    val hasEnoughForLogin: Boolean
        get() = ip.isNotBlank()

    val hasEnoughForCas: Boolean
        get() = ip.isNotBlank() && mac.isNotBlank() && wlanAcIp.isNotBlank()
}

data class LoginResult(
    val success: Boolean,
    val message: String,
    val code: String = "",
    val rawResponse: String = "",
    val requiresDeviceAction: Boolean = false
)

data class BoundDevice(
    val mac: String,
    val onlineIp: String = "",
    val name: String = "",
    val status: String = "",
    val isCurrentDevice: Boolean = false
)

data class AuthSnapshot(
    val context: PortalContext? = null,
    val isOnline: Boolean = false,
    val onlineAccount: String = "",
    val statusMessage: String = "",
    val lastLoginResult: LoginResult? = null,
    val devices: List<BoundDevice> = emptyList(),
    val credentials: SavedCredentials = SavedCredentials(),
    val debugLines: List<String> = emptyList()
)

internal const val DEFAULT_DOMAIN = "https://rz.hstc.edu.cn"
internal const val DEFAULT_EPORTAL_HOST = "https://rz.hstc.edu.cn:802/eportal"
internal const val DEFAULT_JS_VERSION = "4.X"
