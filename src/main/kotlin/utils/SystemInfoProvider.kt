// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

import oshi.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object SystemInfoProvider {
    private val si = SystemInfo()
    private val hardware = si.hardware
    // 缓存的内存频率（MHz），异步填充以避免阻塞调用
    @Volatile
    private var memFreqMHzCached: Long = 0L

    // 一次性探测的完成标志：首次采集前会等待它们（带超时），以保证首帧数据完整。
    // bluetooth 用已有的 bluetoothInitialized。
    @Volatile
    private var memFreqInitialized: Boolean = false
    @Volatile
    private var gpuPresentInitialized: Boolean = false
    @Volatile
    private var cellularInitialized: Boolean = false

    init {
        detectMemFreqAsync()
        // 立即预启动蓝牙探测，使卡片在首帧渲染时就可见
        // （而不是几秒后才出现）。
        refreshBluetoothAsync()
        // 预启动在位显卡探测，使"已安装的GPU"卡片在首帧渲染时就正确
        // （否则首帧会短暂显示历史幽灵显卡，直到后台刷新完成）。
        refreshPresentGpusAsync()
        // 预启动蜂窝/LTE 探测，使网络图标与运营商在首帧渲染时就正确。
        refreshCellularAsync()
    }

    private fun detectMemFreqAsync() {
        thread(start = true, isDaemon = true) {
            try {
                val v = detectMemFreq()
                if (v > 0L) memFreqMHzCached = v
            } catch (_: Exception) {
            } finally {
                memFreqInitialized = true
            }
        }
    }

    private fun detectMemFreq(): Long {
        // 基于反射的探测（沿用此前的启发式策略）
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

        // 平台相关的兜底方案
        val os = System.getProperty("os.name").lowercase()
        
        if (os.contains("windows")) {
            // Windows 兜底方案
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
            // 使用 dmidecode 的 Linux 兜底方案
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
            
            // 备选方案：从 /proc/cpuinfo 读取内存频率线索
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

    // executeCommand 的 UTF-8 强制解码版本，用于输出可能包含非 ASCII 文本的
    // PowerShell 命令（例如带中文的蓝牙设备名）。若不显式按 UTF-8 解码，
    // 原始字节会被错误解读并显示为乱码。
    private fun executeCommandUtf8(command: String): String {
        return try {
            val os = System.getProperty("os.name").lowercase()
            // 包装命令，使 PowerShell 无论当前代码页如何都以 UTF-8 写入 stdout。
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
            runProcessWithTimeout(process, Charset.forName("UTF-8")).trim()
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
                // Linux/macOS 使用 bash/sh
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            }
            runProcessWithTimeout(process, Charset.defaultCharset()).trim()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 读取子进程输出，并保证不会无限阻塞：
     * 看门狗线程在超过 [PROCESS_TIMEOUT_SECONDS] 后强杀进程，管道随之关闭，
     * 阻塞中的 readText() 自然返回。避免 PowerShell / netsh 卡死导致采集线程永久被占用。
     */
    private fun runProcessWithTimeout(process: Process, charset: Charset): String {
        thread(start = true, isDaemon = true) {
            try {
                if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            } catch (_: Exception) {
            }
        }
        val reader = BufferedReader(InputStreamReader(process.inputStream, charset))
        val output = reader.readText()
        reader.close()
        return output
    }

    // 缓存上一次的 CPU ticks，用于非阻塞地计算负载
    private var prevCpuTicks: LongArray? = null

    // 网络 I/O 跟踪（原始字节数 + 时间戳，用于计算速率）
    @Volatile
    private var prevNetRxBytes = 0L
    @Volatile
    private var prevNetTxBytes = 0L
    @Volatile
    private var prevNetSampleNanos = 0L

    // WiFi SSID + IPv4 探测（带缓存，定期刷新，避免每秒执行阻塞式子进程）
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

    // 当前在位显卡显示名集合（仅 Windows）。
    // OSHI 的 hardware.graphicsCards 会读取注册表中显示适配器的类键，而该键会为
    // 机器上安装过的每块显卡保留一条"幽灵"记录——即使显卡已被物理移除。因此这里
    // 与 PnP 的 "Display" 类交叉比对，只保留当前在位的显卡（状态为 OK/Started；
    // 不在位的幽灵设备状态为 "Unknown"）。由于显卡硬件基本静态，且在 UI 线程调用
    // PowerShell 会阻塞，故改在后台线程刷新。
    @Volatile
    private var cachedPresentGpus: Set<String> = emptySet()
    private var gpuPresentRefreshNanos = 0L
    private val GPU_PRESENT_REFRESH_INTERVAL_NANOS = 30_000_000_000L  // every 30s

    // 蜂窝 / LTE 探测状态（仅 Windows）。因 getNetworkIO() 运行在 UI 线程，
    // 每秒同步调用 `netsh mbn` 会阻塞，故带缓存并在后台线程刷新。
    // 通过 `netsh mbn show interfaces` 判定：若某个移动宽带接口报告
    // "State : Connected"，说明当前正在使用蜂窝网络，且该接口的
    // "Provider Name" 即为运营商。
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

    // 用于识别虚拟 / 软件（非物理）网络适配器的关键字。
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
            // 取自 OSHI 显示名的物理适配器名称（如 "Intel Dual-Band Wireless AC-8625"），
            // 过滤掉虚拟 / 软件适配器。
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
            // 使用标准 JDK API（跨 OSHI 版本更稳健）获取活动网卡身份信息。
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

    // 在后台线程启动蜂窝/LTE 探测（非阻塞）。
    private fun refreshCellularAsync() {
        thread(start = true, isDaemon = true) {
            try {
                refreshCellular()
            } finally {
                cellularInitialized = true
            }
        }
    }

    // 探测机器当前是否在使用蜂窝（LTE/WWAN）网络，若是则识别运营商。Windows 上
    // `netsh mbn show interfaces` 会列出每个移动宽带接口的连接状态与运营商名称；
    // 某接口为 "Connected" 即表示正在使用蜂窝网络。非 Windows 系统没有移动宽带
    // 概念，直接跳过。任何解析 / 查询失败都保持缓存标志不变（安全默认值）。
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
                        // 新的接口块开始，重置块内局部状态。
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

        // 定期刷新 WiFi SSID / IPv4，避免每秒阻塞
        if (now - wifiLastRefreshNanos > WIFI_REFRESH_INTERVAL_NANOS) {
            refreshWifiSsid()
            refreshNetworkIdentity()
            wifiLastRefreshNanos = now
        }

        // 在后台线程定期刷新蜂窝/LTE 探测
        // （绝不阻塞 UI 线程）。
        if (now - cellularRefreshNanos > CELLULAR_REFRESH_INTERVAL_NANOS) {
            cellularRefreshNanos = now
            refreshCellularAsync()
        }

        // 判定当前活动连接类型：优先 WiFi（有 SSID），其次蜂窝，
        // 再次普通有线 IPv4 连接，否则为无 / 其他。
        val connectionType = when {
            !cachedSSID.isNullOrBlank() -> NetworkConnectionType.WIFI
            cachedCellularConnected -> NetworkConnectionType.CELLULAR
            !cachedIPv4.isNullOrBlank() && !cachedNICName.isNullOrBlank() -> NetworkConnectionType.ETHERNET
            else -> NetworkConnectionType.OTHER
        }
        val operatorName = cachedOperatorName.takeIf { connectionType == NetworkConnectionType.CELLULAR }

        // 累加所有接口的原始计数器
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

    // 在后台线程启动在位显卡探测（非阻塞）。
    private fun refreshPresentGpusAsync() {
        thread(start = true, isDaemon = true) {
            try {
                refreshPresentGpus()
            } finally {
                gpuPresentInitialized = true
            }
        }
    }

    // 将系统当前在位显卡的显示名写入 cachedPresentGpus。Windows 上，幽灵设备
    // （过去安装过但已不在位的显卡）的 PnP 状态为 "Unknown"，而在位设备的状态为
    // "OK"/"Started"。仅当结果非空时才覆盖缓存，避免偶发的查询失败把真实显卡
    // 隐藏掉。非 Windows 系统上 OSHI 的列表本身已准确。
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

    // 归一化显卡名称以便宽容比较（去空白、转小写、合并连续空白），
    // 因为 OSHI 与 PnP 的拼写可能略有差异。
    private fun normalizeGpuName(name: String): String =
        name.trim().lowercase().replace(Regex("\\s+"), " ")

    // 宽松相等判定：归一化后完全相同，或一方包含另一方。采用包含判定作为兜底，
    // 避免厂商字符串的细微差异导致真实在位的显卡被过滤掉。
    private fun gpuNamesMatch(a: String, b: String): Boolean {
        val na = normalizeGpuName(a)
        val nb = normalizeGpuName(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        return na == nb || na.contains(nb) || nb.contains(na)
    }

    fun getSystemInfo(): SystemInfoSnapshot {
        // 处理器（CPU）
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

        // 内存（RAM）
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

        // 显卡（GPU）
        // OSHI 的 hardware.graphicsCards 在 Windows 上会读取注册表中显示适配器的类键，
        // 而该键会为安装过的每块显卡保留一条"幽灵"记录——即使显卡已被移除，导致
        // "已安装的GPU"卡片在只有一块显卡时也会列出多块。这里在后台线程刷新当前在位的
        // PnP 显示设备集合，只保留匹配的显卡。若在位集合为空（非 Windows 或查询失败），
        // 则回退到 OSHI 的完整列表，而不是隐藏显卡。
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

        // 磁盘：通过分区挂载点将 fileStores 映射到 diskStores（避免调用外部命令）
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
                // Windows 盘符
                if (mount.length == 2 || (mount.length == 3 && mount[2] == '\\')) mount.take(2) else "未指定盘符"
            } else {
                // Linux/macOS：使用挂载点或设备名
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

    // ---- 服务（进程与登录用户）----
    // OSHI 的 processCount 与 sessions 都是内存级查询，但为确保绝对非阻塞，
    // 这里仍然缓存结果并最多每 5 秒刷新一次（远低于 1 秒的 UI 心跳，
    // 且不会产生阻塞调用）。
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

    // ---- 电池 ----
    // OSHI 的 powerSources 是内存级查询（无子进程），但仍缓存结果并最多每 5 秒
    // 刷新一次，以保证 UI 零阻塞。
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
                    // 充电状态：已接交流电（powerOnLine）和/或正在充电。
                    // isCharging 在部分平台上不可靠，故与 powerOnLine 结合以获得更稳健的判定。
                    cachedIsCharging = ps.isPowerOnLine || ps.isCharging
                    // OSHI 的 remainingCapacityPercent 是 [0.0, 1.0] 区间内的小数。
                    // 但在 Windows 上它仅由 SystemBatteryState（CallNtPowerInformation）计算：
                    // 调用失败时默认为 1.0，且 Windows 通常报告 remainingCapacity == maxCapacity
                    // （或 maxCapacity == 0 → Infinity，被截断为 1.0），导致该值恒为 100%。
                    // 通过 DeviceIoControl 读取的准确值（BATTERY_STATUS.Capacity /
                    // BATTERY_INFORMATION.FullChargedCapacity）以 currentCapacity/maxCapacity
                    // 暴露，且单位相同，因此用二者之比推导百分比，并在容量不可用时
                    // 以 remainingCapacityPercent 作为跨平台兜底。
                    val maxCap = ps.maxCapacity.toDouble()
                    val curCap = ps.currentCapacity.toDouble()
                    val frac = if (maxCap > 0 && curCap >= 0) curCap / maxCap else ps.remainingCapacityPercent
                    cachedCapacityPercent = if (frac in 0.0..1.0) frac * 100.0 else frac
                    cachedCapacityPercent = cachedCapacityPercent.coerceIn(0.0, 100.0)
                    cachedCycleCount = ps.cycleCount.takeIf { it >= 0 } ?: 0
                    // PowerSource 没有健康度字段，故用循环次数估算
                    // （循环次数越多，电池损耗越大）。
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

    // ---- 屏幕 ----
    // 使用 java.awt 的内存级 API（无子进程、无阻塞）。分辨率与缩放比例从
    // toolkit 读取；5 秒缓存可避免每次心跳都重新查询。
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
                // getDisplayMode() 返回物理 / 原生显示模式（不受系统缩放影响）。
                // bounds 返回的是逻辑（缩放后）分辨率——而此处需要的是硬件分辨率。
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

    // ---- 蓝牙 ----
    // 探测蓝牙适配器型号需要执行操作系统子进程查询，可能会阻塞。为保持 UI 非阻塞，
    // 这里仅在后台守护线程中执行一次并将结果常驻内存：一旦探测到，适配器名称便
    // 永久缓存（不再重复执行子进程）。此处只探测适配器型号；
    // 已连接设备数量功能已移除。
    @Volatile
    private var cachedBluetoothHasAdapter: Boolean = false
    @Volatile
    private var cachedBluetoothModel: String = "未知"
    @Volatile
    private var bluetoothInitialized: Boolean = false
    private var bluetoothInFlight = false

    // 在后台守护线程中执行一次蓝牙适配器型号查询并将结果常驻内存。完成后
    // bluetoothInitialized 置为 true，不再触发后续探测。
    private fun refreshBluetoothAsync() {
        if (bluetoothInFlight || bluetoothInitialized) return
        bluetoothInFlight = true
        thread(start = true, isDaemon = true) {
            try {
                val os = System.getProperty("os.name").lowercase()
                if (os.contains("windows")) {
                    // Windows：查询 PnP 设备以获取蓝牙适配器型号。
                    // 使用 executeCommandUtf8，确保设备名正确解码（不出现乱码）。
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
                    // Linux：使用 hciconfig 探测适配器型号。
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
        // 只触发一次探测；此后永久复用缓存值
        // （常驻内存，不再重复执行子进程查询）。
        if (!bluetoothInitialized) {
            refreshBluetoothAsync()
        }
        return BluetoothInfo(
            hasAdapter = cachedBluetoothHasAdapter,
            adapterModel = cachedBluetoothModel
        )
    }

    // ---- 快照采集（供后台刷新循环调用）----

    /**
     * 等待一次性探测结束，最多 [INITIAL_DATA_TIMEOUT_MS]。
     * 首次进入主页时的转圈即覆盖这段等待，因此首帧拿到的就是完整数据；
     * 探测失败或超时也会继续（best-effort），保证转圈不会无限转。
     * 等待期间会持续上报"仍在探测的项目"供 UI 显示。
     */
    private suspend fun awaitInitialData(timeoutMs: Long = INITIAL_DATA_TIMEOUT_MS) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val pending = pendingProbeLabels()
            if (pending.isEmpty() || System.currentTimeMillis() >= deadline) break
            updateLoadingStage("正在探测硬件信息：${pending.joinToString("、")}")
            delay(50)
        }
    }

    /** 尚未完成的一次性探测项（中文标签） */
    private fun pendingProbeLabels(): List<String> = buildList {
        if (!memFreqInitialized) add("内存频率")
        if (!gpuPresentInitialized) add("显卡设备")
        if (!bluetoothInitialized) add("蓝牙适配器")
        if (!cellularInitialized) add("蜂窝网络")
    }

    /**
     * 采集一次系统快照。
     * **必须在后台线程调用**（首次调用会触发 OSHI 初始化）。
     * 首次采集时会逐步上报"当前正在获取的项目"，供加载占位显示。
     *
     * @param waitForInitialData 是否等待首页的冷启动数据（首次采集时可能会阻塞数秒，
     *   并占用加载进度文案）。首页轮询用默认的 true；AI 助手等后台调用传 false，
     *   避免阻塞对话并避免覆盖首页的加载阶段提示。
     */
    suspend fun collectSnapshot(waitForInitialData: Boolean = true): SystemSnapshot {
        val reportStage = waitForInitialData && _systemSnapshotFlow.value == null
        if (reportStage) awaitInitialData()

        if (reportStage) updateLoadingStage("正在读取：处理器、内存与磁盘")
        val systemInfo = getSystemInfo()
        if (reportStage) updateLoadingStage("正在读取：系统概览")
        val overview = getSystemOverview()
        if (reportStage) updateLoadingStage("正在读取：网络状态")
        val networkIO = getNetworkIO()
        if (reportStage) updateLoadingStage("正在读取：进程与登录用户")
        val services = getServices()
        if (reportStage) updateLoadingStage("正在读取：电池")
        val battery = getBattery()
        if (reportStage) updateLoadingStage("正在读取：屏幕")
        val screen = getScreen()
        if (reportStage) updateLoadingStage("正在读取：蓝牙适配器")
        val bluetooth = getBluetooth()

        return SystemSnapshot(
            systemInfo = systemInfo,
            overview = overview,
            networkIO = networkIO,
            services = services,
            battery = battery,
            screen = screen,
            bluetooth = bluetooth
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
                // Windows：读取注册表
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

// ============ 后台采集：与 UI 线程解耦 ============

/** 一次采集得到的全部系统信息（不可变快照） */
data class SystemSnapshot(
    val systemInfo: SystemInfoSnapshot,
    val overview: SystemOverview,
    val networkIO: NetworkIOInfo,
    val services: ServicesInfo,
    val battery: BatteryInfo,
    val screen: ScreenInfo,
    val bluetooth: BluetoothInfo
)

/** 首次采集等待一次性探测的上限 */
private const val INITIAL_DATA_TIMEOUT_MS = 5_000L

/** 子进程看门狗超时（秒） */
private const val PROCESS_TIMEOUT_SECONDS = 10L

// 文件级声明：UI 侧读取它们不会触发 SystemInfoProvider 的 object 初始化，
// 因此不会在 UI 线程上构造 OSHI。
private val _systemSnapshotFlow = MutableStateFlow<SystemSnapshot?>(null)

/** UI 只读这个；null 表示首次数据尚未就绪（此时显示转圈） */
val systemSnapshotFlow: StateFlow<SystemSnapshot?> = _systemSnapshotFlow.asStateFlow()

/** 首次读取期间"当前正在获取的项目"（仅供加载占位显示副文本） */
private val _loadingStageFlow = MutableStateFlow<String?>(null)
val systemInfoLoadingStage: StateFlow<String?> = _loadingStageFlow.asStateFlow()

/** 仅在文案变化时发布，避免无谓的刷新触发重组 */
private fun updateLoadingStage(text: String) {
    if (_loadingStageFlow.value != text) _loadingStageFlow.value = text
}

/**
 * 后台刷新循环：在 IO 线程采集并发布快照，UI 线程只接收结果。
 * 由主页的 LaunchedEffect 驱动，离开主页即取消（不空转）；已发布的快照会保留，
 * 因此再次进入主页可以瞬间渲染而无需等待。
 */
suspend fun runSystemInfoRefreshLoop() {
    while (currentCoroutineContext().isActive) {
        val snapshot = withContext(Dispatchers.IO) { SystemInfoProvider.collectSnapshot() }
        _systemSnapshotFlow.value = snapshot
        delay(1000)
    }
}
