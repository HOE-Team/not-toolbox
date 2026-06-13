// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package main.kotlin

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.window.singleWindowApplication
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.WindowState
import java.awt.Dimension
import utils.PackageManagerType
import utils.PackageManagerUtils
import utils.TerminalSessionManager

// 编译时常量：true=启用本地DEBUG包列表，false=从远程拉取
const val IS_DEBUG = false

@OptIn(ExperimentalMaterial3Api::class)
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "NOT Toolbox",
        icon = painterResource("img/logo.png"),
        state = WindowState()
    ) {
        var selectedNavIndex by remember { mutableStateOf(0) }

        val topBarTitle = when (selectedNavIndex) {
            1 -> "工具"
            2 -> "终端"
            3 -> "设置"
            4 -> "关于"
            else -> "概览"
        }

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
        
        // 初始化 TerminalSessionManager 的编码
        TerminalSessionManager.setEncoding(terminalEncoding)

        AppTheme(darkTheme = isDark, seedHex = seedHex) {
            AppScaffold(
                startBar = { NavRail(onSelection = { selectedNavIndex = it }) },
                topBarTitle = topBarTitle
            ) {
                when (selectedNavIndex) {
                    1 -> ToolsScreen(
                        selectedPackageManager = selectedPackageManager, 
                        isDebug = IS_DEBUG,
                        useProxy = useProxy,
                        proxyUrl = proxyUrl
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
                        }
                    )
                    4 -> AboutScreen()
                    else -> HomeScreen()
                }
            }
        }
    }
}
