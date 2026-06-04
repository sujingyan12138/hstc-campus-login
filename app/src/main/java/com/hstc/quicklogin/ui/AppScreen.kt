package com.hstc.quicklogin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiFind
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.hstc.quicklogin.data.BoundDevice
import java.net.URLDecoder

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
            onCapturedContent = viewModel::capturePortalContent,
            onDebug = viewModel::addDebugLine,
            username = uiState.credentials.username,
            password = uiState.credentials.password,
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "HSTC",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "校园网快速连接",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == AppTab.Home,
                    onClick = { currentTab = AppTab.Home },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text(AppTab.Home.title) },
                    colors = refinedNavColors()
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.Devices,
                    onClick = {
                        currentTab = AppTab.Devices
                        viewModel.loadDevices()
                    },
                    icon = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null) },
                    label = { Text(AppTab.Devices.title) },
                    colors = refinedNavColors()
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.Settings,
                    onClick = { currentTab = AppTab.Settings },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(AppTab.Settings.title) },
                    colors = refinedNavColors()
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.Debug,
                    onClick = { currentTab = AppTab.Debug },
                    icon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                    label = { Text(AppTab.Debug.title) },
                    colors = refinedNavColors()
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBrush())
                .padding(padding)
        ) {
            if (uiState.loading) {
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("正在处理请求", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ConnectionHero(
                online = state.isOnline,
                account = state.onlineAccount.ifBlank { "未知账号" },
                message = state.statusMessage.ifBlank { "等待操作" }
            )
        }
        item {
            EnvironmentPanel(
                entries = listOf(
                    Metric("IP", state.context?.ip.orEmpty().ifBlank { "未获取" }, Icons.Outlined.Wifi),
                    Metric("MAC", state.context?.mac.orEmpty().ifBlank { "未获取" }, Icons.Outlined.Smartphone),
                    Metric("AC IP", state.context?.wlanAcIp.orEmpty().ifBlank { "未获取" }, Icons.Outlined.Router),
                    Metric("Program", state.context?.programIndex.orEmpty().ifBlank { "-" }, Icons.Outlined.Link)
                )
            )
        }
        item {
            ActionPanel {
                PrimaryAction(
                    text = "统一身份认证登录",
                    icon = Icons.Outlined.Key,
                    onClick = onStartCasLogin
                )
                SoftAction(
                    text = "采集环境并检测状态",
                    icon = Icons.Outlined.Refresh,
                    onClick = onRefresh
                )
                SoftAction(
                    text = "抓取认证页参数",
                    icon = Icons.Outlined.WifiFind,
                    onClick = onProbe
                )
                SoftAction(
                    text = "非智慧韩园账号直登",
                    icon = Icons.Outlined.Link,
                    onClick = onLogin
                )
                if (state.lastLoginResult?.requiresDeviceAction == true) {
                    SoftAction(
                        text = "查看绑定设备",
                        icon = Icons.AutoMirrored.Outlined.List,
                        onClick = onShowDevices
                    )
                }
            }
        }
        item {
            ResultPanel(
                success = state.lastLoginResult?.success,
                code = state.lastLoginResult?.code.orEmpty().ifBlank { "-" },
                message = state.lastLoginResult?.message.orEmpty().ifBlank { "暂无登录结果" }
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "设备",
            subtitle = if (devices.isEmpty() && !state.isOnline) {
                "暂无可展示的绑定设备"
            } else {
                "已展示 ${devices.size + if (state.isOnline) 1 else 0} 台设备"
            },
            actionText = "刷新",
            action = onRefresh
        )
        if (state.isOnline) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(online = true)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("当前在线设备", fontWeight = FontWeight.SemiBold)
                            Text(
                                state.context?.ip.orEmpty().ifBlank { "未获取 IP" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        "MAC ${state.context?.mac.orEmpty().ifBlank { "未获取" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SoftAction("注销当前在线设备", Icons.Outlined.Logout, onLogoutCurrent)
                }
            }
        }
        if (devices.isEmpty()) {
            EmptyState("暂无已绑定设备")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(devices) { device ->
                    DeviceRow(
                        device = device,
                        onClick = {
                            if (device.isCurrentDevice) onLogoutCurrent() else onUnbind(device)
                        }
                    )
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
        SectionHeader("设置", "账号和本地偏好")
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Text("当前状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.statusMessage.ifBlank { "等待操作" }, fontWeight = FontWeight.SemiBold)
        }
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("账号") },
            singleLine = true,
            shape = RoundedShape,
            colors = textFieldColors()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            singleLine = true,
            shape = RoundedShape,
            visualTransformation = PasswordVisualTransformation(),
            colors = textFieldColors()
        )
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            SwitchRow("解绑后自动重试", autoRetry) { autoRetry = it }
            SwitchRow("启用详细日志", loggingEnabled) { loggingEnabled = it }
        }
        PrimaryAction(
            text = if (state.loading) "正在保存..." else "保存到本地加密存储",
            icon = Icons.Outlined.Key,
            onClick = { onSave(username, password, autoRetry, loggingEnabled) },
            enabled = !state.loading
        )
        SoftAction(
            text = "清除保存的账号密码",
            icon = Icons.Outlined.Logout,
            onClick = onClear,
            enabled = !state.loading
        )
        Text(
            "密码会存进 Android Keystore + EncryptedSharedPreferences，调试页默认会脱敏。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DebugTab(lines: List<String>, rawResponse: String) {
    val visibleLines = lines.takeLast(120)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("调试", "最近 ${visibleLines.size} 条日志")
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            ScrollableLogPanel(visibleLines)
        }
        SectionHeader("最后一次响应", "原始返回内容")
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            ScrollableTextPanel(
                text = rawResponse.ifBlank { "暂无原始响应" },
                height = 220.dp
            )
        }
    }
}

@Composable
private fun ScrollableLogPanel(lines: List<String>) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
    ) {
        if (lines.isEmpty()) {
            EmptyState("暂无日志")
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lines.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            ScrollThumb(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun ScrollableTextPanel(text: String, height: Dp) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = 14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )
        ScrollThumb(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun ScrollThumb(scrollState: ScrollState, modifier: Modifier = Modifier) {
    if (scrollState.maxValue <= 0) return

    BoxWithConstraints(
        modifier = modifier.width(4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        val thumbHeight = if (maxHeight < 72.dp) maxHeight else 72.dp
        val travel = maxHeight - thumbHeight
        val progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        val thumbOffset = travel * progress

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f))
        )
        Box(
            modifier = Modifier
                .offset(y = thumbOffset)
                .height(thumbHeight)
                .width(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.58f))
        )
    }
}

@Composable
private fun ConnectionHero(online: Boolean, account: String, message: String) {
    val accent = if (online) Color(0xFF2EAD6B) else Color(0xFFFF9F0A)
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.13f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
                    )
                ),
                RoundedShape
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (online) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (online) "已在线" else "未在线",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(account, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EnvironmentPanel(entries: List<Metric>) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("当前环境")
        entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { metric ->
                    MetricTile(metric, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActionPanel(content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("快捷操作")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun ResultPanel(success: Boolean?, code: String, message: String) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(online = success == true)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("最后一次登录结果", fontWeight = FontWeight.SemiBold)
                Text(
                    "代码 $code",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeviceRow(device: BoundDevice, onClick: () -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(device.mac, fontWeight = FontWeight.SemiBold)
                Text(
                    device.onlineIp.takeIf { it.isNotBlank() } ?: "当前未在线",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when {
                        device.isCurrentDevice -> "当前设备"
                        device.status.isNotBlank() -> device.status
                        else -> "其他已绑定设备"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SoftAction(if (device.isCurrentDevice) "注销当前设备" else "解绑并重试", Icons.Outlined.Logout, onClick)
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    actionText: String? = null,
    action: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionText != null && action != null) {
            OutlinedButton(
                onClick = action,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun MetricTile(metric: Metric, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedShape)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(metric.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(metric.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(metric.value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PrimaryAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SoftAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f),
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            )
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedShape),
        shape = RoundedShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun StatusDot(online: Boolean) {
    val color = if (online) Color(0xFF2EAD6B) else Color(0xFFFF9F0A)
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun EmptyState(text: String) {
    Text(
        text,
        modifier = Modifier.padding(8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium
    )
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

@Composable
private fun pageBrush(): Brush = Brush.verticalGradient(
    listOf(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        MaterialTheme.colorScheme.background
    )
)

@Composable
private fun refinedNavColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun textFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f),
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)

private data class Metric(
    val label: String,
    val value: String,
    val icon: ImageVector
)

private val RoundedShape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PortalProbeDialog(
    onCaptured: (String) -> Unit,
    onCapturedContent: (String, String) -> Unit,
    onDebug: (String) -> Unit,
    username: String,
    password: String,
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
                Text("会临时打开内置页面访问联网探测地址，必要时自动进入统一身份认证并从回调里抓取参数。")
                AndroidView(
                    factory = { context ->
                        val probeUrls = listOf(
                            "http://www.gstatic.com/generate_204" to true,
                            "http://connectivitycheck.gstatic.com/generate_204" to true,
                            "http://rz.hstc.edu.cn/" to false,
                            "https://rz.hstc.edu.cn/" to false,
                            "http://rz.hstc.edu.cn/eportal/" to false,
                            "http://192.168.2.34/" to false,
                            "http://www.msftconnecttest.com/redirect" to true,
                            "http://connect.rom.miui.com/generate_204" to true,
                            "http://captive.apple.com/hotspot-detect.html" to true,
                            "http://neverssl.com/" to true,
                            "http://1.1.1.1/" to true
                        )
                        var captured = false
                        var nextProbeIndex = 0

                        fun looksLikePortalParamPage(url: String): Boolean {
                            val normalized = url.lowercase()
                            val hasPortalParams = (
                                normalized.contains("wlanuserip=") ||
                                    normalized.contains("wlan_user_ip=") ||
                                    normalized.contains("userip=") ||
                                    normalized.contains("v4ip=")
                                ) &&
                                (
                                    normalized.contains("usermac=") ||
                                        normalized.contains("wlan_user_mac=") ||
                                        normalized.contains("wlanacip=") ||
                                        normalized.contains("wlan_ac_ip=")
                                    )
                            val hasCasState = normalized.contains("hscas.hstc.edu.cn") &&
                                normalized.contains("service=") &&
                                normalized.contains("state=")
                            val hasDirectCasState = normalized.contains("/eportal/portal/cas/") &&
                                normalized.contains("state=")
                            return hasPortalParams || hasCasState || hasDirectCasState
                        }

                        fun looksLikePortalContent(text: String): Boolean {
                            val normalized = text.lowercase()
                            val hasPortalParams = (
                                normalized.contains("wlanuserip") ||
                                    normalized.contains("wlan_user_ip") ||
                                    normalized.contains("usermac") ||
                                    normalized.contains("wlan_user_mac") ||
                                    normalized.contains("wlanacip") ||
                                    normalized.contains("wlan_ac_ip")
                                )
                            val hasCasState = normalized.contains("state=") &&
                                (
                                    normalized.contains("/eportal/portal/cas") ||
                                        normalized.contains("hscas.hstc.edu.cn") ||
                                        normalized.contains("rz.hstc.edu.cn")
                                    )
                            return hasPortalParams || hasCasState
                        }

                        fun captureOnce(url: String): Boolean {
                            if (captured || !looksLikePortalParamPage(url)) return false
                            captured = true
                            onCaptured(url)
                            return true
                        }

                        fun capturePageContent(view: WebView?, baseUrl: String) {
                            if (captured || view == null) return
                            view.evaluateJavascript(
                                "encodeURIComponent(document.documentElement ? document.documentElement.outerHTML : '')"
                            ) { result ->
                                if (captured) return@evaluateJavascript
                                val encoded = result
                                    ?.trim()
                                    ?.removeSurrounding("\"")
                                    .orEmpty()
                                val html = runCatching {
                                    URLDecoder.decode(encoded, "UTF-8")
                                }.getOrDefault("")
                                if (html.isNotBlank() && looksLikePortalContent(html)) {
                                    captured = true
                                    onCapturedContent(html, baseUrl)
                                }
                            }
                        }

                        fun WebView.loadNextProbe() {
                            if (captured || nextProbeIndex >= probeUrls.size) return
                            val (baseUrl, addTimestamp) = probeUrls[nextProbeIndex++]
                            val target = if (addTimestamp) {
                                val separator = if (baseUrl.contains("?")) "&" else "?"
                                "$baseUrl${separator}hstc_probe_ts=${System.currentTimeMillis()}"
                            } else {
                                baseUrl
                            }
                            onDebug("认证页探测 ${nextProbeIndex}/${probeUrls.size}: $target")
                            loadUrl(target)
                        }

                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                private fun tryPortalButtonClick(view: WebView?) {
                                    val script = """
                                        (function() {
                                            if (window.custom && typeof window.custom.identity_login === 'function') {
                                                setTimeout(function() { window.custom.identity_login(); }, 100);
                                                return 'identity_login';
                                            }
                                            var btn = document.querySelector('#cas_login_1,[onclick*="identity_login"],[onclick*="cas"]');
                                            if (!btn) {
                                                var candidates = Array.prototype.slice.call(document.querySelectorAll('button,input,span,a,div'));
                                                btn = candidates.find(function(el) {
                                                    return /统一身份认证/.test((el.innerText || el.value || '').trim());
                                                }) || null;
                                            }
                                            if (btn) {
                                                setTimeout(function() { btn.click(); }, 150);
                                                return 'clicked';
                                            }
                                            return 'missing';
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(script, null)
                                }

                                private fun tryCasFormSubmit(view: WebView?) {
                                    if (username.isBlank() || password.isBlank()) return
                                    val safeUsername = username
                                        .replace("\\", "\\\\")
                                        .replace("'", "\\'")
                                    val safePassword = password
                                        .replace("\\", "\\\\")
                                        .replace("'", "\\'")
                                    val script = """
                                        (function() {
                                            var user = document.querySelector('#username,input[name="username"],input[name="user"],input[type="text"]');
                                            var pass = document.querySelector('#password,input[name="password"],input[type="password"]');
                                            if (!user || !pass) return 'missing';
                                            user.focus();
                                            user.value = '$safeUsername';
                                            user.dispatchEvent(new Event('input', { bubbles: true }));
                                            user.dispatchEvent(new Event('change', { bubbles: true }));
                                            pass.focus();
                                            pass.value = '$safePassword';
                                            pass.dispatchEvent(new Event('input', { bubbles: true }));
                                            pass.dispatchEvent(new Event('change', { bubbles: true }));
                                            var btn = document.querySelector('#login,button[type="submit"],input[type="submit"],.login-btn');
                                            if (!btn) {
                                                var candidates = Array.prototype.slice.call(document.querySelectorAll('button,input,a,div'));
                                                btn = candidates.find(function(el) {
                                                    return /^登录${'$'}/.test((el.innerText || el.value || '').trim());
                                                }) || null;
                                            }
                                            if (btn) {
                                                setTimeout(function() { btn.click(); }, 180);
                                                return 'submitted';
                                            }
                                            var form = pass.form || user.form || document.querySelector('form');
                                            if (form) {
                                                setTimeout(function() { form.submit(); }, 180);
                                                return 'form_submit';
                                            }
                                            return 'filled';
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(script, null)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString().orEmpty()
                                    return captureOnce(url)
                                }

                                override fun onLoadResource(view: WebView?, url: String?) {
                                    captureOnce(url.orEmpty())
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == false || captured) return
                                    val code = error?.errorCode ?: 0
                                    val description = error?.description?.toString().orEmpty()
                                    val failingUrl = request?.url?.toString().orEmpty()
                                    onDebug("认证页探测失败 code=$code msg=$description url=$failingUrl")
                                    view?.postDelayed({
                                        if (!captured) {
                                            view.loadNextProbe()
                                        }
                                    }, 350)
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?
                                ) {
                                    if (request?.isForMainFrame == false || captured) return
                                    val status = errorResponse?.statusCode ?: 0
                                    if (status < 400) return
                                    val failingUrl = request?.url?.toString().orEmpty()
                                    onDebug("认证页 HTTP 探测失败 status=$status url=$failingUrl")
                                    view?.postDelayed({
                                        if (!captured) {
                                            view.loadNextProbe()
                                        }
                                    }, 350)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    val current = url.orEmpty()
                                    if (captureOnce(current)) {
                                        return
                                    }
                                    capturePageContent(view, current)
                                    tryPortalButtonClick(view)
                                    tryCasFormSubmit(view)
                                    view?.postDelayed({
                                        if (!captured) {
                                            tryPortalButtonClick(view)
                                            tryCasFormSubmit(view)
                                        }
                                    }, 1200)
                                    view?.postDelayed({
                                        if (!captured) {
                                            tryCasFormSubmit(view)
                                        }
                                    }, 2400)
                                    view?.postDelayed({
                                        if (!captured) {
                                            view.loadNextProbe()
                                        }
                                    }, 5000)
                                }
                            }
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            clearCache(true)
                            clearHistory()
                            clearFormData()
                            loadNextProbe()
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

                                private fun tryAutoFill(view: WebView?) {
                                    if (username.isBlank() || password.isBlank()) {
                                        onDebug("统一认证自动填表跳过: 未保存账号或密码")
                                        return
                                    }
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
                                            function fire(el) {
                                                ['input', 'change', 'keyup', 'blur'].forEach(function(name) {
                                                    try { el.dispatchEvent(new Event(name, { bubbles: true })); } catch (e) {}
                                                });
                                            }
                                            function visible(el) {
                                                if (!el) return false;
                                                var rect = el.getBoundingClientRect();
                                                var style = window.getComputedStyle(el);
                                                return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
                                            }
                                            function docs() {
                                                var result = [document];
                                                var frames = document.querySelectorAll('iframe,frame');
                                                for (var i = 0; i < frames.length; i++) {
                                                    try {
                                                        if (frames[i].contentDocument) result.push(frames[i].contentDocument);
                                                    } catch (e) {}
                                                }
                                                return result;
                                            }
                                            function bySelector(doc, candidates) {
                                                for (var i = 0; i < candidates.length; i++) {
                                                    try {
                                                        var list = doc.querySelectorAll(candidates[i]);
                                                        for (var j = 0; j < list.length; j++) {
                                                            if (visible(list[j]) && !list[j].disabled && !list[j].readOnly) return list[j];
                                                        }
                                                    } catch (e) {}
                                                }
                                                return null;
                                            }
                                            function byAttrs(doc, wantPassword) {
                                                var inputs = Array.prototype.slice.call(doc.querySelectorAll('input'));
                                                for (var i = 0; i < inputs.length; i++) {
                                                    var el = inputs[i];
                                                    if (!visible(el) || el.disabled || el.readOnly) continue;
                                                    var type = (el.getAttribute('type') || 'text').toLowerCase();
                                                    var name = ((el.getAttribute('name') || '') + ' ' +
                                                        (el.getAttribute('id') || '') + ' ' +
                                                        (el.getAttribute('placeholder') || '') + ' ' +
                                                        (el.getAttribute('aria-label') || '')).toLowerCase();
                                                    if (wantPassword) {
                                                        if (type === 'password' || name.indexOf('密码') >= 0 || name.indexOf('pass') >= 0 || name.indexOf('pwd') >= 0) return el;
                                                    } else {
                                                        if (type === 'password' || type === 'hidden' || type === 'submit' || type === 'button') continue;
                                                        if (name.indexOf('username') >= 0 || name.indexOf('userid') >= 0 ||
                                                            name.indexOf('user') >= 0 || name.indexOf('account') >= 0 ||
                                                            name.indexOf('login') >= 0 || name.indexOf('学号') >= 0 ||
                                                            name.indexOf('工号') >= 0 || name.indexOf('账号') >= 0) return el;
                                                    }
                                                }
                                                return null;
                                            }
                                            function findUser(doc) {
                                                return bySelector(doc, [
                                                    '#username',
                                                    '#userName',
                                                    '#mobileUsername',
                                                    'input[name="username"]',
                                                    'input[name="userName"]',
                                                    'input[name="userid"]',
                                                    'input[name="account"]',
                                                    'input[name="loginName"]',
                                                    'input[autocomplete="username"]',
                                                    'input[placeholder*="学号"]',
                                                    'input[placeholder*="工号"]',
                                                    'input[placeholder*="账号"]',
                                                    'input[type="text"]',
                                                    'input[type="tel"]'
                                                ]) || byAttrs(doc, false);
                                            }
                                            function findPass(doc) {
                                                return bySelector(doc, [
                                                    '#password',
                                                    '#passWord',
                                                    'input[name="password"]',
                                                    'input[name="passWord"]',
                                                    'input[name="pwd"]',
                                                    'input[autocomplete="current-password"]',
                                                    'input[placeholder*="密码"]',
                                                    'input[type="password"]'
                                                ]) || byAttrs(doc, true);
                                            }
                                            function findButton(doc, pass) {
                                                var btn = bySelector(doc, [
                                                    '#login',
                                                    '#login_submit',
                                                    'button[type="submit"]',
                                                    'input[type="submit"]',
                                                    '.login_btn',
                                                    '.login-btn',
                                                    '.btn-login',
                                                    '.submit'
                                                ]);
                                                if (btn) return btn;
                                                var all = Array.prototype.slice.call(doc.querySelectorAll('button,input[type="button"],input[type="submit"],a,div,span'));
                                                for (var i = 0; i < all.length; i++) {
                                                    var text = (all[i].innerText || all[i].value || '').trim();
                                                    if (visible(all[i]) && /^登录${'$'}/.test(text)) return all[i];
                                                }
                                                return null;
                                            }
                                            var allDocs = docs();
                                            var doc = null, user = null, pass = null;
                                            for (var i = 0; i < allDocs.length; i++) {
                                                user = findUser(allDocs[i]);
                                                pass = findPass(allDocs[i]);
                                                if (user && pass) {
                                                    doc = allDocs[i];
                                                    break;
                                                }
                                            }
                                            if (!user || !pass) {
                                                return 'missing inputs=' + document.querySelectorAll('input').length +
                                                    ' title=' + (document.title || '').slice(0, 30);
                                            }
                                            user.focus();
                                            setNativeValue(user, ${org.json.JSONObject.quote(username)});
                                            fire(user);
                                            pass.focus();
                                            setNativeValue(pass, ${org.json.JSONObject.quote(password)});
                                            fire(pass);
                                            var button = findButton(doc || document, pass);
                                            if (button) {
                                                setTimeout(function() {
                                                    fire(user);
                                                    fire(pass);
                                                    button.click();
                                                }, 700);
                                                return 'submitted user=' + (user.id || user.name || user.placeholder || '?') +
                                                    ' pass=' + (pass.id || pass.name || pass.placeholder || '?');
                                            }
                                            if (pass.form) {
                                                setTimeout(function() {
                                                    fire(user);
                                                    fire(pass);
                                                    pass.form.submit();
                                                }, 700);
                                                return 'form_submitted';
                                            }
                                            return 'filled';
                                        })();
                                    """.trimIndent()
                                    evalWithLog(view, "统一认证自动填表", 500, script)
                                    evalWithLog(view, "统一认证自动填表重试1", 1800, script)
                                    evalWithLog(view, "统一认证自动填表重试2", 3500, script)
                                    evalWithLog(view, "统一认证自动填表重试3", 6000, script)
                                }

                                private fun isSuccessUrl(url: String): Boolean {
                                    return url.contains("/3.htm") ||
                                        url.contains("login_success", ignoreCase = true) ||
                                        (url.contains("hsportal.hstc.edu.cn", ignoreCase = true) &&
                                            url.contains("/main.html", ignoreCase = true))
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
                                        tryAutoFill(view)
                                    }
                                }
                            }
                            loadUrl(loginUrl)
                        }
                    },
                    update = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                )
            }
        }
    )
}
