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

    // Network IO tracking (raw bytes + timestamp for rate calculation)
    @Volatile
    private var prevNetRxBytes = 0L
    @Volatile
    private var prevNetTxBytes = 0L
    @Volatile
    private var prevNetSampleNanos = 0L

    // WiFi SSID + IPv4 detection (cached, refreshed periodically to avoid blocking subprocesses every second)
    @Volatile
    private var cachedSSID: String? = null
    @Volatile
    private var cachedIPv4: String? = null
    @Volatile
    private var cachedNICName: String? = null
    @Volatile
    private var cachedMAC: String? = null
    @Volatile
    private var cachedAdapters: List<String> = emptyList()
    private var wifiLastRefreshNanos = 0L
    private val WIFI_REFRESH_INTERVAL_NANOS = 10_000_000_000L  // every 10s

    private fun refreshWifiSsid() {
        val os = System.getProperty("os.name").lowercase()
        try {
            val ssid = if (os.contains("windows")) {
                val out = executeCommand("netsh wlan show interfaces")
                out.lines()
                    .firstOrNull { it.contains("SSID", ignoreCase = true) && !it.contains("BSSID", ignoreCase = true) }
                    ?.split(":")
                    ?.getOrNull(1)
                    ?.trim()
                    ?.ifBlank { null }
            } else if (os.contains("linux")) {
                val out = executeCommand("iwgetid -r 2>/dev/null")
                out.lines().firstOrNull { it.isNotBlank() }?.trim()?.ifBlank { null }
            } else if (os.contains("mac")) {
                val out = executeCommand("networksetup -getairportnetwork en0 2>/dev/null")
                out.lines().firstOrNull { it.contains("SSID", ignoreCase = true) }
                    ?.substringAfter(":")
                    ?.trim()
                    ?.ifBlank { null }
            } else null
            cachedSSID = ssid
        } catch (_: Exception) {
            cachedSSID = null
        }
    }

    // Keywords that indicate a virtual / software (non-physical) network adapter.
    private val virtualAdapterKeywords = listOf(
        "virtual", "vmware", "hyper-v", "hyperv", "vbox", "virtualbox",
        "loopback", "tap-", "tun", "wan miniport", "bluetooth",
        "wi-fi direct", "wifi direct", "microsoft", "pseud", "ppp", "l2tp",
        "vpn", "ndis", "tunnel", "docker", "windows"
    )

    private fun refreshNetworkIdentity() {
        try {
            var ip: String? = null
            var nicName: String? = null
            var mac: String? = null
            // Physical adapter names from OSHI's display names (e.g. "Intel Dual-Band Wireless AC-8625"),
            // filtering out virtual / software adapters.
            val adapters = mutableListOf<String>()
            try {
                for (net in hardware.networkIFs) {
                    val display = net.displayName ?: net.name ?: ""
                    if (display.isBlank()) continue
                    val keyword = display.lowercase()
                    val isVirtual = virtualAdapterKeywords.any { keyword.contains(it) }
                    val loopback = display.contains("Loopback", true)
                    if (isVirtual || loopback) continue
                    if (!adapters.contains(display)) adapters.add(display)
                }
            } catch (_: Exception) {
            }
            // Use the standard JDK API (robust across OSHI versions) for the active NIC identity.
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (nif in interfaces) {
                    if (!nif.isUp || nif.isLoopback) continue
                    val addrs = nif.inetAddresses
                    var hasIpv4 = false
                    for (addr in addrs) {
                        if (addr is java.net.Inet4Address && !addr.hostAddress.startsWith("169.254.")) {
                            ip = addr.hostAddress
                            hasIpv4 = true
                            break
                        }
                    }
                    if (hasIpv4) {
                        nicName = nif.name
                        val hw = try { nif.hardwareAddress } catch (_: Exception) { null }
                        if (hw != null && hw.isNotEmpty()) {
                            mac = hw.joinToString(":") { String.format("%02X", it) }
                        }
                        break
                    }
                }
            }
            cachedIPv4 = ip
            cachedNICName = nicName
            cachedMAC = mac
            cachedAdapters = adapters
        } catch (_: Exception) {
            cachedIPv4 = null
            cachedNICName = null
            cachedMAC = null
            cachedAdapters = emptyList()
        }
    }

    fun getNetworkIO(): NetworkIOInfo {
        val now = System.nanoTime()

        // Periodically refresh WiFi SSID / IPv4 without blocking every second
        if (now - wifiLastRefreshNanos > WIFI_REFRESH_INTERVAL_NANOS) {
            refreshWifiSsid()
            refreshNetworkIdentity()
            wifiLastRefreshNanos = now
        }

        // Accumulate raw counters across all interfaces
        var rx = 0L
        var tx = 0L
        try {
            for (net in hardware.networkIFs) {
                rx += net.bytesRecv
                tx += net.bytesSent
            }
        } catch (_: Exception) {
        }

        var downKBps = 0.0
        var upKBps = 0.0
        val elapsedNanos = now - prevNetSampleNanos
        if (prevNetSampleNanos != 0L && elapsedNanos > 0L && rx >= prevNetRxBytes && tx >= prevNetTxBytes) {
            val elapsedSec = elapsedNanos / 1_000_000_000.0
            downKBps = ((rx - prevNetRxBytes) / 1024.0) / elapsedSec
            upKBps = ((tx - prevNetTxBytes) / 1024.0) / elapsedSec
        }

        prevNetSampleNanos = now
        prevNetRxBytes = rx
        prevNetTxBytes = tx

        return NetworkIOInfo(
            downKBps = downKBps,
            upKBps = upKBps,
            downTotalGB = rx / (1024.0 * 1024.0 * 1024.0),
            upTotalGB = tx / (1024.0 * 1024.0 * 1024.0),
            ssid = cachedSSID,
            ipv4 = cachedIPv4,
            nicName = cachedNICName,
            mac = cachedMAC,
            adapters = cachedAdapters
        )
    }

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

    // ---- Services (processes & logged-in users) ----
    // OSHI's processCount and sessions are memory-level queries, but to keep
    // this absolutely non-blocking we still cache them and refresh at most
    // every 5 seconds (well within the 1s UI tick without blocking calls).
    @Volatile
    private var cachedProcessCount: Int = -1
    @Volatile
    private var cachedLoggedInUsers: Int = -1
    private var servicesLastRefreshNanos = 0L
    private val SERVICES_REFRESH_INTERVAL_NANOS = 5_000_000_000L  // every 5s

    fun getServices(): ServicesInfo {
        val now = System.nanoTime()
        if (now - servicesLastRefreshNanos > SERVICES_REFRESH_INTERVAL_NANOS) {
            try {
                val procCount = si.operatingSystem.processCount
                if (procCount >= 0) cachedProcessCount = procCount
            } catch (_: Exception) {
            }
            try {
                val users = si.operatingSystem.sessions.count()
                if (users >= 0) cachedLoggedInUsers = users
            } catch (_: Exception) {
            }
            servicesLastRefreshNanos = now
        }
        return ServicesInfo(
            processCount = cachedProcessCount.coerceAtLeast(0),
            loggedInUsers = cachedLoggedInUsers.coerceAtLeast(0)
        )
    }

    // ---- Battery ----
    // OSHI's powerSources is a memory-level query (no subprocess), but we still
    // cache it and refresh at most every 5s to guarantee zero UI blocking.
    @Volatile
    private var cachedHasBattery: Boolean = false
    @Volatile
    private var cachedIsCharging: Boolean = false
    @Volatile
    private var cachedCapacityPercent: Double = 0.0
    @Volatile
    private var cachedCycleCount: Int = -1
    @Volatile
    private var cachedHealthStatus: String = "未知"
    private var batteryLastRefreshNanos = 0L
    private val BATTERY_REFRESH_INTERVAL_NANOS = 5_000_000_000L  // every 5s

    fun getBattery(): BatteryInfo {
        val now = System.nanoTime()
        if (now - batteryLastRefreshNanos > BATTERY_REFRESH_INTERVAL_NANOS) {
            try {
                val powerSources = si.hardware.powerSources
                if (powerSources.isNotEmpty()) {
                    val ps = powerSources[0]
                    cachedHasBattery = true
                    // Charging state: on AC (powerOnLine) and/or actively charging.
                    // isCharging can be unreliable on some platforms, so combine
                    // it with powerOnLine for a more robust detection.
                    cachedIsCharging = ps.isPowerOnLine || ps.isCharging
                    // OSHI's remainingCapacityPercent is a fraction in [0.0, 1.0]
                    // representing 0-100%. Convert to a 0-100 percentage for display.
                    val frac = ps.remainingCapacityPercent
                    cachedCapacityPercent = if (frac in 0.0..1.0) frac * 100.0 else frac
                    cachedCapacityPercent = cachedCapacityPercent.coerceIn(0.0, 100.0)
                    cachedCycleCount = ps.cycleCount.takeIf { it >= 0 } ?: 0
                    // PowerSource has no health field; estimate health from cycle
                    // count (more cycles → more degraded battery).
                    cachedHealthStatus = when {
                        cachedCycleCount <= 0 -> "未知"
                        cachedCycleCount < 300 -> "良好"
                        cachedCycleCount < 600 -> "一般"
                        else -> "较差"
                    }
                } else {
                    cachedHasBattery = false
                }
            } catch (_: Exception) {
            }
            batteryLastRefreshNanos = now
        }
        return BatteryInfo(
            hasBattery = cachedHasBattery,
            isCharging = cachedIsCharging,
            capacityPercent = cachedCapacityPercent,
            cycleCount = cachedCycleCount,
            healthStatus = cachedHealthStatus
        )
    }

    // ---- Screen ----
    // Use java.awt memory-level APIs (no subprocess, no blocking). Resolution and
    // scale are read from the toolkit; a 5s cache avoids re-querying every tick.
    @Volatile
    private var cachedResolution: String = "未知"
    @Volatile
    private var cachedScalePercent: Int = 100
    private var screenLastRefreshNanos = 0L
    private val SCREEN_REFRESH_INTERVAL_NANOS = 5_000_000_000L  // every 5s

    fun getScreen(): ScreenInfo {
        val now = System.nanoTime()
        if (now - screenLastRefreshNanos > SCREEN_REFRESH_INTERVAL_NANOS) {
            try {
                val gd = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .defaultScreenDevice
                // getDisplayMode() returns the physical/native display mode
                // (not affected by OS scaling). bounds would give the logical,
                // scaled-down resolution — the user wants the hardware one.
                val mode = gd.displayMode
                cachedResolution = "${mode.width}x${mode.height}"
            } catch (_: Exception) {
                cachedResolution = "未知"
            }
            try {
                val scale = java.awt.Toolkit.getDefaultToolkit().screenResolution / 96.0 * 100.0
                cachedScalePercent = scale.toInt().coerceAtLeast(100)
            } catch (_: Exception) {
                cachedScalePercent = 100
            }
            screenLastRefreshNanos = now
        }
        return ScreenInfo(
            resolution = cachedResolution,
            scalePercent = cachedScalePercent
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
