package com.hstc.quicklogin.di

import android.content.Context
import com.hstc.quicklogin.BuildConfig
import com.hstc.quicklogin.data.AuthRepository
import com.hstc.quicklogin.data.CampusAuthService
import com.hstc.quicklogin.data.CredentialStore
import com.hstc.quicklogin.data.DebugLogStore
import com.hstc.quicklogin.data.DeviceManageService
import com.hstc.quicklogin.data.NetworkEnvCollector
import com.hstc.quicklogin.data.PortalConfigService
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val debugLogStore = DebugLogStore().apply {
        add("应用启动 version=${BuildConfig.VERSION_NAME}")
    }
    private val credentialStore = CredentialStore(context)
    private val networkEnvCollector = NetworkEnvCollector(context, okHttpClient, debugLogStore)
    private val portalConfigService = PortalConfigService(okHttpClient, debugLogStore)
    private val campusAuthService = CampusAuthService(okHttpClient, debugLogStore)
    private val deviceManageService = DeviceManageService(okHttpClient, debugLogStore)

    val authRepository = AuthRepository(
        credentialStore = credentialStore,
        networkEnvCollector = networkEnvCollector,
        portalConfigService = portalConfigService,
        campusAuthService = campusAuthService,
        deviceManageService = deviceManageService,
        debugLogStore = debugLogStore
    )
}
