// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import components.ExpressiveEmptyState
import components.ExpressiveLoadingState
import components.MaterialSymbols
import components.StatusPill
import components.TonalIcon
import kotlinx.coroutines.launch
import utils.BackgroundTaskManager
import utils.InstalledSnapshot
import utils.PackageDetector
import utils.PackageInfo
import utils.PackageManagerType
import utils.PackageManagerUtils
import utils.PackageSearch
import utils.PackageSearchOutcome
import utils.PackageSearchResult
import utils.TerminalSessionManager
import utils.UpdateCheckState
import utils.UpdateCheckStatus
import utils.UpdateChecker

/** 包管理页的单行数据：已安装包或搜索结果中的可安装包。 */
private data class PackageManagementEntry(
    val displayName: String,
    val nativeName: String,
    val version: String?,
    val description: String?,
    val installed: Boolean
)

private fun entryMatches(entry: PackageManagementEntry, query: String): Boolean {
    if (query.isBlank()) return true
    return entry.displayName.contains(query, ignoreCase = true) ||
        entry.nativeName.contains(query, ignoreCase = true) ||
        entry.description?.contains(query, ignoreCase = true) == true
}

/**
 * “包管理”大类。
 *
 * - 默认展示当前包管理器的已安装包；
 * - 点击搜索按钮后才调用包管理器自身的搜索命令（不再从推荐列表里过滤）；
 * - 已安装包支持升级/卸载，搜索结果支持安装；
 * - refreshToken 变化时重新执行当前搜索，并配合父级的 PackageDetector 重新检测。
 */
@Composable
fun PackageManagementTab(
    catalog: List<PackageInfo>,
    manager: PackageManagerType,
    snapshot: InstalledSnapshot?,
    isDetecting: Boolean,
    refreshToken: Int,
    detectionMessage: String? = null,
    modifier: Modifier = Modifier
) {
    var queryText by remember(manager) { mutableStateOf("") }
    var submittedQuery by remember(manager) { mutableStateOf("") }
    var searchTrigger by remember(manager) { mutableIntStateOf(0) }
    var searching by remember(manager) { mutableStateOf(false) }
    var searchResults by remember(manager) { mutableStateOf<List<PackageSearchResult>>(emptyList()) }
    var searchError by remember(manager) { mutableStateOf<String?>(null) }
    var pendingGlobalUpdate by remember(manager) { mutableStateOf(false) }
    var globalUpdateSucceeded by remember(manager) { mutableStateOf(false) }
    var refreshing by remember(manager) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val updateAllCommand = remember(manager) { PackageManagerUtils.getUpdateCommand(manager) }
    val checkState by UpdateChecker.state.collectAsState()
    val sameManager = checkState.manager == manager
    val checkingUpdates = sameManager && checkState.status == UpdateCheckStatus.CHECKING
    val updatesReady = sameManager &&
        checkState.status == UpdateCheckStatus.READY &&
        checkState.supported &&
        !checkState.failed
    val checkPending = refreshing || !sameManager || checkState.status == UpdateCheckStatus.IDLE
    val hasAnyUpdates = updatesReady && checkState.updatable.isNotEmpty()

    // 进入“包管理”页或点击顶栏刷新时立即重查；在结果返回前不显示更新按钮。
    LaunchedEffect(refreshToken, manager) {
        if (manager == PackageManagerType.UNKNOWN) {
            refreshing = false
            return@LaunchedEffect
        }
        refreshing = true
        try {
            PackageDetector.detectIfNeeded(manager)
            UpdateChecker.retryCheck(manager)
        } finally {
            refreshing = false
        }
    }

    fun submitSearch() {
        val q = queryText.trim()
        if (q.isBlank()) {
            submittedQuery = ""
            searchResults = emptyList()
            searchError = null
            return
        }
        submittedQuery = q
        searchTrigger++
    }

    fun clearSearch() {
        queryText = ""
        submittedQuery = ""
        searchResults = emptyList()
        searchError = null
    }

    LaunchedEffect(submittedQuery, searchTrigger, refreshToken, manager) {
        if (submittedQuery.isBlank() || manager == PackageManagerType.UNKNOWN) {
            searching = false
            searchResults = emptyList()
            searchError = null
            return@LaunchedEffect
        }

        searching = true
        searchError = null
        when (val outcome = PackageSearch.search(manager, submittedQuery)) {
            is PackageSearchOutcome.Success -> searchResults = outcome.results
            is PackageSearchOutcome.Failed -> {
                searchResults = emptyList()
                searchError = outcome.message
            }
            PackageSearchOutcome.Unsupported -> {
                searchResults = emptyList()
                searchError = "当前包管理器不支持搜索。"
            }
        }
        searching = false
    }

    val activeQuery = submittedQuery.trim()

    val catalogByNative = remember(catalog, manager) {
        catalog.mapNotNull { pkg ->
            val native = pkg.getPackageNameForManager(manager)
                ?.takeIf { it.isNotBlank() }
                ?.lowercase()
                ?: return@mapNotNull null
            native to pkg
        }.toMap()
    }

    val installedEntries = remember(snapshot, catalogByNative, activeQuery) {
        snapshot?.packages.orEmpty()
            .map { (native, entry) ->
                val info = catalogByNative[native]
                PackageManagementEntry(
                    displayName = info?.name?.takeIf { it.isNotBlank() } ?: native,
                    nativeName = native,
                    version = entry.version,
                    description = info?.description,
                    installed = true
                )
            }
            .filter { entryMatches(it, activeQuery) }
            .sortedBy { it.displayName.lowercase() }
    }

    val installedNames = remember(snapshot) {
        snapshot?.packages?.keys.orEmpty()
    }

    val searchEntries = remember(searchResults, installedNames, activeQuery) {
        searchResults
            .filter { it.packageName.lowercase() !in installedNames }
            .map {
                PackageManagementEntry(
                    displayName = it.name,
                    nativeName = it.packageName,
                    version = it.version,
                    description = it.description,
                    installed = false
                )
            }
            .filter { entryMatches(it, activeQuery) }
            .distinctBy { it.nativeName.lowercase() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入关键词，点击搜索查询包管理器") },
                leadingIcon = { Icon(MaterialSymbols.Search, contentDescription = "搜索") },
                trailingIcon = {
                    if (queryText.isNotEmpty()) {
                        IconButton(onClick = { clearSearch() }) {
                            Icon(MaterialSymbols.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                enabled = !searching
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = { submitSearch() },
                enabled = queryText.isNotBlank() && !searching && manager != PackageManagerType.UNKNOWN,
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                if (searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        MaterialSymbols.Search,
                        contentDescription = "搜索",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (manager == PackageManagerType.UNKNOWN) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ExpressiveEmptyState(
                    icon = MaterialSymbols.Package,
                    title = "未检测到包管理器",
                    message = "请先在设置中选择可用的包管理器。",
                    modifier = Modifier.widthIn(max = 460.dp)
                )
            }
            return@Column
        }

        if (snapshot == null && isDetecting) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ExpressiveLoadingState(
                    label = "正在读取已安装的包",
                    detail = "首次读取可能需要几秒钟",
                    modifier = Modifier.widthIn(max = 460.dp)
                )
            }
            return@Column
        }

        if (snapshot == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ExpressiveEmptyState(
                    icon = MaterialSymbols.Package,
                    title = "无法读取已安装列表",
                    message = detectionMessage ?: "包管理器没有返回已安装列表，请稍后重试。",
                    modifier = Modifier.widthIn(max = 460.dp)
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已安装",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusPill(text = installedEntries.size.toString())
                    if (hasAnyUpdates) {
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusPill(
                            text = "可升级 " + checkState.updatable.size,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    when {
                        checkingUpdates || checkPending -> Text(
                            text = "正在检查更新…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        hasAnyUpdates && updateAllCommand != null -> {
                            FilledTonalButton(
                                onClick = {
                                    globalUpdateSucceeded = false
                                    pendingGlobalUpdate = true
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(MaterialSymbols.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("全部更新", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            if (installedEntries.isEmpty()) {
                item {
                    Text(
                        text = if (activeQuery.isBlank()) "没有读取到已安装的包。" else "没有匹配的已安装包。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(installedEntries, key = { "installed:" + it.nativeName }) { entry ->
                    PackageManagementRow(entry = entry, manager = manager, checkState = checkState)
                }
            }

            if (activeQuery.isNotBlank()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "搜索结果",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusPill(text = searchEntries.size.toString())
                    }
                }

                when {
                    searching -> item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "正在调用包管理器搜索…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    searchError != null -> item {
                        Text(
                            text = searchError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    searchEntries.isEmpty() -> item {
                        Text(
                            text = "没有找到可安装的软件包。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    else -> items(searchEntries, key = { "search:" + it.nativeName }) { entry ->
                        PackageManagementRow(entry = entry, manager = manager, checkState = checkState)
                    }
                }
            } else {
                item {
                    Text(
                        text = "输入关键词并点击搜索，查询包管理器中的软件包。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (pendingGlobalUpdate && updateAllCommand != null) {
        PackageActionDialog(
            toolName = "全部软件包",
            action = PackageAction.UPDATE,
            command = updateAllCommand,
            onDismiss = {
                pendingGlobalUpdate = false
                if (globalUpdateSucceeded) {
                    globalUpdateSucceeded = false
                    scope.launch {
                        PackageDetector.rescanSilently(manager)
                        UpdateChecker.recheckSilently(manager)
                    }
                }
            },
            onConfirm = {
                TerminalSessionManager.executePackageCommandAndWait(updateAllCommand, "包管理 · 全部软件包")
            },
            onSucceeded = { globalUpdateSucceeded = true },
            onBackground = { sid ->
                BackgroundTaskManager.start(
                    title = "全部软件包",
                    actionLabel = "更新",
                    command = updateAllCommand,
                    sessionId = sid,
                    onSuccess = {
                        PackageDetector.rescanSilently(manager)
                        UpdateChecker.recheckSilently(manager)
                    }
                )
            }
        )
    }
}

@Composable
private fun PackageManagementRow(
    entry: PackageManagementEntry,
    manager: PackageManagerType,
    checkState: UpdateCheckState
) {
    var pendingAction by remember(entry.nativeName) { mutableStateOf<PackageAction?>(null) }
    var succeeded by remember(entry.nativeName) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val installCommand = PackageManagerUtils.getInstallCommand(manager, entry.nativeName)
    val updateCommand = PackageManagerUtils.getPackageUpdateCommand(manager, entry.nativeName)
    val uninstallCommand = PackageManagerUtils.getUninstallCommand(manager, entry.nativeName)
    val command = when (pendingAction) {
        PackageAction.INSTALL -> installCommand
        PackageAction.UPDATE -> updateCommand
        PackageAction.UNINSTALL -> uninstallCommand
        null -> null
    }

    val sameManager = checkState.manager == manager
    val checkingUpdates = sameManager && checkState.status == UpdateCheckStatus.CHECKING
    val updatesReady = sameManager &&
        checkState.status == UpdateCheckStatus.READY &&
        checkState.supported &&
        !checkState.failed
    val checkPending = !sameManager || checkState.status == UpdateCheckStatus.IDLE
    val checkUnsupported = sameManager &&
        checkState.status == UpdateCheckStatus.READY &&
        !checkState.supported
    val checkFailed = sameManager &&
        checkState.status == UpdateCheckStatus.READY &&
        checkState.failed
    val hasUpdate = updatesReady && entry.installed &&
        entry.nativeName.lowercase() in checkState.updatable
    val upToDate = updatesReady && entry.installed && !hasUpdate
    val targetVersion = if (entry.installed && sameManager) {
        checkState.available[entry.nativeName.lowercase()]
    } else {
        null
    }
    val updateStatusText = when {
        checkPending || checkingUpdates -> "正在检查更新…"
        hasUpdate -> targetVersion?.let { "可升级至 $it" } ?: "有可用更新"
        upToDate -> "已是最新"
        checkUnsupported -> "不支持更新检查"
        checkFailed -> "更新检查失败"
        else -> "更新状态未知"
    }
    val updateStatusContainer = when {
        hasUpdate -> MaterialTheme.colorScheme.tertiaryContainer
        upToDate -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val updateStatusContent = when {
        hasUpdate -> MaterialTheme.colorScheme.onTertiaryContainer
        upToDate -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TonalIcon(
                icon = if (entry.installed) MaterialSymbols.Package else MaterialSymbols.Apps,
                contentDescription = null,
                size = 42.dp,
                containerColor = if (entry.installed) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (entry.installed) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(entry.nativeName)
                        if (!entry.version.isNullOrBlank()) {
                            append(" · ")
                            append(entry.version)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.description.isNullOrBlank()) {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (entry.installed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusPill(
                        text = updateStatusText,
                        containerColor = updateStatusContainer,
                        contentColor = updateStatusContent
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (entry.installed) {
                if (hasUpdate && updateCommand != null) {
                    FilledTonalButton(
                        onClick = {
                            succeeded = false
                            pendingAction = PackageAction.UPDATE
                        },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("更新", fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                OutlinedButton(
                    onClick = {
                        succeeded = false
                        pendingAction = PackageAction.UNINSTALL
                    },
                    enabled = uninstallCommand != null,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(MaterialSymbols.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("卸载", fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = {
                        succeeded = false
                        pendingAction = PackageAction.INSTALL
                    },
                    enabled = installCommand != null,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(MaterialSymbols.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("安装", fontSize = 13.sp)
                }
            }
        }
    }

    val action = pendingAction
    if (action != null && command != null) {
        PackageActionDialog(
            toolName = entry.displayName,
            action = action,
            command = command,
            onDismiss = {
                pendingAction = null
                if (succeeded) {
                    succeeded = false
                    scope.launch {
                        when (action) {
                            PackageAction.INSTALL ->
                                PackageDetector.onPackageInstalled(manager, entry.nativeName)
                            PackageAction.UNINSTALL ->
                                PackageDetector.onPackageUninstalled(manager, entry.nativeName)
                            PackageAction.UPDATE -> {
                                PackageDetector.rescanSilently(manager)
                                UpdateChecker.recheckSilently(manager)
                            }
                        }
                    }
                }
            },
            onConfirm = {
                TerminalSessionManager.executePackageCommandAndWait(
                    command,
                    "包管理 · " + entry.displayName
                )
            },
            onSucceeded = { succeeded = true },
            onBackground = { sid ->
                BackgroundTaskManager.start(
                    title = entry.displayName,
                    actionLabel = when (action) {
                        PackageAction.INSTALL -> "安装"
                        PackageAction.UPDATE -> "更新"
                        PackageAction.UNINSTALL -> "卸载"
                    },
                    command = command,
                    sessionId = sid,
                    onSuccess = {
                        when (action) {
                            PackageAction.INSTALL ->
                                PackageDetector.onPackageInstalled(manager, entry.nativeName)
                            PackageAction.UNINSTALL ->
                                PackageDetector.onPackageUninstalled(manager, entry.nativeName)
                            PackageAction.UPDATE -> {
                                PackageDetector.rescanSilently(manager)
                                UpdateChecker.recheckSilently(manager)
                            }
                        }
                    }
                )
            }
        )
    }
}
