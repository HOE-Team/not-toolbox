// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI
import utils.PackageManagerUtils
import utils.PackageManagerType
import utils.CommonPackages
import utils.PackageInfo
import utils.PackageListLoader
import utils.TerminalSessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    selectedPackageManager: PackageManagerType = PackageManagerType.UNKNOWN, 
    isDebug: Boolean = true,
    useProxy: Boolean = false,
    proxyUrl: String = "https://ghproxy.net"
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var remotePackages by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 根据当前平台和包管理器加载对应的包列表
    val currentPackages = remember(selectedPackageManager, isDebug, remotePackages) {
        if (!isDebug && remotePackages.isNotEmpty()) {
            // 非 DEBUG 模式：使用远程拉取的结果
            remotePackages
        } else if (isDebug) {
            // DEBUG 模式：从本地加载
            if (selectedPackageManager != PackageManagerType.UNKNOWN) {
                CommonPackages.loadPackagesForManager(selectedPackageManager, true)
            } else {
                CommonPackages.loadPackagesForCurrentPlatform(true)
            }
        } else {
            emptyList()
        }
    }
    
    // 非 DEBUG 模式：从远程拉取包列表
    LaunchedEffect(selectedPackageManager, isDebug, useProxy, proxyUrl) {
        if (!isDebug) {
            isLoading = true
            errorMessage = null
            val manager = if (selectedPackageManager != PackageManagerType.UNKNOWN) {
                selectedPackageManager
            } else {
                PackageManagerUtils.detectPackageManager()
            }
            if (manager != PackageManagerType.UNKNOWN) {
                val proxy = if (useProxy) proxyUrl else null
                val result = PackageListLoader.fetchPackagesFromRemote(manager, proxy)
                result.fold(
                    onSuccess = { packages ->
                        remotePackages = packages
                        errorMessage = null
                    },
                    onFailure = { error ->
                        errorMessage = error.message ?: "未知错误"
                    }
                )
            } else {
                errorMessage = "未检测到包管理器"
            }
            isLoading = false
        }
    }
    // 按分类分组
    val packagesByCategory = remember(currentPackages) {
        currentPackages
            .filter { it.category != null }
            .groupBy { it.category!! }
    }
    val categories = remember(currentPackages) {
        val cats = linkedSetOf<String>()
        currentPackages
            .sortedBy { it.name }
            .forEach { pkg ->
                if (pkg.category != null) {
                    cats.add(pkg.category)
                }
            }
        cats.toList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tabs
        if (categories.isNotEmpty()) {
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                categories.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 12.sp) }
                    )
                }
            }
        }

        // Tab content
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (isLoading) {
                // 加载中：显示转圈圈
                LoadingHint()
            } else if (currentPackages.isEmpty()) {
                // 无法获取包列表时显示错误提示
                EmptyPackageListHint(errorMessage = errorMessage)
            } else if (categories.isNotEmpty() && selectedTab < categories.size) {
                val categoryName = categories[selectedTab]
                val tools = packagesByCategory[categoryName] ?: emptyList()
                ToolCardGrid(tools, selectedPackageManager)
            }
        }
    }
}

@Composable
private fun LoadingHint() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
            Text(
                text = "正在拉取应用列表",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyPackageListHint(errorMessage: String? = null) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SyncDisabled,
                contentDescription = "无法获取应用列表",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = "Oops! 无法获取应用列表",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = errorMessage ?: "请稍后再试，或检查网络连接",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ToolCardGrid(tools: List<PackageInfo>, selectedPackageManager: PackageManagerType = PackageManagerType.UNKNOWN) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Group tools into pairs (2 per row)
        tools.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                row.forEach { tool ->
                    ToolCard(
                        tool = tool,
                        selectedPackageManager = selectedPackageManager,
                        modifier = Modifier.weight(1f)
                    )
                }
                // If odd number, add spacer to balance
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ToolCard(tool: PackageInfo, selectedPackageManager: PackageManagerType = PackageManagerType.UNKNOWN, modifier: Modifier = Modifier) {
    // 使用传入的包管理器，如果为 UNKNOWN 则自动检测
    val packageManager = remember(selectedPackageManager) {
        if (selectedPackageManager != PackageManagerType.UNKNOWN) {
            selectedPackageManager
        } else {
            PackageManagerUtils.detectPackageManager()
        }
    }
    // 获取对应包管理器的包名
    val packageName = remember(tool, packageManager) {
        tool.getPackageNameForManager(packageManager)
    }
    
    // 获取许可证/用户协议 URL
    val licenseOrEulaUrl = remember(tool) {
        if (tool.isProprietarySoftware) {
            tool.eulaUrl
        } else {
            tool.licenseUrl ?: getDefaultLicenseUrl(tool.url)
        }
    }
    
    // 安装按钮
    val installCommand = remember(packageName, packageManager) {
        if (packageName != null && packageManager != PackageManagerType.UNKNOWN) {
            PackageManagerUtils.getInstallCommand(packageManager, packageName)
        } else {
            null
        }
    }
    
    // 安装流程状态（在 Card 外部定义，以便在 Card 外部渲染对话框）
    var installDialogState by remember { mutableStateOf<InstallDialogState?>(null) }
    
    Card(
        modifier = modifier
            .height(240.dp), // 增加高度以容纳许可证信息和双按钮
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 工具名称
            Text(
                text = tool.name,
                style = MaterialTheme.typography.titleMedium
            )
            
            // 工具描述
            Text(
                text = tool.description ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            
            // 许可证/专有软件提示
            if (tool.isProprietarySoftware) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "专有软件",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "专有软件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    if (tool.licenseType != null) {
                        Text(
                            text = "· ${tool.licenseType}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "开源",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = tool.licenseType ?: "开源软件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 显示包名（根据平台动态显示）
            if (packageName != null && packageManager != PackageManagerType.UNKNOWN) {
                val osName = remember { System.getProperty("os.name").lowercase() }
                val isWindows = osName.contains("windows")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "包名",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isWindows) "包名: $packageName" else "Linux包名: $packageName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 双按钮行：安装 + 查看许可证/用户协议
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 安装按钮
                if (installCommand != null) {
                    Button(
                        onClick = { installDialogState = InstallDialogState.CONFIRM },
                        modifier = Modifier.weight(1f),
                        enabled = packageManager != PackageManagerType.UNKNOWN
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "安装", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("安装", fontSize = 12.sp)
                    }
                } else {
                    // 没有安装命令时，显示占位按钮（禁用状态）
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        enabled = false
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "安装", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("安装", fontSize = 12.sp)
                    }
                }
                
                // 查看许可证/用户协议按钮
                if (licenseOrEulaUrl != null) {
                    OutlinedButton(
                        onClick = { openToolWebsite(licenseOrEulaUrl) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "查看", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (tool.isProprietarySoftware) "用户协议" else "查看许可证",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
    
    // 安装流程对话框（在 Card 外部渲染，避免被 Card 裁剪）
    // InstallConfirmationDialog 内部使用 when 表达式管理三个状态：CONFIRM → PROGRESS → RESULT
    if (installDialogState == InstallDialogState.CONFIRM) {
        InstallConfirmationDialog(
            toolName = tool.name,
            installCommand = installCommand ?: "",
            onDismiss = { installDialogState = null },
            onConfirm = {
                TerminalSessionManager.executeCommandAndWait(installCommand ?: "")
            }
        )
    }
}

private fun getDefaultLicenseUrl(toolUrl: String?): String? {
    if (toolUrl == null) return null
    // 尝试从工具URL推断许可证URL
    return when {
        toolUrl.contains("github.com") -> {
            // GitHub仓库，添加/LICENSE路径
            toolUrl.removeSuffix("/") + "/blob/main/LICENSE"
        }
        toolUrl.contains("gitlab.com") -> {
            // GitLab仓库
            toolUrl.removeSuffix("/") + "/-/blob/main/LICENSE"
        }
        else -> null
    }
}

private fun openToolWebsite(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            println("Opened website: $url")
        } else {
            println("Desktop browsing is not supported on this platform")
        }
    } catch (e: Exception) {
        println("Failed to open website $url: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * 安装流程对话框状态
 */
private enum class InstallDialogState {
    CONFIRM,    // 确认安装
    PROGRESS,   // 安装进度
    RESULT      // 安装结果
}

/**
 * 安装确认对话框
 * 点击"确认安装"后打开安装进度对话框（dialog1），安装完成后跳转到结果对话框（dialog2）
 */
@Composable
fun InstallConfirmationDialog(
    toolName: String,
    installCommand: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var dialogState by remember { mutableStateOf(InstallDialogState.CONFIRM) }
    var installSuccess by remember { mutableStateOf(false) }
    
    when (dialogState) {
        InstallDialogState.CONFIRM -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("确认安装") },
                text = { 
                    Column {
                        Text("将执行以下命令安装 $toolName:")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = installCommand,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "注意：这需要管理员权限，可能会要求输入密码。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onConfirm()
                            dialogState = InstallDialogState.PROGRESS
                        }
                    ) {
                        Text("确认安装")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = onDismiss
                    ) {
                        Text("取消")
                    }
                }
            )
        }
        
        InstallDialogState.PROGRESS -> {
            // dialog1: 安装进度对话框
            var terminalOutput by remember { mutableStateOf("") }
            var isRunning by remember { mutableStateOf(false) }
            
            // Collect flow updates
            LaunchedEffect(Unit) {
                TerminalSessionManager.outputFlow.collect { output ->
                    terminalOutput = output
                }
            }
            
            LaunchedEffect(Unit) {
                TerminalSessionManager.isRunning.collect { running ->
                    isRunning = running
                    // 当进程从运行变为停止时，安装完成
                    if (!running) {
                        // 检查是否被取消
                        val cancelled = TerminalSessionManager.wasCancelled.value
                        if (cancelled) {
                            installSuccess = false
                        } else {
                            // 检查退出码
                            val exitCode = TerminalSessionManager.lastExitCode.value
                            installSuccess = exitCode != null && exitCode == 0
                        }
                        dialogState = InstallDialogState.RESULT
                    }
                }
            }
            
            AlertDialog(
                onDismissRequest = { /* 安装中不允许关闭 */ },
                title = { Text("正在安装") },
                text = { 
                    Column {
                        // 日志输出区域
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .background(
                                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp)
                        ) {
                            val scrollState = rememberScrollState()
                            
                            LaunchedEffect(terminalOutput) {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                            
                            Text(
                                text = terminalOutput,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState),
                                style = androidx.compose.ui.text.TextStyle(
                                    color = androidx.compose.ui.graphics.Color.LightGray,
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
                                maxLines = Int.MAX_VALUE
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 状态提示
                        if (isRunning) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "正在安装 $toolName...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Text(
                                text = "安装已完成。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    // 安装中显示"取消"按钮
                    if (isRunning) {
                        OutlinedButton(
                            onClick = {
                                TerminalSessionManager.stopCurrentProcess()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "取消", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("取消")
                        }
                    }
                }
            )
        }
        
        InstallDialogState.RESULT -> {
            // dialog2: 安装结果对话框
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { 
                    Text(
                        text = if (installSuccess) "安装成功！" else "安装失败",
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                text = { 
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (installSuccess) {
                            // 成功图标
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "安装成功",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "已成功安装 $toolName",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            // 失败图标
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "安装失败",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "无法安装 $toolName，详情请查看\"终端\"页面输出",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss
                    ) {
                        Text("完成")
                    }
                }
            )
        }
    }
}
