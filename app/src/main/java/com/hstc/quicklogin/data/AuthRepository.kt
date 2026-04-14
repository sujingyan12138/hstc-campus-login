package com.hstc.quicklogin.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay

class AuthRepository(
    private val credentialStore: CredentialStore,
    private val networkEnvCollector: NetworkEnvCollector,
    private val portalConfigService: PortalConfigService,
    private val campusAuthService: CampusAuthService,
    private val deviceManageService: DeviceManageService,
    private val debugLogStore: DebugLogStore
) {
    val debugLogs: Flow<List<String>> = debugLogStore.lines

    fun addDebugLine(message: String) {
        debugLogStore.add(message)
    }

    suspend fun loadSnapshot(): AuthSnapshot {
        val credentials = credentialStore.load()
        return AuthSnapshot(credentials = credentials, debugLines = debugLogStore.lines.value)
    }

    suspend fun saveCredentials(credentials: SavedCredentials): SavedCredentials {
        credentialStore.save(credentials)
        debugLogStore.add("已更新本地凭据配置 username=${credentials.username}")
        return credentialStore.load()
    }

    suspend fun clearSavedPassword(): SavedCredentials {
        credentialStore.clearCredentials()
        debugLogStore.add("已清除保存的账号密码")
        return credentialStore.load()
    }

    suspend fun collectContext(existing: PortalContext? = null): PortalContext {
        val collected = networkEnvCollector.collect()
        val merged = PortalContext(
            ip = collected.ip.ifBlank { existing?.ip.orEmpty() },
            ipv6 = collected.ipv6.ifBlank { existing?.ipv6.orEmpty() },
            mac = collected.mac.takeUnless { it.isBlank() || it == "000000000000" }
                ?: existing?.mac.orEmpty(),
            vlan = collected.vlan.ifBlank { existing?.vlan.orEmpty() }.ifBlank { "0" },
            wlanAcIp = collected.wlanAcIp.ifBlank { existing?.wlanAcIp.orEmpty() },
            wlanAcName = collected.wlanAcName.ifBlank { existing?.wlanAcName.orEmpty() },
            programIndex = collected.programIndex.ifBlank { existing?.programIndex.orEmpty() },
            pageIndex = collected.pageIndex.ifBlank { existing?.pageIndex.orEmpty() },
            redirectUrl = collected.redirectUrl.ifBlank { existing?.redirectUrl.orEmpty() },
            loginMethod = collected.loginMethod.takeIf { it != 0 } ?: (existing?.loginMethod ?: 0),
            unbindMacEnabled = collected.unbindMacEnabled || (existing?.unbindMacEnabled ?: false),
            jsVersion = collected.jsVersion.ifBlank { existing?.jsVersion.orEmpty() }.ifBlank { DEFAULT_JS_VERSION }
        )
        return portalConfigService.enrich(merged)
    }

    suspend fun capturePortalUrl(urlString: String, existing: PortalContext?): AuthSnapshot {
        val credentials = credentialStore.load()
        val parsed = networkEnvCollector.parsePortalRedirect(urlString)
            ?: return AuthSnapshot(
                context = existing,
                credentials = credentials,
                statusMessage = "当前页面不是校园网认证页，未抓到有效参数",
                lastLoginResult = LoginResult(false, "认证页参数无效"),
                debugLines = debugLogStore.lines.value
            )
        debugLogStore.add("已抓取认证页参数 ip=${parsed.ip.ifBlank { "?" }} mac=${parsed.mac.ifBlank { "?" }}")
        val merged = parsed.copy(
            programIndex = existing?.programIndex.orEmpty(),
            pageIndex = existing?.pageIndex.orEmpty(),
            loginMethod = existing?.loginMethod ?: 0
        )
        val context = portalConfigService.enrich(merged)
        val (online, account) = campusAuthService.checkStatus(context)
        return AuthSnapshot(
            context = context,
            isOnline = online,
            onlineAccount = account,
            statusMessage = "已从认证页抓取参数",
            credentials = credentials,
            debugLines = debugLogStore.lines.value
        )
    }

    suspend fun refreshStatus(existing: PortalContext?): AuthSnapshot {
        val credentials = credentialStore.load()
        val context = collectContext(existing)
        val (online, account) = campusAuthService.checkStatus(context)
        return AuthSnapshot(
            context = context,
            isOnline = online,
            onlineAccount = account,
            statusMessage = if (online) "当前设备已在线" else "当前设备未在线",
            credentials = credentials,
            debugLines = debugLogStore.lines.value
        )
    }

    suspend fun login(existing: PortalContext?): AuthSnapshot {
        val credentials = credentialStore.load()
        if (credentials.username.isBlank() || credentials.password.isBlank()) {
            return AuthSnapshot(
                context = existing,
                credentials = credentials,
                statusMessage = "请先在设置页保存账号和密码",
                lastLoginResult = LoginResult(false, "缺少账号或密码"),
                debugLines = debugLogStore.lines.value
            )
        }
        val context = collectContext(existing)
        if (!context.hasEnoughForLogin) {
            return AuthSnapshot(
                context = context,
                credentials = credentials,
                statusMessage = "不在校园网认证环境中，暂时拿不到登录参数",
                lastLoginResult = LoginResult(false, "缺少校园网环境参数"),
                debugLines = debugLogStore.lines.value
            )
        }
        val (online, account) = campusAuthService.checkStatus(context)
        if (online) {
            return AuthSnapshot(
                context = context,
                isOnline = true,
                onlineAccount = account,
                statusMessage = "当前设备已在线，无需重复登录",
                credentials = credentials,
                lastLoginResult = LoginResult(true, "已经在线"),
                debugLines = debugLogStore.lines.value
            )
        }
        val loginResult = campusAuthService.login(credentials, context)
        val devices = if (loginResult.requiresDeviceAction && context.unbindMacEnabled) {
            deviceManageService.loadBoundDevices(credentials, context)
        } else {
            emptyList()
        }
        val (latestOnline, latestAccount) = if (loginResult.success) {
            campusAuthService.checkStatus(context)
        } else {
            false to ""
        }
        return AuthSnapshot(
            context = context,
            isOnline = latestOnline,
            onlineAccount = latestAccount,
            statusMessage = loginResult.message,
            lastLoginResult = loginResult,
            devices = devices,
            credentials = credentials,
            debugLines = debugLogStore.lines.value
        )
    }

    suspend fun loadDevices(existing: PortalContext?): AuthSnapshot {
        val credentials = credentialStore.load()
        val context = collectContext(existing)
        val inferredContext = context.copy(
            mac = context.mac.takeUnless { it.isBlank() || it == "000000000000" }
                ?: existing?.mac.orEmpty()
        )
        val (online, account) = campusAuthService.checkStatus(inferredContext)
        val devices = if (credentials.username.isBlank()) {
            emptyList()
        } else {
            deviceManageService.loadBoundDevices(credentials, inferredContext)
        }
        val currentMacFromDevices = devices.firstOrNull {
            it.isCurrentDevice || (inferredContext.ip.isNotBlank() && it.onlineIp == inferredContext.ip)
        }?.mac.orEmpty()
        val finalContext = inferredContext.copy(
            mac = inferredContext.mac.takeUnless { it.isBlank() || it == "000000000000" }
                ?: currentMacFromDevices
        )
        return AuthSnapshot(
            context = finalContext,
            isOnline = online,
            onlineAccount = account,
            credentials = credentials,
            devices = devices,
            statusMessage = if (devices.isEmpty()) "没有可展示的绑定设备" else "已获取绑定设备列表",
            debugLines = debugLogStore.lines.value
        )
    }

    suspend fun unbindAndRetry(existing: PortalContext?, device: BoundDevice): AuthSnapshot {
        val credentials = credentialStore.load()
        val context = collectContext(existing)
        val unbindResult = deviceManageService.unbindDevice(credentials, context, device)
        if (!unbindResult.success) {
            return AuthSnapshot(
                context = context,
                credentials = credentials,
                devices = deviceManageService.loadBoundDevices(credentials, context),
                statusMessage = unbindResult.message,
                lastLoginResult = unbindResult,
                debugLines = debugLogStore.lines.value
            )
        }
        val retry = if (credentials.autoRetry) {
            campusAuthService.login(credentials, context)
        } else {
            LoginResult(true, "解绑成功，未自动重试登录")
        }
        val devices = deviceManageService.loadBoundDevices(credentials, context)
        val (online, account) = if (credentials.autoRetry && retry.success) {
            campusAuthService.checkStatus(context)
        } else {
            false to ""
        }
        return AuthSnapshot(
            context = context,
            isOnline = online,
            onlineAccount = account,
            statusMessage = retry.message,
            lastLoginResult = retry,
            devices = devices,
            credentials = credentials,
            debugLines = debugLogStore.lines.value
        )
    }

    suspend fun prepareCasLogin(existing: PortalContext?): Pair<AuthSnapshot, String?> {
        val credentials = credentialStore.load()
        val context = if (existing?.hasEnoughForCas == true) {
            debugLogStore.add("统一认证沿用已抓取的校园网参数 ip=${existing.ip} mac=${existing.mac}")
            portalConfigService.enrich(existing)
        } else {
            collectContext(existing)
        }
        val onlineStatus = campusAuthService.checkStatus(context)
        if (onlineStatus.first) {
            return AuthSnapshot(
                context = context,
                isOnline = true,
                onlineAccount = onlineStatus.second,
                statusMessage = "当前设备已在线，无需再次统一认证",
                credentials = credentials,
                lastLoginResult = LoginResult(true, "已经在线"),
                debugLines = debugLogStore.lines.value
            ) to null
        }
        if (!context.hasEnoughForCas) {
            return AuthSnapshot(
                context = context,
                credentials = credentials,
                statusMessage = "当前没有拿到完整的校园网认证参数，请先在未登录状态下抓取认证页",
                lastLoginResult = LoginResult(false, "缺少统一身份认证参数"),
                debugLines = debugLogStore.lines.value
            ) to null
        }
        val authorizeUrl = campusAuthService.createCasAuthorizeUrl(context)
        return AuthSnapshot(
            context = context,
            credentials = credentials,
            statusMessage = "统一身份认证页面已打开，请在页面内完成登录",
            lastLoginResult = null,
            debugLines = debugLogStore.lines.value
        ) to authorizeUrl
    }

    suspend fun logoutCurrent(existing: PortalContext?): AuthSnapshot {
        val credentials = credentialStore.load()
        val context = collectContext(existing)
        val devices = if (credentials.username.isBlank()) {
            emptyList()
        } else {
            deviceManageService.loadBoundDevices(credentials, context)
        }
        debugLogStore.add("顶部卡片触发当前设备注销")
        val listedCurrent = devices.firstOrNull {
            it.isCurrentDevice || (it.onlineIp.isNotBlank() && it.onlineIp == context.ip)
        }
        val currentMac = listedCurrent?.mac?.takeUnless { it.isBlank() }
            ?: context.mac.takeUnless { it.isBlank() || it == "000000000000" }
            ?: devices.firstOrNull { it.onlineIp == context.ip }?.mac.orEmpty()
        if (listedCurrent != null) {
            debugLogStore.add("列表命中当前设备 mac=${listedCurrent.mac} ip=${listedCurrent.onlineIp}")
        } else {
            debugLogStore.add("列表未命中当前设备，回退本地推断 ip=${context.ip} mac=${currentMac.ifBlank { "?" }}")
        }
        val currentContext = context.copy(mac = currentMac.ifBlank { context.mac })
        if (currentContext.mac.isNotBlank()) {
            debugLogStore.add("当前设备注销使用 mac=${currentContext.mac}")
        }
        val drcomLogoutResult = campusAuthService.drcomLogout(currentContext)
        val strongLogoutResult = if (drcomLogoutResult.success) {
            drcomLogoutResult
        } else if (credentials.username.isNotBlank() && currentMac.isNotBlank()) {
            debugLogStore.add("drcom 注销未成功，尝试当前设备强制下线 mac=$currentMac")
            deviceManageService.logoutCurrentDevice(credentials, currentContext, currentMac)
        } else {
            LoginResult(false, "缺少当前设备 MAC，无法执行强制下线")
        }
        val logoutResult = if (strongLogoutResult.success) {
            strongLogoutResult
        } else {
            debugLogStore.add("强制下线未成功，回退到 CAS 注销")
            campusAuthService.logoutCurrent(currentContext)
        }
        var online = false
        var account = ""
        for (index in 1..4) {
            val (latestOnline, latestAccount) = campusAuthService.checkStatus(context)
            online = latestOnline
            account = latestAccount
            debugLogStore.add("注销后状态轮询 $index/4 online=$online")
            if (!online) {
                break
            }
            delay(800)
        }
        val refreshedDevices = if (credentials.username.isBlank()) {
            emptyList()
        } else {
            deviceManageService.loadBoundDevices(credentials, currentContext)
        }
        val currentStillListed = refreshedDevices.any {
            sanitizeMac(it.mac) == sanitizeMac(currentContext.mac) ||
                (currentContext.ip.isNotBlank() && it.onlineIp == currentContext.ip)
        }
        debugLogStore.add("注销后设备列表仍包含当前设备=$currentStillListed")
        val currentGone = !currentStillListed
        val finalOnline = if (logoutResult.success && currentGone) false else online
        val finalAccount = if (finalOnline) account else ""
        val finalMessage = when {
            logoutResult.success && currentGone -> "当前设备已下线"
            logoutResult.success && !online -> "当前设备已下线"
            logoutResult.success -> "已提交当前设备下线请求，校园网状态同步可能稍有延迟"
            else -> logoutResult.message
        }
        return AuthSnapshot(
            context = currentContext,
            isOnline = finalOnline,
            onlineAccount = finalAccount,
            statusMessage = finalMessage,
            lastLoginResult = logoutResult,
            devices = refreshedDevices,
            credentials = credentials,
            debugLines = debugLogStore.lines.value
        )
    }
}
