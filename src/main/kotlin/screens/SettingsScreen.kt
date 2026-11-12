// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import components.ExpressivePillTabs
import components.MaterialSymbols
import components.PillTab
import components.TonalIcon
import config.GreetingMode
import config.TerminalEncoding
import config.ToolCommandSessionMode
import theme.isValidHex
import utils.CardBgManager
import utils.PackageManagerType
import utils.PackageManagerUtils
import java.awt.FileDialog

/**
 * 设置页（Material 3 Expressive）。
 *
 * 原来的「一条长列表 + 分割线」改为「分区卡片」：
 * 每个分区有图标井、标题与可选说明，设置项以行内 trailing 控件呈现。
 * 所有设置项与回调逻辑保持不变，只改变视觉层级。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean = false,
    onThemeChange: (Boolean) -> Unit = {},
    selectedColor: String = "",
    onColorChange: (String) -> Unit = {},
    selectedPackageManager: PackageManagerType = PackageManagerType.UNKNOWN,
    onPackageManagerChange: (PackageManagerType) -> Unit = {},
    useProxy: Boolean = false,
    onUseProxyChange: (Boolean) -> Unit = {},
    proxyUrl: String = "https://ghproxy.net",
    onProxyUrlChange: (String) -> Unit = {},
    terminalEncoding: String = "UTF-8",
    onTerminalEncodingChange: (String) -> Unit = {},
    displayName: String = "",
    onDisplayNameChange: (String) -> Unit = {},
    greetingMode: String = "DEFAULT",
    onGreetingModeChange: (String) -> Unit = {},
    customGreeting: String = "",
    onCustomGreetingChange: (String) -> Unit = {},
    useCustomBg: Boolean = false,
    onUseCustomBgChange: (Boolean) -> Unit = {},
    customBgFile: String? = null,
    onCustomBgFileChange: (String?) -> Unit = {},
    useWavyProgress: Boolean = false,
    onUseWavyProgressChange: (Boolean) -> Unit = {},
    toolCommandSessionMode: String = "NEW",
    onToolCommandSessionModeChange: (String) -> Unit = {},
    closeSessionOnEnd: Boolean = false,
    onCloseSessionOnEndChange: (Boolean) -> Unit = {}
) {
    var localDarkTheme by remember { mutableStateOf(isDarkTheme) }
    var hexInput by remember { mutableStateOf(selectedColor) }
    var saveStateMessage by remember { mutableStateOf<String?>(null) }
    var displayNameInput by remember(displayName) { mutableStateOf(displayName) }
    var greetingModeInput by remember(greetingMode) { mutableStateOf(greetingMode) }
    var customGreetingInput by remember(customGreeting) { mutableStateOf(customGreeting) }

    // 检测当前平台和包管理器
    val osName = remember { System.getProperty("os.name").lowercase() }
    val isWindows = remember { osName.contains("windows") }
    val detectedPackageManager by remember { mutableStateOf(PackageManagerUtils.detectPackageManager()) }
    val availableManagers by remember { mutableStateOf(PackageManagerUtils.getAvailablePackageManagers()) }
    val allPlatformManagers by remember { mutableStateOf(PackageManagerUtils.getAllPlatformPackageManagers()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        /* ------------------------------ 程序外观 ------------------------------ */
        SettingsSection(
            title = "程序外观",
            subtitle = "主题、强调色与概览背景",
            icon = MaterialSymbols.Palette
        ) {
            SettingsRow(
                icon = MaterialSymbols.DarkMode,
                title = "深色主题",
                subtitle = "在浅色与深色配色之间切换"
            ) {
                Switch(
                    checked = localDarkTheme,
                    onCheckedChange = { newValue ->
                        localDarkTheme = newValue
                        onThemeChange(newValue)
                    }
                )
            }

            SettingsRow(
                icon = MaterialSymbols.Animation,
                title = "波浪形进度指示器",
                subtitle = "开启后程序内的状态指示器都将被更新为波浪形"
            ) {
                Switch(
                    checked = useWavyProgress,
                    onCheckedChange = onUseWavyProgressChange
                )
            }

            val isValid = remember(hexInput) { isValidHex(hexInput.trim()) }
            SettingsRow(
                icon = MaterialSymbols.Palette,
                title = "颜色主题 (HEX)",
                subtitle = "输入 6 位 HEX，例如：#6750A4 或 6750A4"
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { hexInput = it; saveStateMessage = null },
                            singleLine = true,
                            isError = hexInput.isNotBlank() && !isValid,
                            placeholder = { Text("#RRGGBB") },
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.width(160.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val cleaned = hexInput.trim()
                                if (cleaned.isNotBlank() && isValid) {
                                    onColorChange(if (cleaned.startsWith("#")) cleaned else "#$cleaned")
                                    saveStateMessage = "已保存"
                                } else if (cleaned.isBlank()) {
                                    onColorChange("")
                                    saveStateMessage = "已重置为默认"
                                } else {
                                    saveStateMessage = "无效的 HEX"
                                }
                            },
                            shape = CircleShape
                        ) {
                            Text("保存")
                        }
                    }
                    saveStateMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (msg == "无效的 HEX") {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            SettingsRow(
                icon = MaterialSymbols.Wallpaper,
                title = "不获取桌面壁纸",
                subtitle = if (isWindows) "开启后概览卡片使用内置默认壁纸" else "Linux 下不可关闭"
            ) {
                Switch(
                    checked = useCustomBg,
                    onCheckedChange = { onUseCustomBgChange(it) },
                    enabled = isWindows
                )
            }

            if (useCustomBg || !isWindows) {
                SettingsRow(
                    icon = MaterialSymbols.Imagesmode,
                    title = "使用本地图像",
                    subtitle = if (customBgFile != null) "已选择本地图像" else "选择一张图片作为概览卡片背景"
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val dialog = FileDialog(null as java.awt.Frame?, "选择图片", FileDialog.LOAD)
                                dialog.isVisible = true
                                val dir = dialog.directory
                                val file = dialog.file
                                if (dir != null && file != null) {
                                    val path = java.io.File(dir, file).absolutePath
                                    val imported = CardBgManager.importImage(path)
                                    if (imported != null) {
                                        onCustomBgFileChange(imported)
                                    }
                                }
                            },
                            shape = CircleShape
                        ) {
                            Icon(MaterialSymbols.OpenInNew, "选择", Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (customBgFile != null) "更换" else "选择")
                        }
                        if (customBgFile != null) {
                            OutlinedButton(onClick = { onCustomBgFileChange(null) }, shape = CircleShape) {
                                Icon(MaterialSymbols.Close, "撤下", Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("撤下")
                            }
                        }
                    }
                }
            }
        }

        /* ---------------------------- 主页问候语 ---------------------------- */
        SettingsSection(
            title = "主页问候语",
            subtitle = "首页标题如何称呼你",
            icon = MaterialSymbols.AccountCircle
        ) {
            val isCustom = GreetingMode.fromName(greetingModeInput) == GreetingMode.CUSTOM

            ExpressivePillTabs(
                tabs = listOf(
                    PillTab("默认"),
                    PillTab("自定义问候语")
                ),
                selectedIndex = if (isCustom) 1 else 0,
                onSelect = { index ->
                    val mode = if (index == 1) GreetingMode.CUSTOM else GreetingMode.DEFAULT
                    greetingModeInput = mode.name
                    onGreetingModeChange(mode.name)
                },
                scrollable = true
            )

            SettingsRow(
                icon = MaterialSymbols.AccountCircle,
                title = if (isCustom) "自定义问候语" else "称谓",
                subtitle = if (isCustom) "自定义整条问候语，首页标题将原样显示" else "如何称呼您"
            ) {
                if (isCustom) {
                    OutlinedTextField(
                        value = customGreetingInput,
                        onValueChange = {
                            customGreetingInput = it
                            onCustomGreetingChange(it)
                        },
                        singleLine = true,
                        placeholder = { Text("例如：欢迎回来") },
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.width(300.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = displayNameInput,
                        onValueChange = {
                            displayNameInput = it
                            onDisplayNameChange(it)
                        },
                        singleLine = true,
                        placeholder = { Text(System.getProperty("user.name") ?: "用户") },
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.width(220.dp)
                    )
                }
            }
        }

        /* ---------------------------- 包管理器 ---------------------------- */
        SettingsSection(
            title = if (isWindows) "Windows 包管理器" else "Linux 包管理器",
            subtitle = "包页安装命令使用的包管理器",
            icon = MaterialSymbols.Package2
        ) {
            SettingsRow(
                icon = MaterialSymbols.Package2,
                title = "包管理器",
                subtitle = if (detectedPackageManager != PackageManagerType.UNKNOWN) {
                    "检测到: " + PackageManagerUtils.getPackageManagerDisplayName(detectedPackageManager)
                } else {
                    "未检测到包管理器"
                }
            ) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.width(220.dp)
                ) {
                    OutlinedTextField(
                        value = PackageManagerUtils.getPackageManagerDisplayName(selectedPackageManager),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        allPlatformManagers.forEach { manager ->
                            val isInstalled = manager in availableManagers
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = PackageManagerUtils.getPackageManagerDisplayName(manager),
                                            color = if (isInstalled) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            }
                                        )
                                        if (!isInstalled) {
                                            Icon(
                                                imageVector = MaterialSymbols.Warning,
                                                contentDescription = "未安装",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (isInstalled) {
                                        onPackageManagerChange(manager)
                                        expanded = false
                                    }
                                },
                                enabled = isInstalled
                            )
                        }
                    }
                }
            }
        }

        /* ------------------------------- 代理 ------------------------------- */
        SettingsSection(
            title = "代理",
            subtitle = "拉取包列表时使用的 GitHub 代理",
            icon = MaterialSymbols.Flowchart
        ) {
            SettingsRow(
                icon = MaterialSymbols.Flowchart,
                title = "使用 GitHub 代理",
                subtitle = "开启后通过代理拉取包列表"
            ) {
                Switch(checked = useProxy, onCheckedChange = { onUseProxyChange(it) })
            }

            if (useProxy) {
                var localProxyUrl by remember(proxyUrl) { mutableStateOf(proxyUrl) }
                SettingsRow(
                    icon = MaterialSymbols.Http,
                    title = "代理地址",
                    subtitle = "例如: https://gh-proxy.com"
                ) {
                    OutlinedTextField(
                        value = localProxyUrl,
                        onValueChange = {
                            localProxyUrl = it
                            onProxyUrlChange(it)
                        },
                        singleLine = true,
                        placeholder = { Text("https://gh-proxy.com") },
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.width(300.dp)
                    )
                }
            }
        }

        /* ------------------------------- 终端 ------------------------------- */
        SettingsSection(
            title = "终端",
            subtitle = "输出编码与会话行为",
            icon = MaterialSymbols.Terminal2
        ) {
            SettingsRow(
                icon = MaterialSymbols.CodeXml,
                title = "终端输出编码",
                subtitle = "选择终端输出使用的字符编码"
            ) {
                var encodingExpanded by remember { mutableStateOf(false) }
                val currentEncoding = remember(terminalEncoding) { TerminalEncoding.fromName(terminalEncoding) }
                ExposedDropdownMenuBox(
                    expanded = encodingExpanded,
                    onExpandedChange = { encodingExpanded = !encodingExpanded },
                    modifier = Modifier.width(220.dp)
                ) {
                    OutlinedTextField(
                        value = currentEncoding.displayName,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = encodingExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = encodingExpanded,
                        onDismissRequest = { encodingExpanded = false }
                    ) {
                        TerminalEncoding.entries.forEach { encoding ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = encoding.displayName,
                                        color = if (encoding.charsetName == terminalEncoding) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                },
                                onClick = {
                                    onTerminalEncodingChange(encoding.charsetName)
                                    encodingExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            SettingsRow(
                icon = MaterialSymbols.Terminal2,
                title = "包页指令会话模式",
                subtitle = "包页执行本地指令时如何创建终端会话"
            ) {
                var sessionModeExpanded by remember { mutableStateOf(false) }
                val currentMode = remember(toolCommandSessionMode) {
                    ToolCommandSessionMode.fromName(toolCommandSessionMode)
                }
                ExposedDropdownMenuBox(
                    expanded = sessionModeExpanded,
                    onExpandedChange = { sessionModeExpanded = !sessionModeExpanded },
                    modifier = Modifier.width(220.dp)
                ) {
                    OutlinedTextField(
                        value = currentMode.displayName,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sessionModeExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = sessionModeExpanded,
                        onDismissRequest = { sessionModeExpanded = false }
                    ) {
                        ToolCommandSessionMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.displayName,
                                        color = if (mode.name == toolCommandSessionMode) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                },
                                onClick = {
                                    onToolCommandSessionModeChange(mode.name)
                                    sessionModeExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            SettingsRow(
                icon = MaterialSymbols.Terminal2,
                title = "进程结束后立即结束会话",
                subtitle = "关闭后需按回车确认；开启后进程一结束会话即自动关闭"
            ) {
                Switch(checked = closeSessionOnEnd, onCheckedChange = onCloseSessionOnEndChange)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * 设置分区卡片：Expressive 的大圆角容器 + 图标井标题。
 * 分区内部不再使用分割线，而是依靠行间距与 trailing 控件建立节奏。
 */
@Composable
private fun SettingsSection(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TonalIcon(
                    icon = icon,
                    contentDescription = null,
                    size = 44.dp,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

/**
 * 设置项行：左侧图标井 + 标题/说明，右侧为开关、输入框或下拉框。
 * 行本身不画分割线，靠 8dp 的纵向内边距形成分组。
 */
@Composable
private fun SettingsRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            TonalIcon(
                icon = icon,
                contentDescription = null,
                size = 40.dp,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        trailing()
    }
}
