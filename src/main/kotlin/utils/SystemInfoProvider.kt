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
import java.nio.charset.Charset
import kotlin.concurrent.thread

object SystemInfoProvider {
    private val si = SystemInfo()
    private val hardware = si.hardware
    // cached memory frequency (MHz) populated asynchronously to avoid blocking calls
    @Volatile
    private var memFreqMHzCached: Long = 0L

    init {
        detectMemFreqAsync()
        // Pre-start bluetooth detection immediately so the card is visible on
        // the very first render (instead of appearing after a few seconds).
        refreshBluetoothAsync()
        // Pre-start present-GPU detection so the "已安装的GPU" card is correct
        // from the first render (without it, the first frame may briefly show
        // ghost GPUs before the background refresh completes).
        refreshPresentGpusAsync()
        // Pre-start cellular/LTE detection so the network icon and operator are
        // correct from the first render.
        refreshCellularAsync()
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

    // Version of executeCommand that forces UTF-8 decoding. Used for PowerShell
    // commands whose output may contain non-ASCII text (e.g. bluetooth device
    // names with Chinese characters). Without explicit UTF-8 decoding the raw
    // bytes get misinterpreted and render as garbled text (乱码).
    private fun executeCommandUtf8(command: String): String {
        return try {
            val os = System.getProperty("os.name").lowercase()
            // Wrap so PowerShell writes UTF-8 to stdout regardless of code page.
            val utf8Command = if (os.contains("windows")) {
                "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; $command"
            } else {
                command
            }
            val process = if (os.contains("windows")) {
                Runtime.getRuntime().exec(arrayOf("powershell.exe", "-NoProfile", "-Command", utf8Command))
            } else {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            }
            val reader = BufferedReader(
                InputStreamReader(process.inputStream, Charset.forName("UTF-8"))
            )
            val output = reader.readText()
            reader.close()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            ""
        }
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

    // Set of GPU display names that are currently present (Windows only).
    // OSHI's hardware.graphicsCards reads the registry class key for display
    // adapters, which retains a "ghost" entry for every GPU ever installed on
    // the machine — even after the card is physically removed. We therefore
    // cross-reference against the PnP "Display" class and keep only the cards
    // that are currently present (Status OK/Started; non-present ghosts report
    // "Unknown"). Refreshed on a background thread because GPU hardware is
    // essentially static and calling PowerShell from the UI thread would block.
    @Volatile
    private var cachedPresentGpus: Set<String> = emptySet()
    private var gpuPresentRefreshNanos = 0L
    private val GPU_PRESENT_REFRESH_INTERVAL_NANOS = 30_000_000_000L  // every 30s

    // Cellular / LTE detection state (Windows only). Cached and refreshed on a
    // background thread because getNetworkIO() runs on the UI thread and calling
    // `netsh mbn` synchronously every second would block it. Determined via
    // `netsh mbn show interfaces`: if a Mobile Broadband interface reports
    // "State : Connected", the machine is currently using a cellular network, and
    // that interface's "Provider Name" is the network operator.
    @Volatile
    private var cachedCellularConnected = false
    @Volatile
    private var cachedOperatorName: String? = null
    private var cellularRefreshNanos = 0L
    private val CELLULAR_REFRESH_INTERVAL_NANOS = 30_000_000_000L  // every 30s

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

    // Start cellular/LTE detection on a background thread (non-blocking).
    private fun refreshCellularAsync() {
        thread(start = true, isDaemon = true) { refreshCellular() }
    }

    // Detect whether the machine is currently using a cellular (LTE/WWAN)
    // network and, if so, the network operator. On Windows, `netsh mbn show
    // interfaces` lists every Mobile Broadband interface with its connect State
    // and Provider Name; a "Connected" interface means cellular is in use. On
    // non-Windows systems there is no mobile broadband concept, so we skip.
    // Any parse/query failure leaves the cached flags untouched (safe defaults).
    private fun refreshCellular() {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("windows")) return
        try {
            val out = executeCommandUtf8("netsh mbn show interfaces")
            var blockConnected = false
            var foundConnected = false
            var connectedProvider: String? = null
            for (rawLine in out.lineSequence()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                val key = line.substring(0, idx).trim().lowercase()
                val value = line.substring(idx + 1).trim()
                when {
                    key == "name" || key == "interface name" -> {
                        // Start of a new interface block; reset block-local state.
                        blockConnected = false
                    }
                    key == "state" -> {
                        blockConnected = value.equals("connected", ignoreCase = true)
                        if (blockConnected) foundConnected = true
                    }
                    key == "provider name" -> {
                        if (blockConnected && value.isNotEmpty()) connectedProvider = value
                    }
                }
            }
            cachedCellularConnected = foundConnected
            cachedOperatorName = connectedProvider
        } catch (_: Exception) {
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

        // Periodically refresh cellular/LTE detection on a background thread
        // (never blocking the UI thread).
        if (now - cellularRefreshNanos > CELLULAR_REFRESH_INTERVAL_NANOS) {
            cellularRefreshNanos = now
            refreshCellularAsync()
        }

        // Resolve the active connection type. WiFi (SSID) takes precedence, then
        // cellular, then a plain wired IPv4 connection, else none/other.
        val connectionType = when {
            !cachedSSID.isNullOrBlank() -> NetworkConnectionType.WIFI
            cachedCellularConnected -> NetworkConnectionType.CELLULAR
            !cachedIPv4.isNullOrBlank() && !cachedNICName.isNullOrBlank() -> NetworkConnectionType.ETHERNET
            else -> NetworkConnectionType.OTHER
        }
        val operatorName = cachedOperatorName.takeIf { connectionType == NetworkConnectionType.CELLULAR }

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
            adapters = cachedAdapters,
            connectionType = connectionType,
            operatorName = operatorName
        )
    }

    // Start present-GPU detection on a background thread (non-blocking).
    private fun refreshPresentGpusAsync() {
        thread(start = true, isDaemon = true) { refreshPresentGpus() }
    }

    // Populate cachedPresentGpus with the display names of GPUs that are
    // currently present on the system. On Windows, ghost devices (GPUs that
    // were installed in the past but are no longer present) report a PnP Status
    // of "Unknown", while present devices report "OK"/"Started". Only overwrite
    // the cache with a non-empty result so a transient query failure never
    // hides real GPUs. On non-Windows systems OSHI's list is already accurate.
    private fun refreshPresentGpus() {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("windows")) return
        try {
            val out = executeCommandUtf8(
                "Get-PnpDevice -Class Display | " +
                    "Where-Object { \$_.Status -eq 'OK' -or \$_.Status -eq 'Started' } | " +
                    "ForEach-Object { \$_.FriendlyName }"
            )
            val names = out.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            if (names.isNotEmpty()) cachedPresentGpus = names
        } catch (_: Exception) {
        }
    }

    // Normalize a GPU name for tolerant comparison (trim, lowercase, collapse
    // runs of whitespace) because OSHI and PnP may differ slightly in spelling.
    private fun normalizeGpuName(name: String): String =
        name.trim().lowercase().replace(Regex("\\s+"), " ")

    // Lenient equality: identical after normalization, or one is contained in
    // the other. Falls back to containment so minor vendor-string differences
    // don't cause a genuinely-present GPU to be filtered out.
    private fun gpuNamesMatch(a: String, b: String): Boolean {
        val na = normalizeGpuName(a)
        val nb = normalizeGpuName(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        return na == nb || na.contains(nb) || nb.contains(na)
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
            val prev = prevCpuTicks // 先存入局部变量，避免对可变属性的智能转换（Kotlin 2.4）
            val usage = if (prev != null) {
                val u = processor.getSystemCpuLoadBetweenTicks(prev) * 100.0
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
        // OSHI's hardware.graphicsCards on Windows reads the registry class key
        // for display adapters, which retains a "ghost" entry for every GPU ever
        // installed — even after the card is removed. This made the "已安装的GPU"
        // card list several GPUs when only one is present. We refresh the set of
        // currently-present PnP display devices on a background thread and keep
        // only cards that match. If the present set is empty (non-Windows or the
        // query failed), we fall back to OSHI's full list rather than hiding GPUs.
        val nowNanos = System.nanoTime()
        if (nowNanos - gpuPresentRefreshNanos > GPU_PRESENT_REFRESH_INTERVAL_NANOS) {
            gpuPresentRefreshNanos = nowNanos
            refreshPresentGpusAsync()
        }
        val presentGpuNames = cachedPresentGpus
        val gpus = hardware.graphicsCards.map { card ->
            GPUInfo(model = card.name ?: "Unknown GPU", driverVersion = "N/A", usage = 0.0, memoryUsed = 0L, memoryTotal = 0L)
        }.filter { gpu ->
            presentGpuNames.isEmpty() || presentGpuNames.any { present -> gpuNamesMatch(gpu.model, present) }
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
                // 无电池的台式机上 OSHI 可能返回一个占位 PowerSource
                // （Windows 上总是返回一个 name="System Battery"、maxCapacity=1、
                //  chemistry="unknown" 的占位对象），需据此过滤，避免误判有电池。
                //
                // 判定策略：仅当 PowerSource 同时满足以下条件时才认为存在真实电池：
                //   1. maxCapacity > 1（占位对象的 maxCapacity 恒为 1，真实电池
                //      的容量通常远大于 1）
                //   2. remainingCapacityPercent 在有效范围 [0.0, 1.0] 内
                //   3. chemistry 非空且不是占位值（如 "unknown"、"none" 等）
                //
                // 注意：不能检查 isCharging/isDischarging，因为充满电的电池
                // 两个状态都可能为 false，会导致真实电池被误过滤。
                val ps = powerSources.firstOrNull { src ->
                    // 1. 最大容量检查（占位对象 maxCapacity=1，真实电池远大于 1）
                    val capOk = try {
                        src.maxCapacity > 1
                    } catch (_: Exception) {
                        false
                    }

                    // 2. 剩余容量百分比检查（有效值应在 [0.0, 1.0] 范围内）
                    val pctOk = try {
                        val pct = src.remainingCapacityPercent
                        pct in 0.0..1.0
                    } catch (_: Exception) {
                        false
                    }

                    // 3. 化学类型检查（真正的电池有化学类型，占位对象为 "unknown"）
                    val chemOk = try {
                        val chem = src.chemistry
                        !chem.isNullOrBlank() &&
                            !chem.equals("unknown", ignoreCase = true) &&
                            !chem.equals("none", ignoreCase = true) &&
                            !chem.equals("n/a", ignoreCase = true)
                    } catch (_: Exception) {
                        false
                    }

                    capOk && pctOk && chemOk
                }

                if (ps != null) {
                    cachedHasBattery = true
                    // Charging state: on AC (powerOnLine) and/or actively charging.
                    // isCharging can be unreliable on some platforms, so combine
                    // it with powerOnLine for a more robust detection.
                    cachedIsCharging = ps.isPowerOnLine || ps.isCharging
                    // OSHI's remainingCapacityPercent is a fraction in [0.0, 1.0].
                    // HOWEVER on Windows it is computed solely from SystemBatteryState
                    // (CallNtPowerInformation): it defaults to 1.0 when the call fails,
                    // and Windows usually reports remainingCapacity == maxCapacity (or
                    // maxCapacity == 0 → Infinity, capped to 1.0), so it is stuck at 100%.
                    // The accurate values read via DeviceIoControl (BATTERY_STATUS.Capacity
                    // / BATTERY_INFORMATION.FullChargedCapacity) are exposed as
                    // currentCapacity/maxCapacity in the SAME units, so derive the
                    // percentage from their ratio, keeping remainingCapacityPercent as a
                    // cross-platform fallback when the capacities are unavailable.
                    val maxCap = ps.maxCapacity.toDouble()
                    val curCap = ps.currentCapacity.toDouble()
                    val frac = if (maxCap > 0 && curCap >= 0) curCap / maxCap else ps.remainingCapacityPercent
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

    // ---- Bluetooth ----
    // Detecting the bluetooth adapter model requires an OS subprocess query, which
    // can block. To keep the UI non-blocking, we run it once on a background daemon
    // thread and persist the result in memory: once detected, the adapter name is
    // cached forever (no repeated subprocess calls). Only the adapter model is
    // detected here; the connected-device count has been removed.
    @Volatile
    private var cachedBluetoothHasAdapter: Boolean = false
    @Volatile
    private var cachedBluetoothModel: String = "未知"
    @Volatile
    private var bluetoothInitialized: Boolean = false
    private var bluetoothInFlight = false

    // Runs the bluetooth adapter-model query once on a background daemon thread
    // and persists the result in memory. After completion, bluetoothInitialized
    // becomes true and no further detection is triggered.
    private fun refreshBluetoothAsync() {
        if (bluetoothInFlight || bluetoothInitialized) return
        bluetoothInFlight = true
        thread(start = true, isDaemon = true) {
            try {
                val os = System.getProperty("os.name").lowercase()
                if (os.contains("windows")) {
                    // Windows: query PnP devices for the bluetooth adapter model.
                    // Use executeCommandUtf8 so the device name decodes correctly (no 乱码).
                    val out = executeCommandUtf8(
                        "Get-PnpDevice -Class Bluetooth | Where-Object { \$PSItem.Status -eq 'OK' } | Select-Object -First 1 -ExpandProperty FriendlyName"
                    )
                    val lines = out.lines().map { it.trim() }.filter { it.isNotBlank() }
                    if (lines.isNotEmpty()) {
                        cachedBluetoothHasAdapter = true
                        cachedBluetoothModel = lines[0].ifBlank { "未知" }
                    } else {
                        cachedBluetoothHasAdapter = false
                    }
                } else if (os.contains("linux")) {
                    // Linux: use hciconfig to detect the adapter model.
                    val hci = executeCommand("hciconfig -a 2>/dev/null | head -1")
                    if (hci.contains("hci")) {
                        cachedBluetoothHasAdapter = true
                        cachedBluetoothModel = hci.trim()
                    } else {
                        cachedBluetoothHasAdapter = false
                    }
                } else {
                    cachedBluetoothHasAdapter = false
                }
            } catch (_: Exception) {
                cachedBluetoothHasAdapter = false
            } finally {
                bluetoothInFlight = false
                bluetoothInitialized = true
            }
        }
    }

    fun getBluetooth(): BluetoothInfo {
        // Only trigger detection once; afterwards the cached values are reused
        // forever (persisted in memory, no repeated subprocess queries).
        if (!bluetoothInitialized) {
            refreshBluetoothAsync()
        }
        return BluetoothInfo(
            hasAdapter = cachedBluetoothHasAdapter,
            adapterModel = cachedBluetoothModel
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

        // 桌面壁纸路径决策：
        // - 用户自选了本地图像（customBgFileName 非空）→ 使用 cardbg/ 下的该文件。
        // - 否则，若"不获取桌面壁纸"开启（Linux 上强制开启）→ 返回 null（走默认壁纸）。
        // - Windows 且未开启"不获取桌面壁纸" → 尝试读取注册表获取真实壁纸。
        var wallpaperPath: String? = null
        val currentOs = System.getProperty("os.name").lowercase()
        
        // 优先：用户自选本地图像
        val customFileName = config.WallpaperState.customBgFileName
        if (!customFileName.isNullOrBlank()) {
            wallpaperPath = java.io.File("cardbg", customFileName).absolutePath
        } else if (!config.WallpaperState.useDefaultWallpaper()) {
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
