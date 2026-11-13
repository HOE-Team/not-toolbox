// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package main.kotlin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import components.AppScaffold
import components.BackgroundTaskDialog
import components.MaterialSymbols
import components.NavRail
import config.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ntb.generated.resources.Res
import ntb.generated.resources.logo
import org.jetbrains.compose.resources.painterResource
import screens.*
import screens.ai.AiChatState
import screens.ai.AiScreen
import theme.AppTheme
import theme.LocalWavyProgress
import utils.*
import java.awt.Dimension
import java.time.LocalTime

// 编译时常量：true=启用本地DEBUG包列表，false=从远程拉取
const val IS_DEBUG = false

/**
 * 根据当前时间生成问候语，并尝试获取系统用户名。
 * 首页 TopBar 显示"早上/中午/晚上好，<用户名>"；若获取不到用户名，用"用户"代称。
 */
private fun greetingForOverview(customName: String?): String {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "早上好"
        in 12..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }
    // 自定义称谓优先；未设置则用系统用户名；再不行用"用户"
    val name = customName?.trim().takeIf { it.isNullOrBlank().not() }
        ?: System.getProperty("user.name")?.trim().takeIf { !it.isNullOrBlank() }
        ?: "用户"
    return "$greeting，$name"
}

/**
 * 根据问候模式解析首页标题：
 * - DEFAULT：由 [greetingForOverview] 按时间生成问候 + 称谓；
 * - CUSTOM：原样显示用户自定义的整条问候语；若为空则回退默认，避免标题空白。
 */
private fun resolveGreeting(
    mode: GreetingMode,
    displayName: String?,
    customGreeting: String?
): String {
    if (mode == GreetingMode.CUSTOM) {
        val text = customGreeting?.trim()
        if (!text.isNullOrBlank()) return text
    }
    return greetingForOverview(displayName)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun main() = application {
    val windowState =  rememberWindowState(width = 1280.dp, height = 600.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "NOT Toolbox",
        icon = painterResource(Res.drawable.logo),
        state = windowState
    ) {
        val density = LocalDensity.current
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(
                with(density) { 800.dp.roundToPx() },
                with(density) { 600.dp.roundToPx() }
            )
        }

        var selectedNavIndex by remember { mutableStateOf(0) }
        var backgroundDialogVisible by remember { mutableStateOf(false) }
        val backgroundTasks by BackgroundTaskManager.tasks.collectAsState()
        LaunchedEffect(backgroundTasks.isEmpty()) {
            if (backgroundTasks.isEmpty()) backgroundDialogVisible = false
        }

        // 包页搜索状态：Search 按钮在 TopBar，搜索框在包页内容区
        var toolSearchVisible by remember { mutableStateOf(false) }
        var toolSearchQueryState by remember { mutableStateOf("") }
        // 包页的一级标签（0=推荐 / 1=快捷方式 / 2=包管理），提升到此处以便 TopBar 根据它隐藏按钮
        var toolSourceTab by remember { mutableStateOf(0) }
        // 顶栏刷新时递增，通知包管理页重新执行搜索
        var packageRefreshToken by remember { mutableStateOf(0) }

        // 加载已持久化的设置
        val loaded = loadConfig()
        var isDark by remember { mutableStateOf(loaded.dark) }
        var seedHex by remember { mutableStateOf(loaded.color) }
        var useDynamicColor by remember { mutableStateOf(loaded.useDynamicColor) }
        var useProxy by remember { mutableStateOf(loaded.useProxy) }
        var proxyUrl by remember { mutableStateOf(loaded.proxyUrl) }
        // 包管理器选择状态（默认自动检测）
        var selectedPackageManager by remember { mutableStateOf(PackageManagerUtils.detectPackageManager()) }
        // 终端编码设置
        var terminalEncoding by remember { mutableStateOf(loaded.terminalEncoding) }
        // 自定义称谓（主页 TopBar 对用户的称呼）
        var displayName by remember { mutableStateOf(loaded.displayName ?: "") }
        // 问候语模式（默认=DEFAULT；完全自定义=CUSTOM）
        var greetingMode by remember { mutableStateOf(loaded.greetingMode.ifBlank { "DEFAULT" }) }
        // 自定义整条问候语文本（greetingMode=CUSTOM 时使用）
        var customGreeting by remember { mutableStateOf(loaded.customGreeting ?: "") }
        // 壁纸设置
        var useCustomBg by remember { mutableStateOf(loaded.useCustomBg) }
        var customBgFile by remember { mutableStateOf(loaded.customBgFile) }
        // 波浪形进度指示器
        var useWavyProgress by remember { mutableStateOf(loaded.wavyProgress) }
        // 工具指令会话模式
        var toolCommandSessionMode by remember { mutableStateOf(loaded.toolCommandSession) }
        // 终端进程结束后是否立即结束会话
        var closeSessionOnEnd by remember { mutableStateOf(loaded.closeSessionOnEnd) }

        // 用于触发「重新检测已安装」等挂起操作
        val appScope = rememberCoroutineScope()

        // AI 会话状态挂在应用级作用域上，而不是挂在 AI 页面里：
        // - 切到其它页面再回来不再丢对话（此前状态由页面持有，一离开组合就被销毁）
        // - 生成过程中切页也不会把协程一起取消
        // 注意：对话只存在于内存中，**绝不落盘**（见 AiChatState 的类注释）。
        val aiChatState = remember { AiChatState(appScope) }

        // 初始化 TerminalSessionManager 的编码
        TerminalSessionManager.setEncoding(terminalEncoding)

        // 初始化 TerminalSessionManager 的会话路由模式
        TerminalSessionManager.setToolCommandSessionMode(ToolCommandSessionMode.fromName(toolCommandSessionMode))

        // 初始化 TerminalSessionManager 的进程结束后会话处理
        TerminalSessionManager.setCloseSessionOnEnd(closeSessionOnEnd)

        // 仅在"使用默认会话"模式下预先创建默认会话并设为活动；"每次新建会话"模式下初始无会话（终端页显示空状态）
        if (ToolCommandSessionMode.fromName(toolCommandSessionMode) == ToolCommandSessionMode.DEFAULT) {
            TerminalSessionManager.getOrCreateDefaultSession()
            TerminalSessionManager.setActiveSession(TerminalSessionManager.defaultSessionId)
        }

        // 写入壁纸运行时状态（供 SystemInfoProvider 读取）
        WallpaperState.useCustomBg = useCustomBg
        WallpaperState.customBgFileName = customBgFile

        // 壁纸动态取色：开启时定期检查壁纸是否变化，关闭时立即回到 HEX/默认色。
        var dynamicSeed by remember { mutableStateOf<Color?>(null) }
        LaunchedEffect(useDynamicColor, useCustomBg, customBgFile) {
            if (!useDynamicColor) {
                dynamicSeed = null
                return@LaunchedEffect
            }
            while (true) {
                dynamicSeed = withContext(Dispatchers.IO) {
                    DynamicColorProvider.currentSeed()
                }
                delay(30_000L)
            }
        }

        // 将所有设置持久化。集中为一处，避免各事件处理器里手写 AppConfig 遗漏字段
        // （例如漏掉 greetingMode/customGreeting 会在改其它设置时把问候语重置为默认）。
        fun persist() {
            saveConfig(
                AppConfig(
                    dark = isDark,
                    color = seedHex,
                    useDynamicColor = useDynamicColor,
                    useProxy = useProxy,
                    proxyUrl = proxyUrl,
                    terminalEncoding = terminalEncoding,
                    displayName = displayName,
                    greetingMode = greetingMode,
                    customGreeting = customGreeting,
                    useCustomBg = useCustomBg,
                    customBgFile = customBgFile,
                    wavyProgress = useWavyProgress,
                    toolCommandSession = toolCommandSessionMode,
                    closeSessionOnEnd = closeSessionOnEnd
                )
            )
        }

        val topBarTitle = when (selectedNavIndex) {
            1 -> "包"
            2 -> "终端"
            3 -> "AI助手"
            4 -> "设置"
            5 -> "关于"
            else -> resolveGreeting(GreetingMode.fromName(greetingMode), displayName, customGreeting)
        }

        AppTheme(
            darkTheme = isDark,
            seedHex = seedHex,
            dynamicSeed = if (useDynamicColor) dynamicSeed else null
        ) {
            CompositionLocalProvider(LocalWavyProgress provides useWavyProgress) {
            AppScaffold(
                startBar = {
                    NavRail(
                        selectedIndex = selectedNavIndex,
                        onSelection = { selectedNavIndex = it },
                        backgroundTaskActive = backgroundTasks.isNotEmpty(),
                        onBackgroundTaskClick = { backgroundDialogVisible = true }
                    )
                },
                topBarTitle = topBarTitle,
                topBarProgress = {
                    // 包 > 推荐 / 包管理的更新检查提示统一放在顶栏，其他页面不显示
                    val updateState by UpdateChecker.state.collectAsState()
                    // “推荐”页已经显示更新检查加载页时，不再重复显示顶栏进度；
                    // 用户选择“立即显示列表”后，才用顶栏指示器提示仍在检查。
                    val showPackageCheckProgress = selectedNavIndex == 1 &&
                        updateState.status == UpdateCheckStatus.CHECKING &&
                        (
                            toolSourceTab == 2 ||
                                (toolSourceTab == 0 && updateState.dismissed)
                            )
                    if (showPackageCheckProgress) {
                        if (LocalWavyProgress.current) {
                            LinearWavyProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                },
                topBarActions = {
                    if (selectedNavIndex == 1) {
                        // 包页：重新检测已安装的包，并重新检查可用更新
                        // 推荐和包管理页显示；快捷方式页隐藏
                        if (toolSourceTab == 0 || toolSourceTab == 2) {
                            FilledTonalIconButton(onClick = {
                                packageRefreshToken++
                                appScope.launch {
                                    val manager = if (selectedPackageManager != PackageManagerType.UNKNOWN)
                                        selectedPackageManager else PackageManagerUtils.detectPackageManager(force = true)
                                    PackageDetector.rescan(manager)
                                    // 包管理页由页面内的 refreshToken 效果负责强制重查，避免重复
                                    if (toolSourceTab != 2) {
                                        UpdateChecker.retryCheck(manager)
                                    }
                                }
                            }) {
                                Icon(MaterialSymbols.Refresh, "重新检测已安装")
                            }
                        }
                        if (toolSourceTab != 2) {
                            FilledTonalIconButton(onClick = {
                                toolSearchVisible = !toolSearchVisible
                                if (!toolSearchVisible) toolSearchQueryState = ""
                            }) {
                                Icon(
                                    if (toolSearchVisible) MaterialSymbols.Close else MaterialSymbols.Search,
                                    if (toolSearchVisible) "关闭搜索" else "搜索"
                                )
                            }
                        }
                    }
                    if (selectedNavIndex == 2) {
                        // 终端页：新建会话按钮
                        FilledTonalIconButton(onClick = {
                            TerminalSessionManager.createAndActivateSession()
                        }) {
                            Icon(MaterialSymbols.Add, "新建会话")
                        }
                        // 终端页：清除所有标签页按钮
                        FilledTonalIconButton(
                            onClick = {
                                TerminalSessionManager.clearAllSessions()
                            },
                            enabled = TerminalSessionManager.sessions.isNotEmpty()
                        ) {
                            Icon(MaterialSymbols.Delete, "清除所有标签页")
                        }
                    }
                }
            ) {
                when (selectedNavIndex) {
                    1 -> ToolsScreen(
                        selectedPackageManager = selectedPackageManager, 
                        isDebug = IS_DEBUG,
                        useProxy = useProxy,
                        proxyUrl = proxyUrl,
                        searchVisible = toolSearchVisible,
                        searchQuery = toolSearchQueryState,
                        onSearchQueryChange = { toolSearchQueryState = it },
                        sourceTab = toolSourceTab,
                        packageRefreshToken = packageRefreshToken,
                        onSourceTabChange = { newTab ->
                            toolSourceTab = newTab
                            if (newTab == 2) {
                                toolSearchVisible = false
                                toolSearchQueryState = ""
                            }
                        },
                        onNavigateToTerminal = { selectedNavIndex = 2 }
                    )
                    2 -> TerminalScreen()
                    3 -> AiScreen(
                        state = aiChatState,
                        selectedPackageManager = selectedPackageManager,
                        useProxy = useProxy,
                        proxyUrl = proxyUrl,
                        isDebug = IS_DEBUG,
                        onNavigateToTerminal = { selectedNavIndex = 2 }
                    )
                    4 -> SettingsScreen(
                        isDarkTheme = isDark,
                        onThemeChange = { newDark ->
                            isDark = newDark
                            persist()
                        },
                        selectedColor = seedHex ?: "",
                        onColorChange = { hex ->
                            seedHex = hex.ifBlank { null }
                            persist()
                        },
                        useDynamicColor = useDynamicColor,
                        onUseDynamicColorChange = { enabled ->
                            useDynamicColor = enabled
                            persist()
                        },
                        selectedPackageManager = selectedPackageManager,
                        onPackageManagerChange = { selectedPackageManager = it },
                        useProxy = useProxy,
                        onUseProxyChange = { newUseProxy ->
                            useProxy = newUseProxy
                            persist()
                        },
                        proxyUrl = proxyUrl,
                        onProxyUrlChange = { newProxyUrl ->
                            proxyUrl = newProxyUrl
                            persist()
                        },
                        terminalEncoding = terminalEncoding,
                        onTerminalEncodingChange = { newEncoding ->
                            terminalEncoding = newEncoding
                            TerminalSessionManager.setEncoding(newEncoding)
                            persist()
                        },
                        displayName = displayName,
                        onDisplayNameChange = { newName ->
                            displayName = newName
                            persist()
                        },
                        greetingMode = greetingMode,
                        onGreetingModeChange = { newMode ->
                            greetingMode = newMode
                            persist()
                        },
                        customGreeting = customGreeting,
                        onCustomGreetingChange = { newText ->
                            customGreeting = newText
                            persist()
                        },
                        useCustomBg = useCustomBg,
                        onUseCustomBgChange = { newVal ->
                            useCustomBg = newVal
                            WallpaperState.useCustomBg = newVal
                            persist()
                        },
                        customBgFile = customBgFile,
                        onCustomBgFileChange = { newFile ->
                            // 更换或撤下背景时，删除上一次设置的背景图像在 cardbg/ 目录中的缓存
                            val oldFile = customBgFile
                            if (oldFile != null && oldFile != newFile) {
                                CardBgManager.deleteImage(oldFile)
                            }
                            customBgFile = newFile
                            WallpaperState.customBgFileName = newFile
                            persist()
                        },
                        useWavyProgress = useWavyProgress,
                        onUseWavyProgressChange = { newVal ->
                            useWavyProgress = newVal
                            persist()
                        },
                        toolCommandSessionMode = toolCommandSessionMode,
                        onToolCommandSessionModeChange = { newMode ->
                            toolCommandSessionMode = newMode
                            TerminalSessionManager.setToolCommandSessionMode(ToolCommandSessionMode.fromName(newMode))
                            persist()
                        },
                        closeSessionOnEnd = closeSessionOnEnd,
                        onCloseSessionOnEndChange = { newVal ->
                            closeSessionOnEnd = newVal
                            TerminalSessionManager.setCloseSessionOnEnd(newVal)
                            persist()
                        }
                    )
                    5 -> AboutScreen()
                    else -> HomeScreen()
                }
            }
            }
            if (backgroundDialogVisible && backgroundTasks.isNotEmpty()) {
                BackgroundTaskDialog(
                    tasks = backgroundTasks,
                    onClose = {
                        backgroundDialogVisible = false
                        BackgroundTaskManager.clearFinished()
                    },
                    onCancel = { id -> BackgroundTaskManager.cancel(id) },
                    onRemove = { id -> BackgroundTaskManager.remove(id) }
                )
            }
        }
    }
}