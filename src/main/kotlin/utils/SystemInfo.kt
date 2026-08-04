// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

data class CPUInfo(
    val model: String,
    val usage: Double,  // percentage 0-100
    val stepping: String = "Unknown",  // CPU stepping
    val currentFreq: Double = 0.0  // GHz
)

data class RAMInfo(
    val frequency: Long,  // MHz
    val used: Double,  // GB
    val total: Double,  // GB
    val usage: Double  // percentage 0-100
)

data class GPUInfo(
    val model: String,
    val driverVersion: String,
    val usage: Double,  // percentage 0-100
    val memoryUsed: Long,  // GB
    val memoryTotal: Long  // GB
) {
    val memoryUsagePercent: Double
        get() = if (memoryTotal > 0) (memoryUsed.toDouble() / memoryTotal) * 100 else 0.0
}

data class DiskInfo(
    val name: String,
    val mount: String,
    val model: String,
    val usedGB: Double,
    val totalGB: Double,
    val usage: Double // percentage 0-100
)

data class NetworkIOInfo(
    val downKBps: Double,  // download speed KB/s
    val upKBps: Double,  // upload speed KB/s
    val downTotalGB: Double,  // total downloaded GB
    val upTotalGB: Double,  // total uploaded GB
    val ssid: String? = null,  // WiFi SSID if connected to a wireless network
    val ipv4: String? = null,  // primary IPv4 address
    val nicName: String? = null,  // primary network interface name
    val mac: String? = null,  // primary network interface MAC address
    val adapters: List<String> = emptyList()  // all installed (physical) network adapter names
)

data class SystemInfoSnapshot(
    val cpu: CPUInfo,
    val ram: RAMInfo,
    val gpus: List<GPUInfo>,
    val disks: List<DiskInfo>
)

data class ServicesInfo(
    val processCount: Int,      // 当前运行的进程数量
    val loggedInUsers: Int      // 已登录用户数量
)

data class BatteryInfo(
    val hasBattery: Boolean,        // 是否有电池
    val isCharging: Boolean,        // 是否充电中
    val capacityPercent: Double,    // 容量百分比 0-100
    val cycleCount: Int,            // 循环次数（寿命指标）
    val healthStatus: String        // 健康状况描述
)

data class ScreenInfo(
    val resolution: String,         // 屏幕分辨率，如 "1920x1080"
    val scalePercent: Int           // 显示缩放比例，如 100、125、150
)

data class BluetoothInfo(
    val hasAdapter: Boolean,        // 是否有蓝牙适配器
    val adapterModel: String,       // 蓝牙适配器型号
    val isDetecting: Boolean = false // 是否正在后台检测中
)
