// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

import oshi.SystemInfo
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

object SystemInfoProvider {
    private val si = SystemInfo()
    private val hardware = si.hardware
    // cached memory frequency (MHz) populated asynchronously to avoid blocking calls
    @Volatile
    private var memFreqMHzCached: Long = 0L

    init {
        detectMemFreqAsync()
    }

    private fun detectMemFreqAsync() {
        thread(start = true, isDaemon = true) {
            try {
                val v = detectMemFreq()
                if (v > 0L) memFreqMHzCached = v
            } catch (_: Exception) {
            }
        }
    }

    private fun detectMemFreq(): Long {
        // Reflection-based detection (same heuristics as before)
        try {
            val memory = hardware.memory
            var physList: List<*>? = null
            for (m in memory.javaClass.methods) {
                if (m.parameterCount == 0 && List::class.java.isAssignableFrom(m.returnType)) {
                    try {
                        val res = m.invoke(memory) as? List<*>
                        if (!res.isNullOrEmpty()) {
                            physList = res
                            break
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            if (!physList.isNullOrEmpty()) {
                val first = physList[0]
                val methods = first?.javaClass?.methods?.filter { it.parameterCount == 0 }?.map { it.name } ?: emptyList()
                val preferred = methods.firstOrNull { it.contains("Clock", true) || it.contains("Speed", true) || it.contains("Freq", true) || it.contains("Configured", true) }
                var freqVal: Long? = null
                if (preferred != null) {
                    freqVal = tryGetLongProp(first, arrayOf(preferred))
                }
                if (freqVal == null) {
                    freqVal = tryGetLongProp(first, arrayOf("getConfiguredClockSpeed", "getClockSpeed", "getSpeed", "getFrequency", "getCurrentSpeed", "getSpeedMhz"))
                }

                if (freqVal != null) {
                    return when {
                        freqVal > 1_000_000L -> freqVal / 1_000_000L
                        freqVal > 10000L -> freqVal / 1000L
                        else -> freqVal
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Platform-specific fallback
        val os = System.getProperty("os.name").lowercase()
        
        if (os.contains("windows")) {
            // Windows fallback
            try {
                val out = executeCommand("powershell -Command \"Get-CimInstance -ClassName Win32_PhysicalMemory | Select-Object -ExpandProperty Speed\"")
                val lines = out.lines().map { it.trim() }.filter { it.matches(Regex("^\\d+$")) }
                if (lines.isNotEmpty()) {
                    val v = lines[0].toLongOrNull()
                    if (v != null) return v
                }
            } catch (_: Exception) {
            }
        } else if (os.contains("linux")) {
            // Linux fallback using dmidecode
            try {
                val out = executeCommand("sudo dmidecode -t memory 2>/dev/null || dmidecode -t memory 2>/dev/null")
                val lines = out.lines()
                for (line in lines) {
                    if (line.contains("Speed:", ignoreCase = true)) {
                        val parts = line.split(":")
                        if (parts.size >= 2) {
                            val speedStr = parts[1].trim().replace("MHz", "").replace("MT/s", "").trim()
                            val v = speedStr.toLongOrNull()
                            if (v != null) return v
                        }
                    }
                }
            } catch (_: Exception) {
            }
            
            // Alternative: check /proc/cpuinfo for memory speed hints
            try {
                val out = executeCommand("cat /proc/cpuinfo 2>/dev/null | grep -i mhz | head -1")
                if (out.isNotBlank()) {
                    val parts = out.split(":")
                    if (parts.size >= 2) {
                        val mhzStr = parts[1].trim()
                        val v = mhzStr.toLongOrNull()
                        if (v != null) return v
                    }
                }
            } catch (_: Exception) {
            }
        }

        return 0L
    }

    private fun tryGetLongProp(instance: Any?, methodNames: Array<String>): Long? {
        if (instance == null) return null
        for (name in methodNames) {
            try {
                val m = instance.javaClass.getMethod(name)
                val v = m.invoke(instance)
                if (v is Number) return v.toLong()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun executeCommand(command: String): String {
        return try {
            val os = System.getProperty("os.name").lowercase()
            val process = if (os.contains("windows")) {
                Runtime.getRuntime().exec(arrayOf("cmd.exe", "/c", command))
            } else {
                // For Linux/macOS, use bash/sh
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            }
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            ""
        }
    }

    // Cache previous CPU ticks for non-blocking load calculation
    private var prevCpuTicks: LongArray? = null

    fun getSystemInfo(): SystemInfoSnapshot {
        // CPU
        val processor = hardware.processor
        val cpuModel = processor.processorIdentifier.name ?: "Unknown"
        val cpuStepping = processor.processorIdentifier.stepping
        val currentFreqHz = processor.currentFreq.firstOrNull() ?: 0L
        val currentFreqGHz = if (currentFreqHz > 0) currentFreqHz / 1_000_000_000.0 else 0.0

        val cpuUsage = try {
            val ticks = processor.systemCpuLoadTicks
            val usage = if (prevCpuTicks != null) {
                val u = processor.getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100.0
                prevCpuTicks = ticks
                u
            } else {
                prevCpuTicks = ticks
                0.0
            }
            usage
        } catch (e: Exception) {
            0.0
        }

        val cpuInfo = CPUInfo(
            model = cpuModel.trim(),
            usage = minOf(cpuUsage, 100.0),
            stepping = cpuStepping,
            currentFreq = currentFreqGHz
        )

        // RAM
        val memory = hardware.memory
        val memoryUsedGB = ((memory.total - memory.available) / (1024.0 * 1024.0 * 1024.0))
        val memoryTotalGB = (memory.total / (1024.0 * 1024.0 * 1024.0))
        val memoryUsagePercent = (memory.total - memory.available).toDouble() / memory.total * 100

        val ramInfo = RAMInfo(
            frequency = memFreqMHzCached,
            used = memoryUsedGB,
            total = memoryTotalGB,
            usage = memoryUsagePercent
        )

        // GPU
        val gpus = hardware.graphicsCards.map { card ->
            GPUInfo(model = card.name ?: "Unknown GPU", driverVersion = "N/A", usage = 0.0, memoryUsed = 0L, memoryTotal = 0L)
        }

        // Disks: map fileStores to diskStores via partition mount points (avoid external commands)
        val fileStores = si.operatingSystem.fileSystem.fileStores
        val diskStores = hardware.diskStores

        val disks = fileStores.filter { fs ->
            try {
                (fs.totalSpace > 0L) && !(fs.description?.contains("removable", true) ?: false)
            } catch (_: Exception) {
                false
            }
        }.map { fs ->
            val total = try { fs.totalSpace } catch (_: Exception) { 0L }
            val usable = try { fs.usableSpace } catch (_: Exception) { 0L }
            val used = (total - usable).coerceAtLeast(0L)
            val totalGB = total / (1024.0 * 1024.0 * 1024.0)
            val usedGB = used / (1024.0 * 1024.0 * 1024.0)
            val usagePct = if (total > 0L) used.toDouble() / total.toDouble() * 100.0 else 0.0

            val mount = fs.mount ?: ""
            val driveLetter = if (mount.length >= 2 && mount[1] == ':') {
                // Windows drive letter
                if (mount.length == 2 || (mount.length == 3 && mount[2] == '\\')) mount.take(2) else "未指定盘符"
            } else {
                // Linux/macOS: use mount point or device name
                if (mount.isNotBlank()) {
                    if (mount == "/") "Root" else mount.split("/").lastOrNull() ?: mount
                } else {
                    "未指定盘符"
                }
            }

            val diskModel = try {
                diskStores.firstOrNull { disk ->
                    disk.partitions.any { part ->
                        val mp = try { part.mountPoint } catch (_: Exception) { null }
                        mp != null && mp == mount
                    }
                }?.model ?: "未知型号"
            } catch (_: Exception) {
                "未知型号"
            }

            DiskInfo(
                name = driveLetter,
                mount = mount,
                model = diskModel,
                usedGB = usedGB,
                totalGB = totalGB,
                usage = usagePct
            )
        }

        return SystemInfoSnapshot(
            cpu = cpuInfo,
            ram = ramInfo,
            gpus = gpus.ifEmpty { listOf(GPUInfo(model = "Unknown GPU", driverVersion = "N/A", usage = 0.0, memoryUsed = 0L, memoryTotal = 0L)) },
            disks = disks
        )
    }

    fun getSystemOverview(): SystemOverview {
        val os = si.operatingSystem
        val architecture = System.getProperty("os.arch") ?: "Unknown"
        val osVersionStr = "${os.family} ${os.versionInfo?.version ?: ""}".trim()

        val platformStr = try {
            val cs = hardware.computerSystem
            val model = cs.model ?: ""
            if (model.isNotBlank()) model else System.getProperty("os.name") ?: "Unknown"
        } catch (_: Exception) {
            System.getProperty("os.name") ?: "Unknown"
        }

        val computerName = try { java.net.InetAddress.getLocalHost().hostName } catch (_: Exception) { "Unknown" }

        // Wallpaper path detection for different platforms
        var wallpaperPath: String? = null
        val currentOs = System.getProperty("os.name").lowercase()
        
        if (currentOs.contains("windows")) {
            // Windows: check registry
            try {
                val reg = executeCommand("reg query \"HKCU\\Control Panel\\Desktop\" /v WallPaper")
                val line = reg.split("\n").firstOrNull { it.contains("WallPaper", ignoreCase = true) }
                if (line != null) {
                    val parts = line.trim().split(Regex("\\s{2,}"))
                    wallpaperPath = if (parts.size >= 3) parts[2] else line.trim().split(" ").lastOrNull()
                }
            } catch (_: Exception) {
                wallpaperPath = null
            }
        } else if (currentOs.contains("linux")) {
            // Linux: check common desktop environments
            try {
                // Try GNOME
                var out = executeCommand("gsettings get org.gnome.desktop.background picture-uri 2>/dev/null")
                if (out.isNotBlank() && !out.contains("No such schema") && out.contains("file://")) {
                    wallpaperPath = out.replace("file://", "").replace("'", "").trim()
                } else {
                    // Try KDE
                    out = executeCommand("kreadconfig5 --file kdeglobals --group Wallpapers --key wallpaper 2>/dev/null")
                    if (out.isNotBlank()) {
                        wallpaperPath = out.trim()
                    } else {
                        // Try XFCE
                        out = executeCommand("xfconf-query -c xfce4-desktop -p /backdrop/screen0/monitor0/workspace0/last-image 2>/dev/null")
                        if (out.isNotBlank()) {
                            wallpaperPath = out.trim()
                        }
                    }
                }
            } catch (_: Exception) {
                wallpaperPath = null
            }
        }

        return SystemOverview(
            osVersion = osVersionStr,
            architecture = architecture,
            windowsUpdateStatus = "",
            platform = platformStr,
            computerName = computerName,
            wallpaperPath = wallpaperPath
        )
    }
}
