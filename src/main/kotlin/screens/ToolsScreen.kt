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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import components.MaterialSymbols
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import utils.PackageDetector
import utils.DetectionStatus
import utils.UpdateChecker
import utils.UpdateCheckStatus
import utils.TerminalSessionManager
import theme.warningColors
import config.loadOfflineItems
import config.addOfflineItem
import config.updateOfflineItem
import config.deleteOfflineItem
import config.MOfflineEntryType
import config.OfflineItem
import java.awt.FileDialog

/** 工具搜索来源筛选 */
private enum class ToolFilter { ALL, ONLINE, OFFLINE }

/** 全局搜索结果封装 */
private data class GlobalResult(
    val isOnline: Boolean,
    val packageInfo: PackageInfo? = null,
    val offlineItem: OfflineItem? = null
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    selectedPackageManager: PackageManagerType = PackageManagerType.UNKNOWN,
    isDebug: Boolean = true,
    useProxy: Boolean = false,
    proxyUrl: String = "https://ghproxy.net",
    searchVisible: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    sourceTab: Int = 0,
    onSourceTabChange: (Int) -> Unit = {},
    onNavigateToTerminal: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var toolFilter by rememberSaveable { mutableStateOf(ToolFilter.ALL) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var remotePackages by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableStateOf(0) }
    var offlineItems by remember { mutableStateOf(loadOfflineItems()) }
    var showLinuxInputDialog by remember { mutableStateOf(false) }
    var editCommandId by remember { mutableStateOf<String?>(null) }
    var fabMenuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var isExecuting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isWindows = remember { System.getProperty("os.name").lowercase().contains("windows") }

    LaunchedEffect(Unit) {
        TerminalSessionManager.isRunning.collect { running ->
            if (isExecuting && !running) {
                isExecuting = false
                val exitCode = TerminalSessionManager.lastExitCode.value
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (exitCode == 0) "已执行指令"
                        else "指令执行错误，查看\"终端\"页面获得更多信息"
                    )
                }
            }
        }
    }

    val currentPackages = remember(selectedPackageManager, isDebug, remotePackages) {
        when {
            !isDebug && remotePackages.isNotEmpty() -> remotePackages
            isDebug -> if (selectedPackageManager != PackageManagerType.UNKNOWN)
                CommonPackages.loadPackagesForManager(selectedPackageManager, true)
            else CommonPackages.loadPackagesForCurrentPlatform(true)
            else -> emptyList()
        }
    }

    // 实际生效的包管理器（未显式选择时自动检测；该调用带进程级缓存）
    val effectiveManager = remember(selectedPackageManager) {
        if (selectedPackageManager != PackageManagerType.UNKNOWN) selectedPackageManager
        else PackageManagerUtils.detectPackageManager()
    }

    // 已安装检测：仅在本进程确实需要时执行（首次进入 / 切换包管理器 / 缓存被删除）。
    // 反复进出工具页会命中进程内快照，既不读盘也不扫描。
    LaunchedEffect(effectiveManager) {
        PackageDetector.detectIfNeeded(effectiveManager)
        // 检测完成后再检查可用更新（顺序执行，避免两段网络请求叠加）
        UpdateChecker.checkIfNeeded(effectiveManager)
    }

    // 检测中：显示转圈并隐藏卡片
    val detection by PackageDetector.state.collectAsState()
    val isDetecting = detection.status == DetectionStatus.SCANNING

    // 更新检查中：先转圈（带「立即显示列表」），用户可选择提前展示卡片
    val checkState by UpdateChecker.state.collectAsState()
    val checkingUpdates = checkState.status == UpdateCheckStatus.CHECKING
    var warningSnackbarVisible by remember { mutableStateOf(false) }

    // 检测失败（如离线导致 RPM/Winget 命令不可用）时提示用户
    val detectionMessage by PackageDetector.message.collectAsState()
    LaunchedEffect(detectionMessage) {
        detectionMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    // 更新检查的警告提示（黄色）：不支持检查 / 检查失败
    LaunchedEffect(checkState.supported, checkState.failed, checkState.noticeReported, effectiveManager) {
        if (checkState.noticeReported) return@LaunchedEffect
        val noticeMessage = when {
            !checkState.supported -> "您设备上的包管理器不支持更新检查，请自行判断"
            checkState.failed -> "更新检查失败，您需要自行判断"
            else -> null
        } ?: return@LaunchedEffect

        UpdateChecker.markNoticeReported()
        warningSnackbarVisible = true
        val result = snackbarHostState.showSnackbar(
            message = noticeMessage,
            actionLabel = if (checkState.failed) "重试" else null,
            duration = SnackbarDuration.Long
        )
        warningSnackbarVisible = false
        if (result == SnackbarResult.ActionPerformed) {
            UpdateChecker.retryCheck(effectiveManager)
        }
    }

    LaunchedEffect(selectedPackageManager, isDebug, useProxy, proxyUrl, reloadTrigger) {
        if (!isDebug) {
            isLoading = true
            errorMessage = null
            val manager = if (selectedPackageManager != PackageManagerType.UNKNOWN)
                selectedPackageManager else PackageManagerUtils.detectPackageManager()
            if (manager != PackageManagerType.UNKNOWN) {
                val result = PackageListLoader.fetchPackagesFromRemote(manager, if (useProxy) proxyUrl else null)
                result.fold(
                    onSuccess = { packages -> remotePackages = packages; errorMessage = null },
                    onFailure = { error -> errorMessage = error.message ?: "未知错误" }
                )
            } else errorMessage = "未检测到包管理器"
            isLoading = false
        }
    }

    val packagesByCategory = remember(currentPackages) {
        currentPackages.filter { it.category != null }.groupBy { it.category!! }
    }
    val categories = remember(currentPackages) {
        val cats = linkedSetOf<String>()
        currentPackages.sortedBy { it.name }.forEach { if (it.category != null) cats.add(it.category) }
        cats.toList()
    }

    // 点击 Search 按钮后即隐藏 Tabs，进入全局搜索模式
    val isSearching = searchVisible
    // 搜索框有输入才算真正开始搜索
    val hasQuery = searchVisible && searchQuery.isNotBlank()
    val filteredPackages = remember(currentPackages, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) currentPackages
        else currentPackages.filter { it.name.contains(q, true) || it.description?.contains(q, true) == true }
    }
    val filteredOfflineItems = remember(offlineItems, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) offlineItems else offlineItems.filter { it.value.contains(q, true) }
    }
    val showOnline = toolFilter != ToolFilter.OFFLINE
    val showOffline = toolFilter != ToolFilter.ONLINE
    val globalResults = remember(filteredPackages, filteredOfflineItems, toolFilter) {
        buildList {
            if (showOnline) addAll(filteredPackages.map { GlobalResult(true, packageInfo = it) })
            if (showOffline) addAll(filteredOfflineItems.map { GlobalResult(false, offlineItem = it) })
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索行（searchVisible 由 TopBar 按钮控制，显示搜索框 + 筛选按钮）
        if (searchVisible) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("键入来搜索...") },
                    leadingIcon = { Icon(MaterialSymbols.Search, contentDescription = "搜索") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty())
                            IconButton(onClick = { onSearchQueryChange("") }) { Icon(MaterialSymbols.Close, contentDescription = "清除") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Box {
                    IconButton(onClick = { filterMenuExpanded = true }) { Icon(MaterialSymbols.FilterList, contentDescription = "筛选") }
                    DropdownMenu(expanded = filterMenuExpanded, onDismissRequest = { filterMenuExpanded = false }) {
                        ToolFilter.entries.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(when (f) { ToolFilter.ALL -> "全部"; ToolFilter.ONLINE -> "在线"; ToolFilter.OFFLINE -> "离线" }) },
                                trailingIcon = { if (toolFilter == f) Icon(MaterialSymbols.Check, contentDescription = null) },
                                onClick = { toolFilter = f; filterMenuExpanded = false }
                            )
                        }
                    }
                }
            }
        }

        if (isSearching && !hasQuery) {
            // 搜索框展开但无输入：居中提示"键入来搜索"，不展示卡片
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SearchPromptHint()
                SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter)) { data ->
                    WarningAwareSnackbar(data, warningSnackbarVisible)
                }
            }
        } else if (isSearching && hasQuery) {
            // 全局搜索模式：隐藏 Tabs，显示所有匹配项
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isDetecting -> DetectingHint()
                    checkingUpdates && !checkState.dismissed -> CheckingUpdatesHint(onShowNow = { UpdateChecker.dismiss() })
                    globalResults.isEmpty() -> EmptySearchHint()
                    else -> GlobalResultGrid(
                        results = globalResults,
                        selectedPackageManager = effectiveManager,
                        onRunOffline = { item ->
                            isExecuting = true
                            if (item.type == MOfflineEntryType.PATH) {
                                val workingDir = java.io.File(item.value).parent ?: ""
                                TerminalSessionManager.executeCommandAndWait("\"${item.value}\"", workingDir)
                            } else {
                                TerminalSessionManager.executeCommandAndWait(item.value)
                                onNavigateToTerminal()
                            }
                        },
                        onDeleteOffline = { item -> deleteOfflineItem(item.id); offlineItems = loadOfflineItems() },
                        onEditOffline = { item -> if (item.type == MOfflineEntryType.COMMAND) editCommandId = item.id }
                    )
                }
                SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter)) { data ->
                    WarningAwareSnackbar(data, warningSnackbarVisible)
                }
            }
        } else {
            // 非搜索模式：显示一级 Tabs
            PrimaryTabRow(selectedTabIndex = sourceTab, modifier = Modifier.fillMaxWidth()) {
                Tab(
                    selected = sourceTab == 0,
                    onClick = { onSourceTabChange(0) },
                    icon = { Icon(MaterialSymbols.Link, "联机", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
                    text = { Text("联机", fontSize = 14.sp) }
                )
                Tab(
                    selected = sourceTab == 1,
                    onClick = { onSourceTabChange(1) },
                    icon = { Icon(MaterialSymbols.LinkOff, "本地", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
                    text = { Text("本地", fontSize = 14.sp) }
                )
            }

            if (sourceTab == 0 && showOnline) {
                // 联机模式
                if (categories.isNotEmpty()) {
                    PrimaryTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                        categories.forEachIndexed { index, title ->
                            Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title, fontSize = 12.sp) })
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                    when {
                        isLoading -> LoadingHint()
                        isDetecting -> DetectingHint()
                        checkingUpdates && !checkState.dismissed -> CheckingUpdatesHint(onShowNow = { UpdateChecker.dismiss() })
                        filteredPackages.isEmpty() -> EmptyPackageListHint(errorMessage, onRetry = { reloadTrigger++ })
                        categories.isNotEmpty() && selectedTab < categories.size -> {
                            val tools = (packagesByCategory[categories[selectedTab]] ?: emptyList())
                                .filter { t -> searchQuery.isBlank() || t.name.contains(searchQuery.trim(), true) || t.description?.contains(searchQuery.trim(), true) == true }
                            if (tools.isEmpty()) EmptySearchHint() else ToolCardGrid(tools, effectiveManager)
                        }
                    }
                    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter)) { data ->
                        WarningAwareSnackbar(data, warningSnackbarVisible)
                    }
                }
            } else if (sourceTab == 1 && showOffline) {
                // 本地模式
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        offlineItems.isEmpty() -> EmptyOfflineHint(
                            isWindows = isWindows,
                            onAddPathClick = { handleAddExecutable { path -> if (path.isNotBlank()) { addOfflineItem(MOfflineEntryType.PATH, path); offlineItems = loadOfflineItems() } } },
                            onAddCommandClick = { showLinuxInputDialog = true }
                        )
                        filteredOfflineItems.isEmpty() -> EmptySearchHint()
                        else -> {
                            LocalToolCardGrid(
                                items = filteredOfflineItems,
                                onRun = { item ->
                                    isExecuting = true
                                    if (item.type == MOfflineEntryType.PATH) {
                                        val workingDir = java.io.File(item.value).parent ?: ""
                                        TerminalSessionManager.executeCommandAndWait("\"${item.value}\"", workingDir)
                                    } else {
                                        TerminalSessionManager.executeCommandAndWait(item.value)
                                        onNavigateToTerminal()
                                    }
                                },
                                onDelete = { item -> deleteOfflineItem(item.id); offlineItems = loadOfflineItems() },
                                onEdit = { item -> if (item.type == MOfflineEntryType.COMMAND) editCommandId = item.id }
                            )
                            // Windows FAB Menu
                            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                                if (isWindows) {
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxSize()) {
                                        if (fabMenuExpanded) {
                                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("添加指令", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
                                                    SmallFloatingActionButton(onClick = { fabMenuExpanded = false; showLinuxInputDialog = true }) {
                                                        Icon(MaterialSymbols.Terminal2, contentDescription = "添加指令")
                                                    }
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("添加可执行文件", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
                                                    SmallFloatingActionButton(onClick = {
                                                        fabMenuExpanded = false
                                                        handleAddExecutable { path -> if (path.isNotBlank()) { addOfflineItem(MOfflineEntryType.PATH, path); offlineItems = loadOfflineItems() } }
                                                    }) {
                                                        Icon(MaterialSymbols.AppRegistration, contentDescription = "添加可执行文件")
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        FloatingActionButton(onClick = { fabMenuExpanded = !fabMenuExpanded }) {
                                            Icon(if (fabMenuExpanded) MaterialSymbols.Close else MaterialSymbols.Add, if (fabMenuExpanded) "收起" else "添加")
                                        }
                                    }
                                } else {
                                    FloatingActionButton(onClick = { showLinuxInputDialog = true }) { Icon(MaterialSymbols.Add, "添加") }
                                }
                            }
                        }
                    }
                    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter)) { data ->
                        WarningAwareSnackbar(data, warningSnackbarVisible)
                    }
                }
            }
        }
    }

    if (showLinuxInputDialog) {
        LinuxCommandInputDialog(
            initialValue = "", isEdit = false,
            onDismiss = { showLinuxInputDialog = false },
            onConfirm = { cmd -> if (cmd.isNotBlank()) { addOfflineItem(MOfflineEntryType.COMMAND, cmd); offlineItems = loadOfflineItems() }; showLinuxInputDialog = false }
        )
    }
    editCommandId?.let { editId ->
        val editing = offlineItems.firstOrNull { it.id == editId }
        if (editing != null) {
            LinuxCommandInputDialog(
                initialValue = editing.value, isEdit = true,
                onDismiss = { editCommandId = null },
                onConfirm = { cmd -> if (cmd.isNotBlank()) { updateOfflineItem(editId, cmd); offlineItems = loadOfflineItems() }; editCommandId = null }
            )
        }
    }
}

private fun handleAddExecutable(onSelected: (String) -> Unit) {
    try {
        val dialog = FileDialog(null as java.awt.Frame?, "选择可执行文件", FileDialog.LOAD)
        dialog.isVisible = true
        val dir = dialog.directory; val f = dialog.file
        if (dir != null && f != null) onSelected(java.io.File(dir, f).absolutePath)
    } catch (e: Exception) { println("文件选择失败: ${e.message}") }
}

@Composable
private fun GlobalResultGrid(
    results: List<GlobalResult>,
    selectedPackageManager: PackageManagerType,
    onRunOffline: (OfflineItem) -> Unit,
    onDeleteOffline: (OfflineItem) -> Unit,
    onEditOffline: (OfflineItem) -> Unit
) {
    // 按离线/联机分组
    val offlineResults = results.filter { !it.isOnline }
    val onlineResults = results.filter { it.isOnline }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // 联机分组标题（存在在线结果时显示）
        if (onlineResults.isNotEmpty()) {
            SectionTitle("联机")
            onlineResults.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { r ->
                        if (r.packageInfo != null) {
                            ToolCard(r.packageInfo, selectedPackageManager, Modifier.weight(1f))
                        }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // 离线分组标题（存在离线结果时显示）
        if (offlineResults.isNotEmpty()) {
            SectionTitle("离线")
            offlineResults.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { r ->
                        if (r.offlineItem != null) {
                            LocalToolCard(r.offlineItem, onRun = { onRunOffline(r.offlineItem) }, onDelete = { onDeleteOffline(r.offlineItem) }, onEdit = { onEditOffline(r.offlineItem) }, modifier = Modifier.weight(1f))
                        }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun EmptyOfflineHint(isWindows: Boolean, onAddPathClick: () -> Unit, onAddCommandClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("列表为空", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(if (isWindows) "添加一个本地可执行程序或指令来使用离线功能" else "添加一个指令来使用离线功能",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (isWindows) {
                Button(onClick = onAddPathClick) { Icon(MaterialSymbols.AppRegistration, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("添加一个可执行文件") }
                OutlinedButton(onClick = onAddCommandClick) { Icon(MaterialSymbols.Terminal2, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("添加一个指令") }
            } else {
                Button(onClick = onAddCommandClick) { Icon(MaterialSymbols.Terminal2, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("添加一个指令") }
            }
        }
    }
}

@Composable
private fun LocalToolCardGrid(items: List<OfflineItem>, onRun: (OfflineItem) -> Unit, onDelete: (OfflineItem) -> Unit, onEdit: (OfflineItem) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { item -> LocalToolCard(item, onRun = { onRun(item) }, onDelete = { onDelete(item) }, onEdit = { onEdit(item) }, modifier = Modifier.weight(1f)) }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LocalToolCard(item: OfflineItem, onRun: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val isPath = item.type == MOfflineEntryType.PATH
    Card(modifier = modifier.height(160.dp), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.value, style = MaterialTheme.typography.titleSmall, maxLines = 2, modifier = Modifier.weight(1f))
                if (item.type == MOfflineEntryType.COMMAND) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(MaterialSymbols.Edit, "修改", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(MaterialSymbols.Delete, "删除", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(if (isPath) MaterialSymbols.AppRegistration else MaterialSymbols.Terminal2, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (isPath) "可执行文件" else "指令", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                Icon(MaterialSymbols.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (isPath) "运行" else "执行", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LinuxCommandInputDialog(initialValue: String = "", isEdit: Boolean = false, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var cmd by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "修改指令" else "添加指令") },
        text = {
            Column {
                Text("输入一个快捷指令，用于离线执行:")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(cmd, { cmd = it }, placeholder = { Text("例如: sudo apt-get update") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(cmd) }, enabled = cmd.isNotBlank()) { Text(if (isEdit) "保存" else "添加") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun LoadingHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
            Text("正在拉取应用列表", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 检测已安装包期间占位：显示转圈并隐藏卡片 */
@Composable
private fun DetectingHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
            Text("正在检测已安装的应用", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 检查可用更新期间占位：显示转圈，并提供「立即显示列表」提前查看卡片 */
@Composable
private fun CheckingUpdatesHint(onShowNow: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
            Text("正在检查可用更新", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onShowNow) {
                Text("立即显示列表")
            }
        }
    }
}

/** 警告类提示使用主题里的黄色警告色，其余 Snackbar 保持默认外观 */
@Composable
private fun WarningAwareSnackbar(data: SnackbarData, warning: Boolean) {
    if (warning) {
        val colors = MaterialTheme.warningColors
        Snackbar(
            snackbarData = data,
            containerColor = colors.container,
            contentColor = colors.onContainer,
            actionColor = colors.action,
            actionContentColor = colors.action
        )
    } else {
        Snackbar(snackbarData = data)
    }
}

@Composable
private fun SearchPromptHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(MaterialSymbols.Search, "键入来搜索", Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text("键入来搜索", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("输入字符来进行搜索", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun EmptySearchHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(MaterialSymbols.SearchOff, "无搜索结果", Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text("没有匹配的工具", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("尝试更换搜索关键词", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun EmptyPackageListHint(errorMessage: String? = null, onRetry: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(MaterialSymbols.FileDownloadOff, "无法获取应用列表", Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text("Oops! 无法获取应用列表", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(errorMessage ?: "请稍后再试，或检查网络连接", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            // 重试按钮
            Button(onClick = onRetry) {
                Icon(MaterialSymbols.Refresh, "重试", Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("重试")
            }
        }
    }
}

@Composable
fun ToolCardGrid(
    tools: List<PackageInfo>,
    selectedPackageManager: PackageManagerType = PackageManagerType.UNKNOWN
) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        tools.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { tool -> ToolCard(tool, selectedPackageManager, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ToolCard(
    tool: PackageInfo,
    selectedPackageManager: PackageManagerType = PackageManagerType.UNKNOWN,
    modifier: Modifier = Modifier
) {
    // 包管理器由 ToolsScreen 统一解析（内部带进程级缓存），此处不再重复检测
    val packageManager = selectedPackageManager
    val packageName = remember(tool, packageManager) { tool.getPackageNameForManager(packageManager) }
    val licenseOrEulaUrl = remember(tool) { if (tool.isProprietarySoftware) tool.eulaUrl else tool.licenseUrl ?: getDefaultLicenseUrl(tool.url) }
    val supported = packageName != null && packageManager != PackageManagerType.UNKNOWN
    val installCommand = remember(packageName, packageManager) {
        if (supported) PackageManagerUtils.getInstallCommand(packageManager, packageName) else null
    }
    val updateCommand = remember(packageName, packageManager) {
        if (supported) PackageManagerUtils.getPackageUpdateCommand(packageManager, packageName) else null
    }
    val uninstallCommand = remember(packageName, packageManager) {
        if (supported) PackageManagerUtils.getUninstallCommand(packageManager, packageName) else null
    }
    // 待确认的包管理动作（安装 / 更新 / 卸载）
    var pendingAction by remember { mutableStateOf<PackageAction?>(null) }
    val scope = rememberCoroutineScope()

    // 已安装检测（响应式）：快照查表为 O(1)
    val detection by PackageDetector.state.collectAsState()
    val installedEntry = detection.takeIf { it.manager == packageManager }?.snapshot?.lookup(packageName)
    val isInstalled = installedEntry != null

    // 更新可用性（响应式）：
    // - 仅在「检查成功且确认无更新」或「正在检查」时禁用
    // - 不支持检查（EMERGE）或检查失败时保持可用，由用户自行判断
    val checkState by UpdateChecker.state.collectAsState()
    val sameManager = checkState.manager == packageManager
    val checkingUpdates = sameManager && checkState.status == UpdateCheckStatus.CHECKING
    val upToDate = isInstalled && sameManager && checkState.supported && !checkState.failed &&
            checkState.status == UpdateCheckStatus.READY &&
            !UpdateChecker.hasUpdate(packageManager, packageName)
    val canUpdate = updateCommand != null && !checkingUpdates && !upToDate

    // 可升级到的目标版本（无可用更新时为 null）
    val targetVersion = if (isInstalled) UpdateChecker.availableVersion(packageName) else null

    val pendingCommand = when (pendingAction) {
        PackageAction.INSTALL -> installCommand
        PackageAction.UPDATE -> updateCommand
        PackageAction.UNINSTALL -> uninstallCommand
        null -> null
    }

    Card(modifier = modifier.height(260.dp), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 已安装：右上角提供卸载入口
                if (isInstalled) {
                    IconButton(
                        onClick = { pendingAction = PackageAction.UNINSTALL },
                        enabled = uninstallCommand != null,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(MaterialSymbols.Delete, "卸载", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(tool.description ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 2)
            if (tool.isProprietarySoftware) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(MaterialSymbols.Warning, "专有软件", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    Text("专有软件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (tool.licenseType != null) Text("· ${tool.licenseType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(MaterialSymbols.Description, "开源", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(tool.licenseType ?: "开源软件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (packageName != null && packageManager != PackageManagerType.UNKNOWN) {
                val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(MaterialSymbols.Terminal, "包名", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (isWindows) "包名: $packageName" else "Linux包名: $packageName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (isInstalled) {
                val versionLabel = installedEntry.version?.let { "已安装 · $it" } ?: "已安装"
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(MaterialSymbols.Check, "已安装", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = versionLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isInstalled && targetVersion != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(MaterialSymbols.Info, "有更新", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "有更新 · $targetVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isInstalled) {
                    Button(
                        onClick = { pendingAction = PackageAction.UPDATE },
                        modifier = Modifier.weight(1f),
                        enabled = canUpdate
                    ) {
                        // 确认无可用更新时，按钮改为「已是最新」并换用勾选图标
                        if (upToDate) {
                            Icon(MaterialSymbols.Check, "已是最新", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("已是最新", fontSize = 12.sp)
                        } else {
                            Icon(MaterialSymbols.Refresh, "更新", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("更新", fontSize = 12.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = { pendingAction = PackageAction.INSTALL },
                        modifier = Modifier.weight(1f),
                        enabled = installCommand != null
                    ) {
                        Icon(MaterialSymbols.Download, "安装", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("安装", fontSize = 12.sp)
                    }
                }
                if (licenseOrEulaUrl != null) {
                    OutlinedButton(onClick = { openToolWebsite(licenseOrEulaUrl) }, modifier = Modifier.weight(1f)) {
                        Icon(MaterialSymbols.OpenInNew, "查看", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (tool.isProprietarySoftware) "用户协议" else "查看许可证", fontSize = 12.sp)
                    }
                }
            }
        }
    }
    val action = pendingAction
    if (action != null && pendingCommand != null) {
        PackageActionDialog(
            toolName = tool.name,
            action = action,
            command = pendingCommand,
            onDismiss = { pendingAction = null },
            onConfirm = { TerminalSessionManager.executeCommandAndWait(pendingCommand) },
            onSucceeded = {
                when (action) {
                    PackageAction.INSTALL ->
                        scope.launch { PackageDetector.onPackageInstalled(packageManager, packageName) }
                    PackageAction.UNINSTALL ->
                        scope.launch { PackageDetector.onPackageUninstalled(packageManager, packageName) }
                    PackageAction.UPDATE -> scope.launch {
                        // 更新后版本号已过时：静默重扫（不转圈、卡片不消失）
                        PackageDetector.rescanSilently(packageManager)
                        // 同步刷新可用更新信息，使「更新」按钮回到正确状态
                        UpdateChecker.recheckSilently(packageManager)
                    }
                }
            }
        )
    }
}

private fun getDefaultLicenseUrl(toolUrl: String?): String? {
    if (toolUrl == null) return null
    return when {
        toolUrl.contains("github.com") -> toolUrl.removeSuffix("/") + "/blob/main/LICENSE"
        toolUrl.contains("gitlab.com") -> toolUrl.removeSuffix("/") + "/-/blob/main/LICENSE"
        else -> null
    }
}

private fun openToolWebsite(url: String) {
    try { if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(URI(url)) }
    catch (e: Exception) { println("打开网站失败 $url: ${e.message}") }
}

/** 将终端输出解析为带 ANSI 颜色的 AnnotatedString（供 Dialog 内嵌入式终端展示） */
private fun parseAnsiForDialog(text: String): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        TerminalSessionManager.parseAnsiText(text).forEach { styled ->
            val color = TerminalSessionManager.ansiColorToComposeColor(styled.foregroundColor)
            val bgColor = TerminalSessionManager.ansiColorToComposeColor(styled.backgroundColor)
            withStyle(
                SpanStyle(
                    color = color ?: Color(0xFFCCCCCC),
                    background = bgColor ?: Color.Transparent,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            ) { append(styled.text) }
        }
    }
}

/** 包管理动作：安装 / 更新 / 卸载 */
enum class PackageAction { INSTALL, UPDATE, UNINSTALL }

private enum class PackageDialogState { CONFIRM, PROGRESS, RESULT }

@Composable
fun PackageActionDialog(
    toolName: String,
    action: PackageAction,
    command: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onSucceeded: () -> Unit = {}
) {
    var dialogState by remember { mutableStateOf(PackageDialogState.CONFIRM) }
    var installSuccess by remember { mutableStateOf(false) }

    // 各动作的文案
    val confirmTitle = when (action) {
        PackageAction.INSTALL -> "确认安装"
        PackageAction.UPDATE -> "确认更新"
        PackageAction.UNINSTALL -> "确认卸载"
    }
    val confirmLead = when (action) {
        PackageAction.INSTALL -> "将执行以下命令安装 $toolName:"
        PackageAction.UPDATE -> "将执行以下命令更新 $toolName:"
        PackageAction.UNINSTALL -> "将执行以下命令卸载 $toolName:"
    }
    val confirmWarning = when (action) {
        PackageAction.INSTALL -> "注意：这需要管理员权限，可能会要求输入密码。"
        PackageAction.UPDATE -> "注意：这需要管理员权限，可能会要求输入密码。"
        PackageAction.UNINSTALL -> "注意：卸载后可能需要重新安装才能恢复；该操作需要管理员权限。"
    }
    val progressTitle = when (action) {
        PackageAction.INSTALL -> "正在安装"
        PackageAction.UPDATE -> "正在更新"
        PackageAction.UNINSTALL -> "正在卸载"
    }
    val resultTitle = when (action) {
        PackageAction.INSTALL -> if (installSuccess) "安装成功！" else "安装失败"
        PackageAction.UPDATE -> if (installSuccess) "更新成功！" else "更新失败"
        PackageAction.UNINSTALL -> if (installSuccess) "卸载成功！" else "卸载失败"
    }
    val resultText = when (action) {
        PackageAction.INSTALL -> if (installSuccess) "已成功安装 $toolName" else "无法安装 $toolName，详情请查看\"终端\"页面输出"
        PackageAction.UPDATE -> if (installSuccess) "已成功更新 $toolName" else "无法更新 $toolName，详情请查看\"终端\"页面输出"
        PackageAction.UNINSTALL -> if (installSuccess) "已成功卸载 $toolName" else "无法卸载 $toolName，详情请查看\"终端\"页面输出"
    }

    when (dialogState) {
        PackageDialogState.CONFIRM -> AlertDialog(onDismissRequest = onDismiss, title = { Text(confirmTitle) },
            text = { Column { Text(confirmLead); Spacer(Modifier.height(8.dp)); Text(command, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp)).padding(8.dp)); Spacer(Modifier.height(8.dp)); Text(confirmWarning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) } },
            confirmButton = { Button(onClick = { onConfirm(); dialogState = PackageDialogState.PROGRESS }) { Text(confirmTitle) } },
            dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } })
        PackageDialogState.PROGRESS -> {
            var output by remember { mutableStateOf("") }
            var running by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { TerminalSessionManager.outputFlow.collect { output = it } }
            LaunchedEffect(Unit) {
                var sawRunning = false
                var resultHandled = false
                TerminalSessionManager.isRunning.collect { r ->
                    running = r
                    if (r) {
                        sawRunning = true
                    } else if (sawRunning && !resultHandled) {
                        resultHandled = true
                        installSuccess = TerminalSessionManager.lastExitCode.value == 0
                        if (installSuccess) onSucceeded()
                        dialogState = PackageDialogState.RESULT
                    }
                }
            }
            // 嵌入式终端样式的输出区域（深色背景 + ANSI 着色 + 等宽字体）
            val annotatedOutput = remember(output) { parseAnsiForDialog(output) }
            AlertDialog(onDismissRequest = {}, title = { Text(progressTitle) },
                text = {
                    Column {
                        // 内嵌终端的输入状态（PROGRESS 期间持有）
                        var pendingInput by remember { mutableStateOf("") }
                        val inputFocusRequester = remember { FocusRequester() }
                        // 发送输入行到活动会话（运行中的安装命令的 stdin）
                        val sendPendingInput: () -> Unit = {
                            if (pendingInput.isNotBlank()) {
                                TerminalSessionManager.executeCommand(pendingInput)
                                pendingInput = ""
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 320.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E1E1E) // 深色终端背景
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    // 拦截 Ctrl+C（通过父级冒泡接收输入框未消费的按键）
                                    .onKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown &&
                                            event.key == Key.C && event.isCtrlPressed
                                        ) {
                                            TerminalSessionManager.activeSessionId.value?.let {
                                                TerminalSessionManager.sendRawInput(it, "\u0003")
                                            }
                                            true
                                        } else false
                                    }
                            ) {
                                // 输出区域：点击任意位置将焦点交给输入框
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clickable { inputFocusRequester.requestFocus() }
                                ) {
                                    val scrollState = rememberScrollState()
                                    LaunchedEffect(output) { scrollState.animateScrollTo(scrollState.maxValue) }
                                    Text(
                                        text = annotatedOutput,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp)
                                            .verticalScroll(scrollState),
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 12.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = Color(0xFFCCCCCC)
                                        )
                                    )
                                }

                                // 输入行：提示符 + 真正的文本输入框（系统处理 Shift/CapsLock/IME）
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "> ",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 12.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = Color(0xFFCCCCCC)
                                        )
                                    )
                                    BasicTextField(
                                        value = pendingInput,
                                        onValueChange = { pendingInput = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(inputFocusRequester),
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontSize = 12.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = Color(0xFFCCCCCC)
                                        ),
                                        cursorBrush = SolidColor(Color(0xFFCCCCCC)),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(onSend = { sendPendingInput() }),
                                        decorationBox = { innerTextField ->
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                if (pendingInput.isEmpty()) {
                                                    Text(
                                                        text = "向运行中的命令发送输入…",
                                                        style = androidx.compose.ui.text.TextStyle(
                                                            fontSize = 12.sp,
                                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                            color = Color(0xFF6E6E6E)
                                                        )
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = { if (running) OutlinedButton(onClick = { TerminalSessionManager.stopCurrentProcess() }) { Text("取消") } })
        }
        PackageDialogState.RESULT -> AlertDialog(onDismissRequest = onDismiss, title = { Text(resultTitle, style = MaterialTheme.typography.titleLarge) },
            text = { Text(resultText, textAlign = TextAlign.Center) },
            confirmButton = { Button(onClick = onDismiss) { Text("完成") } })
    }
}