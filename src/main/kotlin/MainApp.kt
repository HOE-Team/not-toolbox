// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package main.kotlin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import components.AppScaffold
import components.MaterialSymbols
import components.NavRail
import config.*
import kotlinx.coroutines.launch
import ntb.generated.resources.Res
import ntb.generated.resources.logo
import org.jetbrains.compose.resources.painterResource
import screens.*
import theme.AppTheme
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

        // 工具页搜索状态：Search 按钮在 TopBar，搜索框在工具页内容区
        var toolSearchVisible by remember { mutableStateOf(false) }
        var toolSearchQueryState by remember { mutableStateOf("") }
        // 工具页的一级标签（0=联机 / 1=本地），提升到此处以便 TopBar 根据它隐藏按钮
        var toolSourceTab by remember { mutableStateOf(0) }

        // load persisted settings
        val loaded = loadConfig()
        var isDark by remember { mutableStateOf(loaded.dark) }
        var seedHex by remember { mutableStateOf(loaded.color) }
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
        // 工具指令会话模式
        var toolCommandSessionMode by remember { mutableStateOf(loaded.toolCommandSession) }
        // 终端进程结束后是否立即结束会话
        var closeSessionOnEnd by remember { mutableStateOf(loaded.closeSessionOnEnd) }

        // 用于触发「重新检测已安装」等挂起操作
        val appScope = rememberCoroutineScope()

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

        // 将所有设置持久化。集中为一处，避免各事件处理器里手写 AppConfig 遗漏字段
        // （例如漏掉 greetingMode/customGreeting 会在改其它设置时把问候语重置为默认）。
        fun persist() {
            saveConfig(
                AppConfig(
                    dark = isDark,
                    color = seedHex,
                    useProxy = useProxy,
                    proxyUrl = proxyUrl,
                    terminalEncoding = terminalEncoding,
                    displayName = displayName,
                    greetingMode = greetingMode,
                    customGreeting = customGreeting,
                    useCustomBg = useCustomBg,
                    customBgFile = customBgFile,
                    toolCommandSession = toolCommandSessionMode,
                    closeSessionOnEnd = closeSessionOnEnd
                )
            )
        }

        val topBarTitle = when (selectedNavIndex) {
            1 -> "工具"
            2 -> "终端"
            3 -> "设置"
            4 -> "AI助手"
            5 -> "关于"
            else -> resolveGreeting(GreetingMode.fromName(greetingMode), displayName, customGreeting)
        }

        AppTheme(darkTheme = isDark, seedHex = seedHex) {
            AppScaffold(
                startBar = { NavRail(selectedIndex = selectedNavIndex, onSelection = { selectedNavIndex = it }) },
                topBarTitle = topBarTitle,
                topBarProgress = {
                    // 用户点了「立即显示列表」后，用非确定性线性指示器继续提示后台仍在检查
                    val updateState by UpdateChecker.state.collectAsState()
                    if (updateState.status == UpdateCheckStatus.CHECKING && updateState.dismissed) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                },
                topBarActions = {
                    if (selectedNavIndex == 1) {
                        // 工具页：重新检测已安装的包，并重新检查可用更新
                        // 切到「本地」（离线）标签页时隐藏该按钮
                        if (toolSourceTab == 0) {
                            IconButton(onClick = {
                                appScope.launch {
                                    val manager = if (selectedPackageManager != PackageManagerType.UNKNOWN)
                                        selectedPackageManager else PackageManagerUtils.detectPackageManager(force = true)
                                    PackageDetector.rescan(manager)
                                    UpdateChecker.retryCheck(manager)
                                }
                            }) {
                                Icon(MaterialSymbols.Refresh, "重新检测已安装")
                            }
                        }
                        IconButton(onClick = {
                            toolSearchVisible = !toolSearchVisible
                            if (!toolSearchVisible) toolSearchQueryState = ""
                        }) {
                            Icon(
                                if (toolSearchVisible) MaterialSymbols.Close else MaterialSymbols.Search,
                                if (toolSearchVisible) "关闭搜索" else "搜索"
                            )
                        }
                    }
                    if (selectedNavIndex == 2) {
                        // 终端页：新建会话按钮
                        IconButton(onClick = {
                            TerminalSessionManager.createAndActivateSession()
                        }) {
                            Icon(MaterialSymbols.Add, "新建会话")
                        }
                        // 终端页：清除所有标签页按钮
                        IconButton(
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
                        onSourceTabChange = { toolSourceTab = it },
                        onNavigateToTerminal = { selectedNavIndex = 2 }
                    )
                    2 -> TerminalScreen()
                    3 -> SettingsScreen(
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
                    4 -> AiScreen(
                        selectedPackageManager = selectedPackageManager,
                        useProxy = useProxy,
                        proxyUrl = proxyUrl,
                        isDebug = IS_DEBUG,
                        onNavigateToTerminal = { selectedNavIndex = 2 }
                    )
                    5 -> AboutScreen()
                    else -> HomeScreen()
                }
            }
        }
    }
}