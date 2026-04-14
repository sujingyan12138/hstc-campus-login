package com.hstc.quicklogin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hstc.quicklogin.data.AuthRepository
import com.hstc.quicklogin.data.BoundDevice
import com.hstc.quicklogin.data.LoginResult
import com.hstc.quicklogin.data.PortalContext
import com.hstc.quicklogin.data.SavedCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val context: PortalContext? = null,
    val isOnline: Boolean = false,
    val onlineAccount: String = "",
    val statusMessage: String = "等待操作",
    val lastLoginResult: LoginResult? = null,
    val devices: List<BoundDevice> = emptyList(),
    val credentials: SavedCredentials = SavedCredentials(),
    val debugLines: List<String> = emptyList(),
    val showPortalProbe: Boolean = false,
    val showCasLogin: Boolean = false,
    val casLoginUrl: String = ""
)

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.debugLogs.collect { lines ->
                _uiState.update { it.copy(debugLines = lines) }
            }
        }
        viewModelScope.launch {
            val snapshot = repository.loadSnapshot()
            _uiState.update {
                it.copy(
                    credentials = snapshot.credentials,
                    debugLines = snapshot.debugLines
                )
            }
        }
    }

    fun refreshStatus() = launchTask { repository.refreshStatus(_uiState.value.context) }

    fun login() = launchTask { repository.login(_uiState.value.context) }

    fun startCasLogin() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, statusMessage = "正在准备统一身份认证入口") }
            runCatching { repository.prepareCasLogin(_uiState.value.context) }
                .onSuccess { (snapshot, authorizeUrl) ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            context = snapshot.context,
                            isOnline = snapshot.isOnline,
                            onlineAccount = snapshot.onlineAccount,
                            statusMessage = snapshot.statusMessage,
                            lastLoginResult = snapshot.lastLoginResult,
                            devices = snapshot.devices,
                            credentials = snapshot.credentials,
                            debugLines = snapshot.debugLines,
                            showCasLogin = authorizeUrl != null,
                            casLoginUrl = authorizeUrl.orEmpty()
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            showCasLogin = false,
                            casLoginUrl = "",
                            statusMessage = error.message ?: "统一身份认证入口准备失败"
                        )
                    }
                }
        }
    }

    fun loadDevices() = launchTask { repository.loadDevices(_uiState.value.context) }

    fun startPortalProbe() {
        _uiState.update { it.copy(showPortalProbe = true, statusMessage = "正在打开认证页抓取参数") }
    }

    fun cancelPortalProbe() {
        _uiState.update { it.copy(showPortalProbe = false) }
    }

    fun cancelCasLogin() {
        _uiState.update { it.copy(showCasLogin = false, casLoginUrl = "") }
    }

    fun completeCasLogin() = launchTask(
        before = {
            _uiState.update { it.copy(showCasLogin = false, casLoginUrl = "") }
        }
    ) {
        repository.refreshStatus(_uiState.value.context)
    }

    fun capturePortalUrl(url: String) = launchTask(
        before = {
            _uiState.update { it.copy(showPortalProbe = false) }
        }
    ) {
        repository.capturePortalUrl(url, _uiState.value.context)
    }

    fun saveCredentials(username: String, password: String, autoRetry: Boolean, loggingEnabled: Boolean) {
        viewModelScope.launch {
            val credentials = repository.saveCredentials(
                SavedCredentials(
                    username = username.trim(),
                    password = password,
                    autoRetry = autoRetry,
                    loggingEnabled = loggingEnabled
                )
            )
            _uiState.update { it.copy(credentials = credentials, statusMessage = "配置已保存") }
        }
    }

    fun clearSavedCredentials() {
        viewModelScope.launch {
            val credentials = repository.clearSavedPassword()
            _uiState.update {
                it.copy(credentials = credentials, statusMessage = "已清除保存的账号密码")
            }
        }
    }

    fun unbindAndRetry(device: BoundDevice) = launchTask {
        repository.unbindAndRetry(_uiState.value.context, device)
    }

    fun logoutCurrent() = launchTask {
        repository.logoutCurrent(_uiState.value.context)
    }

    fun addDebugLine(message: String) {
        repository.addDebugLine(message)
    }

    private fun launchTask(
        before: (() -> Unit)? = null,
        block: suspend () -> com.hstc.quicklogin.data.AuthSnapshot
    ) {
        viewModelScope.launch {
            before?.invoke()
            _uiState.update { it.copy(loading = true) }
            runCatching { block() }
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            showPortalProbe = false,
                            showCasLogin = false,
                            casLoginUrl = "",
                            context = snapshot.context,
                            isOnline = snapshot.isOnline,
                            onlineAccount = snapshot.onlineAccount,
                            statusMessage = snapshot.statusMessage,
                            lastLoginResult = snapshot.lastLoginResult,
                            devices = snapshot.devices,
                            credentials = snapshot.credentials,
                            debugLines = snapshot.debugLines
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            showPortalProbe = false,
                            showCasLogin = false,
                            casLoginUrl = "",
                            statusMessage = error.message ?: "发生未知错误"
                        )
                    }
                }
        }
    }
}

class AuthViewModelFactory(
    private val repository: AuthRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AuthViewModel(repository) as T
    }
}
