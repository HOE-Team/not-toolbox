// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

import config.InstalledManagerScan
import config.InstalledPackageEntry
import config.installedPackagesFileExists
import config.loadInstalledPackages
import config.saveInstalledPackages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** 检测状态 */
enum class DetectionStatus { IDLE, SCANNING, READY }

/**
 * 某个包管理器的已安装快照。
 * packages 的键统一为小写原生包名，便于大小写无关的 O(1) 查表。
 */
data class InstalledSnapshot(
    val manager: PackageManagerType,
    val scannedAt: Long,
    val packages: Map<String, InstalledPackageEntry>
) {
    /** 按原生包名查表（大小写无关） */
    fun lookup(nativeName: String?): InstalledPackageEntry? {
        if (nativeName.isNullOrBlank()) return null
        return packages[nativeName.lowercase()]
    }
}

/** UI 可观察的检测状态 */
data class DetectionState(
    val manager: PackageManagerType = PackageManagerType.UNKNOWN,
    val status: DetectionStatus = DetectionStatus.IDLE,
    val snapshot: InstalledSnapshot? = null,
    val fromCache: Boolean = false
) {
    val isScanning: Boolean get() = status == DetectionStatus.SCANNING
}

/**
 * 已安装包检测器。
 *
 * 设计要点：
 * - **低延迟**：优先直接读取包管理器在磁盘上的元数据（目录 / 文本数据库），零子进程；仅 RPM 系与
 *   Winget 需要一次性批量命令（`rpm -qa` / `winget export`）。
 * - **只在必要时检测**：满足以下任一条件才真正扫描
 *   1. 本进程内该包管理器尚未检测过（含重启后第一次进入工具页）
 *   2. 切换到本进程尚未检测过的包管理器
 *   3. 之前检测过、但 `installed_packages.json` 已被删除
 *   除此之外（包括反复进出工具页）不读盘、不扫描。
 * - **安装 / 卸载不重扫**：只对快照与缓存文件做增量增删。
 * - **全量集合**：快照保存该包管理器的全部已安装包，因此远程目录更新无需重新探测。
 */
object PackageDetector {
    private val mutex = Mutex()

    private val _state = MutableStateFlow(DetectionState())
    /** 当前检测状态（UI 通过 collectAsState 订阅） */
    val state: StateFlow<DetectionState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    /** 最近一次失败信息（如 RPM/Winget 命令不可用），供 UI 提示 */
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 本进程内各包管理器的快照；切回已检测过的管理器时零 IO 秒显 */
    private val sessionSnapshots = mutableMapOf<PackageManagerType, InstalledSnapshot>()

    /** 正在后台刷新的管理器（避免同一个管理器并发扫描） */
    private val refreshing = mutableSetOf<PackageManagerType>()

    /**
     * 后台刷新作用域：进程级。仅用于「缓存优先」模式下把刷新挪出调用方路径——
     * 调用方拿完缓存就返回了，刷新必须继续跑完，因此不能借用调用方的作用域。
     */
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 每个管理器「检测完成后缓存文件是否存在」，用于判定「缓存被删除」这一触发条件 */
    private val cachePresentAfterDetection = mutableMapOf<PackageManagerType, Boolean>()

    /** 当前快照（null 表示尚未检测） */
    val snapshot: InstalledSnapshot? get() = _state.value.snapshot

    /** 当前是否正在检测（UI 据此显示转圈并隐藏卡片） */
    val isDetecting: Boolean get() = _state.value.status == DetectionStatus.SCANNING

    /**
     * 进入工具页或切换包管理器时调用，也供内置工具在需要时调用。
     *
     * 顺序：进程内快照 → 磁盘缓存（立即返回 + 后台静默刷新）→ 真正扫描。
     *
     * 第三档（真正扫描）是慢操作：Windows 上 `winget list` 通常几秒，`winget export` 实测可达 109 秒。
     * 因此有磁盘缓存时一律先用缓存把界面点亮，刷新交给后台，绝不让调用方空等。
     */
    suspend fun detectIfNeeded(manager: PackageManagerType) {
        if (manager == PackageManagerType.UNKNOWN) return
        mutex.withLock {
            if (_state.value.status == DetectionStatus.SCANNING) return@withLock
            val session = sessionSnapshots[manager]
            val cacheMissing = cachePresentAfterDetection[manager] == true && !installedPackagesFileExists()
            if (session != null && !cacheMissing) {
                // 命中进程内快照：仅切换当前视图
                if (_state.value.manager != manager || _state.value.snapshot == null) {
                    _state.value = DetectionState(manager, DetectionStatus.READY, session, fromCache = false)
                }
                return@withLock
            }
            if (session == null) {
                val cached = loadInstalledPackages().scans[manager.name]
                if (cached != null && cached.packages.isNotEmpty()) {
                    val snapshot = InstalledSnapshot(manager, cached.scannedAt, cached.packages)
                    sessionSnapshots[manager] = snapshot
                    _state.value = DetectionState(manager, DetectionStatus.READY, snapshot, fromCache = true)
                    scheduleRefresh(manager)
                    return@withLock
                }
            }
            detectLocked(manager)
        }
    }

    /**
     * 后台静默刷新（同一管理器同时只会有一个刷新在跑）。
     * 用进程级作用域而不是调用方作用域：调用方拿完缓存就走了，刷新必须继续跑完。
     */
    private fun scheduleRefresh(manager: PackageManagerType) {
        if (!refreshing.add(manager)) return
        refreshScope.launch {
            try {
                mutex.withLock { detectLocked(manager, silent = true) }
            } finally {
                refreshing.remove(manager)
            }
        }
    }

    /** 无条件重新检测（顶栏「重新检测」按钮） */
    suspend fun rescan(manager: PackageManagerType) {
        if (manager == PackageManagerType.UNKNOWN) return
        mutex.withLock {
            if (_state.value.status == DetectionStatus.SCANNING) return@withLock
            detectLocked(manager)
        }
    }

    /**
     * 静默重新检测：不进入 [DetectionStatus.SCANNING]，因此 UI 不会转圈、卡片不会消失。
     * 用于「更新」成功后刷新版本号。
     */
    suspend fun rescanSilently(manager: PackageManagerType) {
        if (manager == PackageManagerType.UNKNOWN) return
        mutex.withLock {
            detectLocked(manager, silent = true)
        }
    }

    /**
     * 实际执行检测；调用方必须已持有 [mutex]。
     * @param silent true 时不切换为 SCANNING（UI 不显示转圈）
     */
    private suspend fun detectLocked(manager: PackageManagerType, silent: Boolean = false) {
        if (!silent) {
            _state.value = DetectionState(
                manager = manager,
                status = DetectionStatus.SCANNING,
                snapshot = sessionSnapshots[manager],
                fromCache = false
            )
        }
        val scanned = scan(manager)
        if (scanned == null) {
            // 扫描失败（如离线导致 Winget / RPM 命令不可用）：
            // 沿用旧快照，避免把已有数据清空；无旧快照时用空快照占位，避免反复重试
            val cached = loadInstalledPackages().scans[manager.name]
            val fallback = sessionSnapshots[manager]
                ?: cached?.let { InstalledSnapshot(manager, it.scannedAt, it.packages) }
                ?: InstalledSnapshot(manager, System.currentTimeMillis(), emptyMap())
            sessionSnapshots[manager] = fallback
            _state.value = DetectionState(manager, DetectionStatus.READY, fallback, fromCache = true)
            _message.value = failedHint(manager) ?: "已安装检测失败"
        } else {
            persist(manager, scanned)
            sessionSnapshots[manager] = scanned
            _state.value = DetectionState(manager, DetectionStatus.READY, scanned, fromCache = false)
            _message.value = null
        }
        // 记录「检测后缓存文件是否存在」，供「缓存被删除」判定使用
        cachePresentAfterDetection[manager] = installedPackagesFileExists()
    }

    /** 安装成功后增量更新（不重扫） */
    suspend fun onPackageInstalled(manager: PackageManagerType, nativeName: String?, version: String? = null) {
        if (manager == PackageManagerType.UNKNOWN || nativeName.isNullOrBlank()) return
        mutate(manager) { packages -> packages + (nativeName.lowercase() to InstalledPackageEntry(version = version)) }
    }

    /** 卸载成功后增量更新（不重扫） */
    suspend fun onPackageUninstalled(manager: PackageManagerType, nativeName: String?) {
        if (manager == PackageManagerType.UNKNOWN || nativeName.isNullOrBlank()) return
        mutate(manager) { packages -> packages - nativeName.lowercase() }
    }

    private suspend fun mutate(
        manager: PackageManagerType,
        transform: (Map<String, InstalledPackageEntry>) -> Map<String, InstalledPackageEntry>
    ) {
        mutex.withLock {
            val base = sessionSnapshots[manager]?.packages
                ?: loadInstalledPackages().scans[manager.name]?.packages
                ?: emptyMap()
            val updated = InstalledSnapshot(manager, System.currentTimeMillis(), transform(base))
            persist(manager, updated)
            sessionSnapshots[manager] = updated
            if (_state.value.manager == manager) {
                _state.value = DetectionState(manager, DetectionStatus.READY, updated, fromCache = false)
            }
            cachePresentAfterDetection[manager] = installedPackagesFileExists()
        }
    }

    private fun persist(manager: PackageManagerType, snapshot: InstalledSnapshot) {
        val file = loadInstalledPackages()
        val scans = file.scans + (manager.name to InstalledManagerScan(snapshot.scannedAt, snapshot.packages))
        saveInstalledPackages(file.copy(scans = scans))
    }

    /** 大小写无关地查表（非响应式；UI 请订阅 [state]）。 */
    fun lookup(manager: PackageManagerType, nativeName: String?): InstalledPackageEntry? {
        val s = _state.value
        if (s.manager != manager) return null
        return s.snapshot?.lookup(nativeName)
    }
}

// ============================================================================
// 扫描实现（优先零子进程的本地元数据读取）
// ============================================================================

private val isWindowsPlatform: Boolean by lazy {
    System.getProperty("os.name").lowercase().contains("windows")
}

/** 扫描指定包管理器；返回 null 表示无法读取（命令失败 / 不可用） */
private suspend fun scan(manager: PackageManagerType): InstalledSnapshot? = withContext(Dispatchers.IO) {
    val packages: Map<String, InstalledPackageEntry> = when (manager) {
        PackageManagerType.APT -> scanDpkg()
        PackageManagerType.DNF, PackageManagerType.ZYPPER -> scanRpm()
        PackageManagerType.PACMAN -> scanPacman()
        PackageManagerType.EMERGE -> scanEmerge()
        PackageManagerType.NIX -> scanNix()
        PackageManagerType.SCOOP -> scanScoop()
        PackageManagerType.CHOCOLATEY -> scanChocolatey()
        PackageManagerType.WINGET -> scanWinget()
        PackageManagerType.UNKNOWN -> emptyMap()
    } ?: return@withContext null
    InstalledSnapshot(manager, System.currentTimeMillis(), packages)
}

private fun failedHint(manager: PackageManagerType): String? = when (manager) {
    PackageManagerType.WINGET -> "未能读取 Winget 已安装列表（winget 未在超时内返回），当前显示缓存数据"
    PackageManagerType.DNF, PackageManagerType.ZYPPER -> "未能读取 RPM 数据库"
    else -> null
}

/**
 * APT / Debian：直接解析 `/var/lib/dpkg/status`（纯文本、单文件、零子进程）。
 * 若不可读，回退为列举 `/var/lib/dpkg/info` 下的 `.list` 文件（仅能拿到包名）。
 */
private fun scanDpkg(): Map<String, InstalledPackageEntry> {
    val result = HashMap<String, InstalledPackageEntry>()
    val status = Path.of("/var/lib/dpkg/status")
    if (Files.isReadable(status)) {
        var name: String? = null
        var version: String? = null
        var installed = false
        fun flush() {
            val n = name
            if (installed && !n.isNullOrBlank()) {
                result[n.lowercase()] = InstalledPackageEntry(version = version)
            }
            name = null; version = null; installed = false
        }
        Files.newBufferedReader(status).useLines { lines ->
            for (line in lines) {
                when {
                    line.isBlank() -> flush()
                    line.startsWith("Package: ") -> name = line.substring(9).trim()
                    line.startsWith("Status: ") -> installed = line.substring(8).contains("install ok installed")
                    line.startsWith("Version: ") -> version = line.substring(9).trim()
                }
            }
        }
        flush()
    }
    if (result.isEmpty()) {
        val infoDir = Path.of("/var/lib/dpkg/info")
        if (Files.isDirectory(infoDir)) {
            try {
                Files.newDirectoryStream(infoDir, "*.list").use { stream ->
                    for (f in stream) {
                        val n = f.fileName.toString().removeSuffix(".list")
                        if (n.isNotBlank()) result[n.lowercase()] = InstalledPackageEntry()
                    }
                }
            } catch (_: Exception) { }
        }
    }
    return result
}

/**
 * Pacman / Arch：列举 `/var/lib/pacman/local/<name>-<ver>-<rel>/`。
 * 优先读取条目内 `desc` 以获得精确的 `%NAME%` / `%VERSION%`；失败时从目录名回退解析。
 */
private fun scanPacman(): Map<String, InstalledPackageEntry> {
    val result = HashMap<String, InstalledPackageEntry>()
    val local = Path.of("/var/lib/pacman/local")
    if (!Files.isDirectory(local)) return result
    try {
        Files.newDirectoryStream(local).use { stream ->
            for (dir in stream) {
                if (!Files.isDirectory(dir)) continue
                val dirName = dir.fileName.toString()
                if (dirName == "ALPM_DB_VERSION") continue
                var name: String? = null
                var version: String? = null
                val desc = dir.resolve("desc")
                if (Files.isReadable(desc)) {
                    try {
                        var key: String? = null
                        Files.newBufferedReader(desc).useLines { lines ->
                            for (line in lines) {
                                val trimmed = line.trim()
                                if (trimmed.length >= 2 && trimmed.startsWith("%") && trimmed.endsWith("%")) {
                                    key = trimmed
                                    continue
                                }
                                if (trimmed.isEmpty()) continue
                                when (key) {
                                    "%NAME%" -> if (name == null) name = trimmed
                                    "%VERSION%" -> if (version == null) version = trimmed
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
                val finalName = name
                    ?: dirName.substringBeforeLast('-').substringBeforeLast('-').ifBlank { dirName }
                result[finalName.lowercase()] = InstalledPackageEntry(version = version ?: dirName)
            }
        }
    } catch (_: Exception) { }
    return result
}

/**
 * Emerge / Gentoo：列举 `/var/db/pkg/<category>/<pkg>-<version>/`。
 */
private fun scanEmerge(): Map<String, InstalledPackageEntry> {
    val result = HashMap<String, InstalledPackageEntry>()
    val root = Path.of("/var/db/pkg")
    if (!Files.isDirectory(root)) return result
    try {
        Files.newDirectoryStream(root).use { categories ->
            for (category in categories) {
                if (!Files.isDirectory(category)) continue
                try {
                    Files.newDirectoryStream(category).use { pkgs ->
                        for (pkg in pkgs) {
                            if (!Files.isDirectory(pkg)) continue
                            val dirName = pkg.fileName.toString()
                            val name = dirName.substringBeforeLast('-')
                            val version = dirName.substringAfterLast('-', "")
                            if (name.isNotBlank()) {
                                result[name.lowercase()] = InstalledPackageEntry(version = version.ifBlank { null })
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    } catch (_: Exception) { }
    return result
}

/**
 * Nix：`nix-env -q` 一次性输出（条目形如 `git-2.46.0`）。
 * 无独立版本字段，故整条输出作为包名保存（尽力而为）。
 */
private fun scanNix(): Map<String, InstalledPackageEntry>? {
    val result = HashMap<String, InstalledPackageEntry>()
    val output = PackageDetectorCommand.run("nix-env -q", 30) ?: return null
    output.lineSequence().forEach { line ->
        val name = line.trim()
        if (name.isNotEmpty()) result[name.lowercase()] = InstalledPackageEntry()
    }
    return result
}

/**
 * DNF / Zypper（RPM 系）：rpmdb 为二进制数据库，无法纯 Java 解析，
 * 故用一次批量查询 `rpm -qa --qf '%{NAME}\t%{VERSION}\n'`（制表符分隔，易解析）。
 */
private fun scanRpm(): Map<String, InstalledPackageEntry>? {
    val result = HashMap<String, InstalledPackageEntry>()
    val output = PackageDetectorCommand.run("rpm -qa --qf '%{NAME}\\t%{VERSION}\\n'", 60) ?: return null
    output.lineSequence().forEach { line ->
        if (line.isBlank()) return@forEach
        val parts = line.split('\t')
        val name = parts.getOrNull(0)?.trim().orEmpty()
        if (name.isNotEmpty()) {
            result[name.lowercase()] = InstalledPackageEntry(version = parts.getOrNull(1)?.trim())
        }
    }
    return result
}

/**
 * Scoop：列举 `$SCOOP/apps` 与 `$SCOOP_GLOBAL/apps`（默认为 ~/scoop 与 C:\ProgramData\scoop）。
 */
private fun scanScoop(): Map<String, InstalledPackageEntry> {
    val result = HashMap<String, InstalledPackageEntry>()
    for (root in scoopRoots()) {
        val apps = root.resolve("apps")
        if (!Files.isDirectory(apps)) continue
        try {
            Files.newDirectoryStream(apps).use { stream ->
                for (app in stream) {
                    val name = app.fileName.toString()
                    if (name == "scoop" || !Files.isDirectory(app)) continue
                    result[name.lowercase()] = InstalledPackageEntry()
                }
            }
        } catch (_: Exception) { }
    }
    return result
}

private fun scoopRoots(): List<Path> {
    val roots = linkedSetOf<Path>()
    System.getenv("SCOOP")?.takeIf { it.isNotBlank() }?.let { roots.add(Path.of(it)) }
    System.getenv("SCOOP_GLOBAL")?.takeIf { it.isNotBlank() }?.let { roots.add(Path.of(it)) }
    System.getProperty("user.home")?.takeIf { it.isNotBlank() }?.let { roots.add(Path.of(it, "scoop")) }
    if (isWindowsPlatform) roots.add(Path.of("C:\\ProgramData\\scoop"))
    return roots.toList()
}

/**
 * Chocolatey：列举 `<ChocolateyInstall>/lib`（默认 C:\ProgramData\chocolatey\lib）。
 */
private fun scanChocolatey(): Map<String, InstalledPackageEntry> {
    val result = HashMap<String, InstalledPackageEntry>()
    val root = chocolateyRoot()
    val lib = root.resolve("lib")
    if (!Files.isDirectory(lib)) return result
    try {
        Files.newDirectoryStream(lib).use { stream ->
            for (p in stream) {
                val name = p.fileName.toString()
                if (name == ".chocolatey" || !Files.isDirectory(p)) continue
                result[name.lowercase()] = InstalledPackageEntry()
            }
        }
    } catch (_: Exception) { }
    return result
}

private fun chocolateyRoot(): Path {
    System.getenv("ChocolateyInstall")?.takeIf { it.isNotBlank() }?.let { return Path.of(it) }
    return Path.of("C:\\ProgramData\\chocolatey")
}

/**
 * Winget：优先 `winget list --source winget`（本机实测 **3.00 秒**），失败时回退到
 * `winget export`（结构化 JSON，但本机实测 **109 秒**）。
 *
 * 关键细节（都是实测踩出来的）：
 * - **必须带 `--source winget`**：不带时 winget 会把 ARP / MSIX / msstore 全部枚举一遍，
 *   本机要 107.89 秒，而且返回的 ID 是 `ARP\Machine\X64\...` / `MSIX\...`，
 *   与软件目录里的 winget 包标识符对不上，装了/卸了都无从判断。
 * - `winget --version`、`winget source list` 都是 0.5 秒内返回，所以慢的不是 CLI 本身。
 *
 * 即便如此，这里仍属于「慢操作」：真正的对策是缓存优先 + 不把扫描放进 AI 关键路径
 * （见 [PackageDetector.detectIfNeeded] 与 ai/EnvironmentSummary）。
 */
private fun scanWinget(): Map<String, InstalledPackageEntry>? {
    // 少于 3 条基本可判定解析失败（列宽/本地化格式变化），交给 export 兜底
    scanWingetList()?.takeIf { it.size >= 3 }?.let { return it }
    return scanWingetExport()
}

/**
 * `winget list --disable-interactivity --source winget`：固定宽度列，按「2 个以上空格」分列。
 * 真实输出形如：
 * ```
 * 名称                  ID                            版本            可用
 * --------------------- ----------------------------- --------------- ---------------
 * Eclipse Temurin ...   EclipseAdoptium.Temurin.21.JRE 21.0.12.8       21.0.12.101
 * ```
 * 表头、提示行、列位错乱的行都会被下面的规则挡掉。
 */
private fun scanWingetList(): Map<String, InstalledPackageEntry>? {
    val output = PackageDetectorCommand.run(
        "winget list --disable-interactivity --source winget --accept-source-agreements",
        15
    ) ?: return null
    val columnSplit = Regex("\\s{2,}")
    val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._+-]*$")
    val result = HashMap<String, InstalledPackageEntry>()
    for (rawLine in output.lineSequence()) {
        val line = rawLine.trimEnd()
        if (line.isBlank() || line.startsWith("---")) continue
        val cols = line.split(columnSplit).map { it.trim() }.filter { it.isNotEmpty() }
        if (cols.size < 3) continue
        val id = cols[1]
        if (!idPattern.matches(id) || id.equals("ID", ignoreCase = true)) continue
        val version = cols[2]
        if (version.none { it.isDigit() }) continue
        result[id.lowercase()] = InstalledPackageEntry(version = version)
    }
    return result.ifEmpty { null }
}

/** 回退：`winget export` 的结构化 JSON（完整但很慢，故超时压到 30 秒） */
private fun scanWingetExport(): Map<String, InstalledPackageEntry>? {
    val tmp = Path.of(System.getProperty("java.io.tmpdir"), "ntb-winget-export-${System.nanoTime()}.json")
    return try {
        val cmd = "winget export -o \"${tmp.toAbsolutePath()}\" --include-versions --accept-source-agreements"
        if (PackageDetectorCommand.run(cmd, 30) == null) return null
        if (!Files.exists(tmp)) return null
        val decoded = PackageDetectorCommand.json.decodeFromString<WingetExportFile>(Files.readString(tmp))
        val result = HashMap<String, InstalledPackageEntry>()
        decoded.sources.forEach { source ->
            source.packages.forEach { pkg ->
                if (pkg.packageIdentifier.isNotBlank()) {
                    result[pkg.packageIdentifier.lowercase()] = InstalledPackageEntry(version = pkg.version)
                }
            }
        }
        result
    } catch (_: Exception) {
        null
    } finally {
        try { Files.deleteIfExists(tmp) } catch (_: Exception) { }
    }
}

@Serializable
private data class WingetExportFile(
    @SerialName("Sources") val sources: List<WingetSource> = emptyList()
)

@Serializable
private data class WingetSource(
    @SerialName("Packages") val packages: List<WingetPackage> = emptyList()
)

@Serializable
private data class WingetPackage(
    @SerialName("PackageIdentifier") val packageIdentifier: String = "",
    @SerialName("Version") val version: String? = null
)

// ============================================================================
// 批量命令执行（仅 RPM 系 / Winget / Nix 需要）
// ============================================================================

/**
 * 一次性批量命令执行器。
 * 输出量可能较大，故丢弃 stderr 并完整读取 stdout 后再等待退出。
 */
private object PackageDetectorCommand {
    val json = Json { ignoreUnknownKeys = true }

    fun run(command: String, timeoutSeconds: Long): String? {
        return try {
            val cmdArray = if (isWindowsPlatform) {
                arrayOf("cmd.exe", "/c", command)
            } else {
                arrayOf("sh", "-c", command)
            }
            val process = ProcessBuilder(*cmdArray)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() != 0) {
                null
            } else {
                output
            }
        } catch (_: Exception) {
            null
        }
    }
}







