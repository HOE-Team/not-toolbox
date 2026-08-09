// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package main.kotlin

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.window.singleWindowApplication
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import components.MaterialSymbols
import components.AppScaffold
import components.NavRail
import screens.HomeScreen
import screens.ToolsScreen
import screens.SettingsScreen
import screens.AboutScreen
import screens.TerminalScreen
import theme.AppTheme
import config.loadConfig
import config.saveConfig
import config.AppConfig
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.awt.Dimension
import java.time.LocalTime
import utils.PackageManagerType
import utils.PackageManagerUtils
import utils.TerminalSessionManager

// 编译时常量：true=启用本地DEBUG包列表，false=从远程拉取
const val IS_DEBUG = false

/**
 * 根据当前时间生成问候语，并尝试获取系统用户名。
 * 首页 TopBar 显示"早上/中午/晚上好，<用户名>"；若获取不到用户名，用"用户"代称。
 */
private fun greetingForOverview(customName: String?): String {
    val hour = LocalTime.now().hour
    val greeting = when {
        hour in 5..11 -> "早上好"
        hour in 12..13 -> "中午好"
        hour in 14..17 -> "下午好"
        else -> "晚上好"
    }
    // 自定义称谓优先；未设置则用系统用户名；再不行用"用户"
    val name = customName?.trim().takeIf { it.isNullOrBlank().not() }
        ?: System.getProperty("user.name")?.trim().takeIf { !it.isNullOrBlank() }
        ?: "用户"
    return "$greeting，$name"
}

@OptIn(ExperimentalMaterial3Api::class)
fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 600.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "NOT Toolbox",
        icon = painterResource("img/logo.png"),
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

        // load persisted settings
        val loaded = loadConfig()
        var isDark by remember { mutableStateOf(loaded.dark) }
        var seedHex by remember { mutableStateOf<String?>(loaded.color) }
        var useProxy by remember { mutableStateOf(loaded.useProxy) }
        var proxyUrl by remember { mutableStateOf(loaded.proxyUrl) }
        // 包管理器选择状态（默认自动检测）
        var selectedPackageManager by remember { mutableStateOf(PackageManagerUtils.detectPackageManager()) }
        // 终端编码设置
        var terminalEncoding by remember { mutableStateOf(loaded.terminalEncoding) }
        // 自定义称谓（主页 TopBar 对用户的称呼）
        var displayName by remember { mutableStateOf(loaded.displayName ?: "") }

        // 初始化 TerminalSessionManager 的编码
        TerminalSessionManager.setEncoding(terminalEncoding)

        val topBarTitle = when (selectedNavIndex) {
            1 -> "工具"
            2 -> "终端"
            3 -> "设置"
            4 -> "关于"
            else -> greetingForOverview(displayName)
        }

        AppTheme(darkTheme = isDark, seedHex = seedHex) {
            AppScaffold(
                startBar = { NavRail(selectedIndex = selectedNavIndex, onSelection = { selectedNavIndex = it }) },
                topBarTitle = topBarTitle,
                topBarActions = {
                    if (selectedNavIndex == 1) {
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
                        onNavigateToTerminal = { selectedNavIndex = 2 }
                    )
                    2 -> TerminalScreen()
                    3 -> SettingsScreen(
                        isDarkTheme = isDark,
                        onThemeChange = { newDark ->
                            isDark = newDark
                            saveConfig(AppConfig(dark = isDark, color = seedHex, useProxy = useProxy, proxyUrl = proxyUrl, terminalEncoding = terminalEncoding))
                        },
                        selectedColor = seedHex ?: "",
                        onColorChange = { hex ->
                            seedHex = if (hex.isBlank()) null else hex
                            saveConfig(AppConfig(dark = isDark, color = seedHex, useProxy = useProxy, proxyUrl = proxyUrl, terminalEncoding = terminalEncoding))
                        },
                        selectedPackageManager = selectedPackageManager,
                        onPackageManagerChange = { selectedPackageManager = it },
                        useProxy = useProxy,
                        onUseProxyChange = { newUseProxy ->
                            useProxy = newUseProxy
                            saveConfig(AppConfig(dark = isDark, color = seedHex, useProxy = useProxy, proxyUrl = proxyUrl, terminalEncoding = terminalEncoding))
                        },
                        proxyUrl = proxyUrl,
                        onProxyUrlChange = { newProxyUrl ->
                            proxyUrl = newProxyUrl
                            saveConfig(AppConfig(dark = isDark, color = seedHex, useProxy = useProxy, proxyUrl = proxyUrl, terminalEncoding = terminalEncoding))
                        },
                        terminalEncoding = terminalEncoding,
                        onTerminalEncodingChange = { newEncoding ->
                            terminalEncoding = newEncoding
                            TerminalSessionManager.setEncoding(newEncoding)
                            saveConfig(AppConfig(dark = isDark, color = seedHex, useProxy = useProxy, proxyUrl = proxyUrl, terminalEncoding = newEncoding))
                        },
                        displayName = displayName,
                        onDisplayNameChange = { newName ->
                            displayName = newName
                            saveConfig(AppConfig(dark = isDark, color = seedHex, useProxy = useProxy, proxyUrl = proxyUrl, terminalEncoding = terminalEncoding, displayName = newName))
                        }
                    )
                    4 -> AboutScreen()
                    else -> HomeScreen()
                }
            }
        }
    }
}