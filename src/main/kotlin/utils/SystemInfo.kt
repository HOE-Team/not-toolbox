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

data class SystemInfoSnapshot(
    val cpu: CPUInfo,
    val ram: RAMInfo,
    val gpus: List<GPUInfo>,
    val disks: List<DiskInfo>
)
