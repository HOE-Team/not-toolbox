// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import components.MaterialSymbols
import androidx.compose.material3.*
import theme.isValidHex
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import utils.PackageManagerUtils
import utils.PackageManagerType
import utils.CardBgManager
import config.TerminalEncoding
import java.awt.FileDialog

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
    useCustomBg: Boolean = false,
    onUseCustomBgChange: (Boolean) -> Unit = {},
    customBgFile: String? = null,
    onCustomBgFileChange: (String?) -> Unit = {}
) {
    var localDarkTheme by remember { mutableStateOf(isDarkTheme) }
    var hexInput by remember { mutableStateOf(selectedColor) }
    var saveStateMessage by remember { mutableStateOf<String?>(null) }
    var displayNameInput by remember(displayName) { mutableStateOf(displayName) }

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
            .padding(24.dp)
    ) {
        // 程序外观标题（缩小）
        Text(
            text = "程序外观",
            style = MaterialTheme.typography.titleMedium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 设置项1：深色主题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = MaterialSymbols.DarkMode,
                    contentDescription = "深色主题",
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "深色主题",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Switch(
                checked = localDarkTheme,
                onCheckedChange = { newValue ->
                    localDarkTheme = newValue
                    onThemeChange(newValue)
                },
                // M3 switch colors: on = primary track / onPrimary thumb,
                // off = surfaceVariant track / onSurfaceVariant thumb.
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // 设置项：称谓
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = MaterialSymbols.AccountCircle,
                    contentDescription = "称谓",
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "称谓",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "如何称呼您",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextField(
                value = displayNameInput,
                onValueChange = { displayNameInput = it; onDisplayNameChange(it) },
                singleLine = true,
                placeholder = { Text(System.getProperty("user.name") ?: "用户") },
                modifier = Modifier.width(200.dp)
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // 设置项2：HEX 颜色输入与保存
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = MaterialSymbols.Palette,
                    contentDescription = "颜色主题",
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "颜色主题 (HEX)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "输入 6 位 HEX，例如：#6750A4 或 6750A4",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val isValid = remember(hexInput) { isValidHex(hexInput.trim()) }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = hexInput,
                        onValueChange = { hexInput = it; saveStateMessage = null },
                        singleLine = true,
                        isError = hexInput.isNotBlank() && !isValid,
                        placeholder = { Text("#RRGGBB") },
                        modifier = Modifier.width(180.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = {
                        val cleaned = hexInput.trim()
                        if (cleaned.isNotBlank() && isValid) {
                            onColorChange(if (cleaned.startsWith("#")) cleaned else "#${cleaned}")
                            saveStateMessage = "已保存"
                        } else if (cleaned.isBlank()) {
                            onColorChange("")
                            saveStateMessage = "已重置为默认"
                        } else {
                            saveStateMessage = "无效的 HEX"
                        }
                    }) {
                        Text("保存")
                    }
                }
                
                // 在输入框下方显示保存状态消息
                saveStateMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // 设置项：不获取桌面壁纸
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = MaterialSymbols.Wallpaper,
                    contentDescription = "不获取桌面壁纸",
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "不获取桌面壁纸",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (isWindows) "开启后概览卡片使用内置默认壁纸" else "不可关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = useCustomBg,
                onCheckedChange = { onUseCustomBgChange(it) },
                enabled = isWindows,  // Linux 强制开启，不可关闭
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        // 设置项：使用本地图像（仅当开启"不获取桌面壁纸"时显示/可用）
        if (useCustomBg || !isWindows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Imagesmode,
                        contentDescription = "使用本地图像",
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "使用本地图像",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (customBgFile != null) "已选择本地图像" else "选择一张图片作为概览卡片背景",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
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
                    }) {
                        Icon(MaterialSymbols.OpenInNew, "选择", Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (customBgFile != null) "更换" else "选择")
                    }
                    if (customBgFile != null) {
                        OutlinedButton(onClick = { onCustomBgFileChange(null) }) {
                            Icon(MaterialSymbols.Close, "撤下", Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("撤下")
                        }
                    }
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // 包管理器检测和选择（根据平台显示不同标题）
        Text(
            text = if (isWindows) "Windows包管理器" else "Linux包管理器",
            style = MaterialTheme.typography.titleMedium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = MaterialSymbols.Package2,
                    contentDescription = "包管理器",
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "包管理器",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (detectedPackageManager != PackageManagerType.UNKNOWN) 
                            "检测到: ${PackageManagerUtils.getPackageManagerDisplayName(detectedPackageManager)}" 
                            else "未检测到包管理器",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 包管理器选择下拉菜单
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.width(200.dp)
            ) {
                TextField(
                    value = PackageManagerUtils.getPackageManagerDisplayName(selectedPackageManager),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier.menuAnchor()
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
                                        color = if (isInstalled) 
                                            MaterialTheme.colorScheme.onSurface 
                                        else 
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // 代理设置
        Text(
            text = "代理",
            style = MaterialTheme.typography.titleMedium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = MaterialSymbols.Flowchart,
                    contentDescription = "GitHub代理",
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "使用GitHub代理",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "开启后通过代理拉取包列表",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = useProxy,
                onCheckedChange = { onUseProxyChange(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        // 代理地址输入
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = MaterialSymbols.Http,
                    contentDescription = "GitHub代理地址",
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "代理地址",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "例如: https://gh-proxy.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            var localProxyUrl by remember(proxyUrl) { mutableStateOf(proxyUrl) }
            TextField(
                value = localProxyUrl,
                onValueChange = { localProxyUrl = it; onProxyUrlChange(it) },
                singleLine = true,
                placeholder = { Text("https://gh-proxy.com") },
                modifier = Modifier.width(250.dp),
                enabled = useProxy
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // 终端编码设置
        Text(
            text = "终端编码",
            style = MaterialTheme.typography.titleMedium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = MaterialSymbols.CodeXml,
                    contentDescription = "终端编码",
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "终端输出编码",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "选择终端输出使用的字符编码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 编码选择下拉菜单
            var encodingExpanded by remember { mutableStateOf(false) }
            val currentEncoding = remember(terminalEncoding) {
                TerminalEncoding.fromName(terminalEncoding)
            }
            ExposedDropdownMenuBox(
                expanded = encodingExpanded,
                onExpandedChange = { encodingExpanded = !encodingExpanded },
                modifier = Modifier.width(200.dp)
            ) {
                TextField(
                    value = currentEncoding.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = encodingExpanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier.menuAnchor()
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
                                    color = if (encoding.charsetName == terminalEncoding)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
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

        Spacer(modifier = Modifier.height(12.dp))
    }
}
