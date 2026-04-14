package com.hstc.quicklogin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.hstc.quicklogin.data.BoundDevice

private enum class AppTab(val title: String) {
    Home("首页"),
    Devices("设备"),
    Settings("设置"),
    Debug("调试")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HstcQuickLoginAppScreen(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var currentTab by rememberSaveable { mutableStateOf(AppTab.Home) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (uiState.showPortalProbe) {
        PortalProbeDialog(
            onCaptured = viewModel::capturePortalUrl,
            onDismiss = viewModel::cancelPortalProbe
        )
    }
    if (uiState.showCasLogin && uiState.casLoginUrl.isNotBlank()) {
                    CasLoginDialog(
                        loginUrl = uiState.casLoginUrl,
                        username = uiState.credentials.username,
                        password = uiState.credentials.password,
                        onDebug = viewModel::addDebugLine,
                        onSuccess = viewModel::completeCasLogin,
                        onDismiss = viewModel::cancelCasLogin
                    )
    }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage.trim()
        if (message.isNotEmpty() && message != "等待操作") {
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("HSTC 校园网一键重登") }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                NavigationBarItem(
                    selected = currentTab == AppTab.Home,
                    onClick = { currentTab = AppTab.Home },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text(AppTab.Home.title) }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.Devices,
                    onClick = {
                        currentTab = AppTab.Devices
                        viewModel.loadDevices()
                    },
                    icon = { Icon(Icons.Outlined.List, contentDescription = null) },
                    label = { Text(AppTab.Devices.title) }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.Settings,
                    onClick = { currentTab = AppTab.Settings },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(AppTab.Settings.title) }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.Debug,
                    onClick = { currentTab = AppTab.Debug },
                    icon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                    label = { Text(AppTab.Debug.title) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("正在处理请求，请稍候")
                }
            }
            when (currentTab) {
                AppTab.Home -> HomeTab(
                    state = uiState,
                    onRefresh = viewModel::refreshStatus,
                    onStartCasLogin = viewModel::startCasLogin,
                    onLogin = viewModel::login,
                    onProbe = viewModel::startPortalProbe,
                    onShowDevices = {
                        currentTab = AppTab.Devices
                        viewModel.loadDevices()
                    }
                )
                AppTab.Devices -> DeviceTab(
                    state = uiState,
                    devices = uiState.devices.filterNot { it.isCurrentDevice },
                    onRefresh = viewModel::loadDevices,
                    onUnbind = viewModel::unbindAndRetry,
                    onLogoutCurrent = viewModel::logoutCurrent
                )
                AppTab.Settings -> SettingsTab(
                    state = uiState,
                    onSave = viewModel::saveCredentials,
                    onClear = viewModel::clearSavedCredentials
                )
                AppTab.Debug -> DebugTab(
                    lines = uiState.debugLines,
                    rawResponse = uiState.lastLoginResult?.rawResponse.orEmpty()
                )
            }
        }
    }
}

@Composable
private fun HomeTab(
    state: AuthUiState,
    onRefresh: () -> Unit,
    onStartCasLogin: () -> Unit,
    onLogin: () -> Unit,
    onProbe: () -> Unit,
    onShowDevices: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatusCard(
                title = "网络状态",
                lines = listOf(
                    "状态: ${if (state.isOnline) "已在线" else "未在线"}",
                    "账号: ${state.onlineAccount.ifBlank { "未知" }}",
                    "消息: ${state.statusMessage}"
                )
            )
        }
        item {
            StatusCard(
                title = "当前环境",
                lines = listOf(
                    "IP: ${state.context?.ip.orEmpty().ifBlank { "未获取" }}",
                    "MAC: ${state.context?.mac.orEmpty().ifBlank { "未获取" }}",
                    "AC IP: ${state.context?.wlanAcIp.orEmpty().ifBlank { "未获取" }}",
                    "AC 名称: ${state.context?.wlanAcName.orEmpty().ifBlank { "未获取" }}",
                    "Program/Page: ${state.context?.programIndex.orEmpty()}/${state.context?.pageIndex.orEmpty()}"
                )
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("快捷操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("采集环境并检测状态") }
                    Button(onClick = onStartCasLogin, modifier = Modifier.fillMaxWidth()) {
                        Text("统一身份认证登录")
                    }
                    OutlinedButton(onClick = onProbe, modifier = Modifier.fillMaxWidth()) {
                        Text("抓取认证页参数（仅未登录时）")
                    }
                    OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                        Text("非智慧韩园账号直登")
                    }
                    if (state.lastLoginResult?.requiresDeviceAction == true) {
                        OutlinedButton(onClick = onShowDevices, modifier = Modifier.fillMaxWidth()) {
                            Text("登录失败，查看绑定设备")
                        }
                    }
                }
            }
        }
        item {
            StatusCard(
                title = "最后一次登录结果",
                lines = listOf(
                    "成功: ${state.lastLoginResult?.success ?: false}",
                    "代码: ${state.lastLoginResult?.code.orEmpty().ifBlank { "-" }}",
                    "消息: ${state.lastLoginResult?.message.orEmpty().ifBlank { "暂无" }}"
                )
            )
        }
    }
}

@Composable
private fun DeviceTab(
    state: AuthUiState,
    devices: List<BoundDevice>,
    onRefresh: () -> Unit,
    onUnbind: (BoundDevice) -> Unit,
    onLogoutCurrent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onRefresh) { Text("刷新设备") }
        Text(
            text = if (devices.isEmpty() && !state.isOnline) {
                "当前没有加载到可展示的绑定设备。"
            } else {
                "已展示 ${devices.size + if (state.isOnline) 1 else 0} 台设备${if (state.isOnline) "（含当前设备）" else ""}"
            },
            style = MaterialTheme.typography.bodyMedium
        )
        if (state.isOnline) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("当前在线设备", fontWeight = FontWeight.SemiBold)
                    Text("当前 IP: ${state.context?.ip.orEmpty().ifBlank { "未获取" }}")
                    Text("当前 MAC: ${state.context?.mac.orEmpty().ifBlank { "未获取" }}")
                    OutlinedButton(onClick = onLogoutCurrent) {
                        Text("注销当前在线设备")
                    }
                }
            }
        }
        if (devices.isEmpty()) {
            Text("暂无已绑定设备，或者当前账号还没有加载出设备列表。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(devices) { device ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(device.mac, fontWeight = FontWeight.SemiBold)
                            if (device.name.isNotBlank()) {
                                Text("设备名: ${device.name}")
                            }
                            Text(
                                if (device.onlineIp.isNotBlank()) {
                                    "在线 IP: ${device.onlineIp}"
                                } else {
                                    "在线 IP: 未知/当前未在线"
                                }
                            )
                            Text(
                                when {
                                    device.isCurrentDevice -> "当前设备"
                                    device.status.isNotBlank() -> "状态: ${device.status}"
                                    else -> "其他已绑定设备"
                                }
                            )
                            OutlinedButton(
                                onClick = {
                                    if (device.isCurrentDevice) onLogoutCurrent() else onUnbind(device)
                                }
                            ) {
                                Text(if (device.isCurrentDevice) "注销当前设备" else "解绑并重试")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    state: AuthUiState,
    onSave: (String, String, Boolean, Boolean) -> Unit,
    onClear: () -> Unit
) {
    var username by remember(state.credentials.username) { mutableStateOf(state.credentials.username) }
    var password by remember(state.credentials.password) { mutableStateOf(state.credentials.password) }
    var autoRetry by remember(state.credentials.autoRetry) { mutableStateOf(state.credentials.autoRetry) }
    var loggingEnabled by remember(state.credentials.loggingEnabled) { mutableStateOf(state.credentials.loggingEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("账号设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("当前状态", fontWeight = FontWeight.SemiBold)
                Text(state.statusMessage.ifBlank { "等待操作" })
            }
        }
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("账号") },
            singleLine = true
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            singleLine = true
        )
        SwitchRow("解绑后自动重试", autoRetry) { autoRetry = it }
        SwitchRow("启用详细日志", loggingEnabled) { loggingEnabled = it }
        Button(
            onClick = { onSave(username, password, autoRetry, loggingEnabled) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading
        ) {
            Text(if (state.loading) "正在保存..." else "保存到本地加密存储")
        }
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading
        ) {
            Text("清除保存的账号密码")
        }
        Text("密码会存进 Android Keystore + EncryptedSharedPreferences，调试页默认会脱敏。")
    }
}

@Composable
private fun DebugTab(lines: List<String>, rawResponse: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("调试日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lines.isEmpty()) {
                    Text("暂无日志")
                } else {
                    lines.takeLast(120).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text("最后一次响应", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = rawResponse.ifBlank { "暂无原始响应" },
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatusCard(title: String, lines: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            lines.forEach { Text(it) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PortalProbeDialog(
    onCaptured: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        title = { Text("抓取认证页参数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("会临时打开内置页面访问联网探测地址，抓到校园网认证页跳转后自动关闭。")
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString().orEmpty()
                                    if (url.contains("rz.hstc.edu.cn") &&
                                        url.contains("wlanuserip=") &&
                                        url.contains("usermac=")
                                    ) {
                                        onCaptured(url)
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    val current = url.orEmpty()
                                    if (current.contains("rz.hstc.edu.cn") &&
                                        current.contains("wlanuserip=") &&
                                        current.contains("usermac=")
                                    ) {
                                        onCaptured(current)
                                    }
                                }
                            }
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            clearCache(true)
                            clearHistory()
                            clearFormData()
                            loadUrl("http://www.msftconnecttest.com/redirect?ts=${System.currentTimeMillis()}")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                )
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CasLoginDialog(
    loginUrl: String,
    username: String,
    password: String,
    onDebug: (String) -> Unit,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        title = { Text("统一身份认证登录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("请在内置页面里完成学校统一身份认证，成功后会自动刷新状态。")
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            clearCache(true)
                            clearHistory()
                            clearFormData()
                            webViewClient = object : WebViewClient() {
                                private fun evalWithLog(
                                    view: WebView?,
                                    label: String,
                                    delayMs: Long,
                                    script: String
                                ) {
                                    view?.postDelayed({
                                        view.evaluateJavascript(script) { result ->
                                            onDebug("$label => ${result?.trim('"').orEmpty().ifBlank { "null" }}")
                                        }
                                    }, delayMs)
                                }

                                private fun tryPortalButtonClick(view: WebView?, url: String) {
                                    if (!url.contains("rz.hstc.edu.cn", ignoreCase = true)) return
                                    val script = """
                                        (function() {
                                            if (window.custom && typeof window.custom.identity_login === 'function') {
                                                setTimeout(function() { window.custom.identity_login(); }, 150);
                                                return 'identity_login()';
                                            }
                                            var btn = document.querySelector('#cas_login_1');
                                            if (!btn) {
                                                var candidates = Array.prototype.slice.call(document.querySelectorAll('input,button,span,a,div'));
                                                btn = candidates.find(function(el) {
                                                    return /统一身份认证/.test((el.innerText || el.value || '').trim());
                                                }) || null;
                                            }
                                            if (btn) {
                                                setTimeout(function() { btn.click(); }, 250);
                                                return 'clicked';
                                            }
                                            return 'missing';
                                        })();
                                    """.trimIndent()
                                    evalWithLog(view, "统一认证按钮尝试", 250, script)
                                    evalWithLog(view, "统一认证按钮重试1", 1200, script)
                                    evalWithLog(view, "统一认证按钮重试2", 2500, script)
                                }

                                private fun tryAutoFill(view: WebView?, url: String) {
                                    if (username.isBlank() || password.isBlank()) return
                                    if (!url.contains("hscas.hstc.edu.cn", ignoreCase = true)) return
                                    val script = """
                                        (function() {
                                            function setNativeValue(el, value) {
                                                var descriptor = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value');
                                                if (descriptor && descriptor.set) {
                                                    descriptor.set.call(el, value);
                                                } else {
                                                    el.value = value;
                                                }
                                            }
                                            function findInput(candidates) {
                                                for (var i = 0; i < candidates.length; i++) {
                                                    var el = document.querySelector(candidates[i]);
                                                    if (el) return el;
                                                }
                                                return null;
                                            }
                                            var user = findInput([
                                                'input[name=username]',
                                                'input[name=userName]',
                                                'input[name=mobileUsername]',
                                                'input[id=username]',
                                                'input[placeholder*="学号"]',
                                                'input[placeholder*="工号"]',
                                                'input[type=text]',
                                                'input[type=tel]'
                                            ]);
                                            var pass = findInput([
                                                'input[name=password]',
                                                'input[id=password]',
                                                'input[placeholder*="密码"]',
                                                'input[type=password]'
                                            ]);
                                            if (!user || !pass) return 'missing';
                                            user.focus();
                                            setNativeValue(user, ${org.json.JSONObject.quote(username)});
                                            user.dispatchEvent(new Event('input', { bubbles: true }));
                                            user.dispatchEvent(new Event('change', { bubbles: true }));
                                            user.dispatchEvent(new Event('blur', { bubbles: true }));
                                            pass.focus();
                                            setNativeValue(pass, ${org.json.JSONObject.quote(password)});
                                            pass.dispatchEvent(new Event('input', { bubbles: true }));
                                            pass.dispatchEvent(new Event('change', { bubbles: true }));
                                            pass.dispatchEvent(new Event('blur', { bubbles: true }));
                                            var button = findInput([
                                                'button[type=submit]',
                                                'input[type=submit]',
                                                '#login',
                                                '.login_btn',
                                                'button.login',
                                                '.login-btn',
                                                '.btn-login',
                                                '.submit'
                                            ]);
                                            if (!button) {
                                                var allButtons = Array.prototype.slice.call(document.querySelectorAll('button,input[type=button],span,div,a'));
                                                button = allButtons.find(function(el) {
                                                    return /登录/.test((el.innerText || el.value || '').trim());
                                                }) || null;
                                            }
                                            if (button) {
                                                setTimeout(function() { button.click(); }, 300);
                                                return 'submitted';
                                            }
                                            if (pass.form) {
                                                setTimeout(function() { pass.form.submit(); }, 300);
                                                return 'form_submitted';
                                            }
                                            return 'filled';
                                        })();
                                    """.trimIndent()
                                    evalWithLog(view, "统一认证自动填表", 350, script)
                                    evalWithLog(view, "统一认证自动填表重试1", 1400, script)
                                    evalWithLog(view, "统一认证自动填表重试2", 2800, script)
                                }

                                private fun isSuccessUrl(url: String): Boolean {
                                    return url.contains("/3.htm") ||
                                        url.contains("login_success", ignoreCase = true)
                                }

                                private fun maybeFinish(url: String): Boolean {
                                    if (!isSuccessUrl(url)) return false
                                    onDebug("统一认证成功页命中: $url")
                                    Toast.makeText(context, "统一认证完成，正在刷新状态", Toast.LENGTH_SHORT).show()
                                    onSuccess()
                                    return true
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    return maybeFinish(request?.url?.toString().orEmpty())
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    val currentUrl = url.orEmpty()
                                    onDebug("统一认证页面完成加载: $currentUrl")
                                    if (!maybeFinish(currentUrl)) {
                                        tryPortalButtonClick(view, currentUrl)
                                        tryAutoFill(view, currentUrl)
                                    }
                                }
                            }
                            loadUrl(loginUrl)
                        }
                    },
                    update = { webView ->
                        if (webView.url != loginUrl) {
                            webView.loadUrl(loginUrl)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                )
            }
        }
    )
}
