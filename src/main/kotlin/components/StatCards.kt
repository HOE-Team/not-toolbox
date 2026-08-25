// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import java.util.Locale

@Composable
fun CircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Float = 80f,
    strokeWidth: Float = 4f,
    label: String = ""
) {
    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.size(size.dp),
            strokeWidth = strokeWidth.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = "${progress.roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp
        )
    }
}

@Composable
fun StatCard(
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Title row: (optional) icon + title text
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            content()
        }
    }
}

@Composable
fun CPUStatCard(
    model: String,
    usage: Double,
    stepping: String = "Unknown",
    currentFreq: Double = 0.0,
    modifier: Modifier = Modifier
) {
    StatCard(
        title = "CPU",
        modifier = modifier,
        icon = MaterialSymbols.Memory,
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    progress = usage.toFloat(),
                    modifier = Modifier.weight(0.3f)
                )
                Column(
                    modifier = Modifier
                        .weight(0.7f)
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "型号: $model",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                    Text(
                        text = "步进: $stepping",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "频率: ${String.format("%.2f", currentFreq)} GHz",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}

@Composable
fun RAMStatCard(
    frequency: Long,
    used: Double,
    total: Double,
    usage: Double,
    modifier: Modifier = Modifier
) {
    StatCard(
        title = "内存",
        modifier = modifier,
        icon = MaterialSymbols.MemoryAlt,
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    progress = usage.toFloat(),
                    modifier = Modifier.weight(0.3f)
                )
                Column(
                    modifier = Modifier
                        .weight(0.7f)
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (frequency > 0) "频率: ${frequency} MHz" else "频率: 未知",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "占用: %.2f / %.2f GB", used, total),
                        style = MaterialTheme.typography.bodySmall
                    )
                        // Removed textual percentage; percentage remains inside circular indicator
                }
            }
        }
    )
}

@Composable
fun GPUStatCard(
    gpus: List<utils.GPUInfo>,
    modifier: Modifier = Modifier
) {
    StatCard(
        title = "已安装的GPU",
        modifier = modifier,
        icon = MaterialSymbols.DeveloperBoard,
        content = {
            if (gpus.isEmpty()) {
                // No GPU installed
                Text(
                    text = "无GPU被安装或驱动程序未安装",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (gpus.size == 1) {
                // Single GPU - show only model name
                val gpu = gpus[0]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = gpu.model,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            } else {
                // Multiple GPUs - show all model names
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    gpus.forEach { gpu ->
                        Text(
                            text = gpu.model,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun DiskStatCard(
    disks: List<utils.DiskInfo>,
    modifier: Modifier = Modifier
) {
    if (disks.isEmpty()) return

    StatCard(
        title = "磁盘",
        modifier = modifier,
        icon = MaterialSymbols.HardDrive,
        content = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                disks.forEach { disk ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "${disk.name} - ${disk.model}", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (disk.usage / 100.0).toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "%.2f GB / %.2f GB", disk.usedGB, disk.totalGB),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    )
}

/** Format a KB/s rate into a human-readable string (auto KB/s, MB/s, GB/s). */
private fun formatRate(kbPerSec: Double): String {
    return when {
        kbPerSec >= 1024.0 * 1024.0 -> String.format(Locale.getDefault(), "%.2f GB/s", kbPerSec / (1024.0 * 1024.0))
        kbPerSec >= 1024.0 -> String.format(Locale.getDefault(), "%.2f MB/s", kbPerSec / 1024.0)
        else -> String.format(Locale.getDefault(), "%.2f KB/s", kbPerSec)
    }
}

/** Format a total GB value into a human-readable string (auto GB, TB). */
private fun formatTotal(gb: Double): String {
    return if (gb >= 1024.0) {
        String.format(Locale.getDefault(), "%.2f TB", gb / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.2f GB", gb)
    }
}

@Composable
fun NetworkIOCard(
    network: utils.NetworkIOInfo,
    modifier: Modifier = Modifier
) {
    StatCard(
        title = "网络I/O",
        modifier = modifier,
        icon = MaterialSymbols.SwapVert,
        content = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MaterialSymbols.Download,
                        contentDescription = "下载",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "下载: ${formatRate(network.downKBps)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MaterialSymbols.Upload,
                        contentDescription = "上传",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "上传: ${formatRate(network.upKBps)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}

/** Displays the primary network adapter (NIC name, MAC, WiFi SSID, IPv4). */
@Composable
fun NetworkAdapterCard(
    network: utils.NetworkIOInfo,
    modifier: Modifier = Modifier
) {
    // Connection-type icon: WiFi → NetworkWifi, cellular/LTE → SignalCellular3Bar,
    // wired ethernet → SettingsEthernet, anything else (bluetooth / unknown) keeps Router.
    val connectionIcon = when (network.connectionType) {
        utils.NetworkConnectionType.WIFI -> MaterialSymbols.NetworkWifi
        utils.NetworkConnectionType.CELLULAR -> MaterialSymbols.SignalCellular3Bar
        utils.NetworkConnectionType.ETHERNET -> MaterialSymbols.SettingsEthernet
        else -> MaterialSymbols.Router
    }
    StatCard(
        title = "已连接的网络",
        modifier = modifier,
        icon = connectionIcon,
        content = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // WiFi SSID (if connected to a wireless network)
                if (!network.ssid.isNullOrBlank()) {
                    Text(
                        text = "WiFi: ${network.ssid}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // Cellular operator (if connected via LTE/cellular)
                if (network.connectionType == utils.NetworkConnectionType.CELLULAR && !network.operatorName.isNullOrBlank()) {
                    Text(
                        text = "运营商: ${network.operatorName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = if (network.ipv4.isNullOrBlank()) "IP: 未知" else "IP: ${network.ipv4}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (network.mac.isNullOrBlank()) "MAC: 未知" else "MAC: ${network.mac}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    )
}

/** 显示系统实时运行中的进程数量和已登录用户数量。 */
@Composable
fun ServicesStatCard(
    services: utils.ServicesInfo,
    modifier: Modifier = Modifier
) {
    StatCard(
        title = "服务和进程",
        modifier = modifier,
        icon = MaterialSymbols.RoomService,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = MaterialSymbols.RoomService,
                        contentDescription = "进程数量",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "运行中的进程: ${services.processCount}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = MaterialSymbols.Login,
                        contentDescription = "活动的登录会话",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "活动的登录会话: ${services.loggedInUsers}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}

/** 显示系统电池的充电状态、容量与寿命信息（仅在检测到电池时显示）。 */
@Composable
fun BatteryStatCard(
    battery: utils.BatteryInfo,
    modifier: Modifier = Modifier
) {
    StatCard(
        title = "电源",
        modifier = modifier,
        icon = MaterialSymbols.BatteryAndroidFrameFull,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 充电状态
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = MaterialSymbols.BatteryAndroidFrameBolt,
                        contentDescription = "充电状态",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (battery.isCharging) "充电状态: 充电中" else "充电状态: 未充电",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // 容量
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = MaterialSymbols.BatteryAndroidFrameQuestion,
                        contentDescription = "容量",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "容量: ${battery.capacityPercent.toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // 寿命
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = MaterialSymbols.EcgHeart,
                        contentDescription = "电池寿命",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "寿命: ${battery.cycleCount} 次循环（${battery.healthStatus}）",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}

/** 显示屏幕分辨率与显示缩放比例。 */
@Composable
fun ScreenStatCard(
    screen: utils.ScreenInfo,
    modifier: Modifier = Modifier
) {
    StatCard(
        title = "屏幕",
        modifier = modifier,
        icon = MaterialSymbols.DesktopWindows,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 分辨率
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = MaterialSymbols.AspectRatio,
                        contentDescription = "分辨率",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "分辨率: ${screen.resolution}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // 缩放
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = MaterialSymbols.PanZoom,
                        contentDescription = "缩放",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "缩放: ${screen.scalePercent}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}

/** 显示蓝牙适配器名称（无蓝牙适配器时不显示）。 */
@Composable
fun BluetoothStatCard(
    bluetooth: utils.BluetoothInfo,
    modifier: Modifier = Modifier
) {
    StatCard(
        title = "蓝牙适配器",
        modifier = modifier,
        icon = MaterialSymbols.Bluetooth,
        content = {
            Text(
                text = bluetooth.adapterModel,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
        }
    )
}

/** Lists all installed network adapters, collapsible when there are many. */
@Composable
fun NetworkAdaptersCard(
    network: utils.NetworkIOInfo,
    modifier: Modifier = Modifier
) {
    StatCard(
        title = "网络适配器",
        modifier = modifier,
        icon = MaterialSymbols.Lan,
        content = {
            val list = network.adapters
            if (list.isEmpty()) {
                Text(
                    text = "未检测到已安装的网络适配器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Collapse when there are more than this many items.
                val visibleLimit = 4
                var expanded by remember { mutableStateOf(false) }
                val shown = if (expanded) list else list.take(visibleLimit)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    shown.forEach { name ->
                        Text(
                            text = "• $name",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Expand / collapse toggle (only when there are extra items).
                    if (list.size > visibleLimit) {
                        Text(
                            text = if (expanded) "收起" else "展开全部（${list.size}）",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { expanded = !expanded }
                        )
                    }
                }
            }
        }
    )
}
