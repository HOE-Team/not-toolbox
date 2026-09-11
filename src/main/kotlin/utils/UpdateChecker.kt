// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** 更新检查状态 */
enum class UpdateCheckStatus { IDLE, CHECKING, READY }

/**
 * 更新检查结果。
 *
 * @param supported 该包管理器是否支持检查更新（[PackageManagerType.EMERGE] 不支持）
 * @param failed 检查失败（离线 / 命令报错）
 * @param noticeReported 是否已向用户提示过（不支持 / 失败），避免反复弹出 Snackbar
 * @param updatable 有可用更新的「小写原生包名」集合
 * @param available 可升级到的目标版本
 * @param dismissed 用户已点「立即显示列表」
 */
data class UpdateCheckState(
    val manager: PackageManagerType = PackageManagerType.UNKNOWN,
    val status: UpdateCheckStatus = UpdateCheckStatus.IDLE,
    val supported: Boolean = true,
    val failed: Boolean = false,
    val noticeReported: Boolean = false,
    val updatable: Set<String> = emptySet(),
    val available: Map<String, String> = emptyMap(),
    val dismissed: Boolean = false
)

/**
 * 可用更新检查器。
 *
 * 与 [PackageDetector] 一样遵循「本进程内按需检查、同管理器只查一次」的原则：
 * - 只在工具页完成「拉取列表 + 检测已安装」之后触发
 * - 不落盘：可升级信息时效性强，跨启动复用容易误导
 * - 结果按管理器缓存在内存中，切回时零开销
 */
object UpdateChecker {
    private val mutex = Mutex()

    private val _state = MutableStateFlow(UpdateCheckState())
    val state: StateFlow<UpdateCheckState> = _state.asStateFlow()

    /** 本进程各管理器的检查结果（含 dismissed / noticeReported） */
    private val sessionResults = mutableMapOf<PackageManagerType, UpdateCheckState>()

    /** 进入工具页后调用；本进程已查过该管理器则直接复用结果 */
    suspend fun checkIfNeeded(manager: PackageManagerType) {
        if (manager == PackageManagerType.UNKNOWN) return
        mutex.withLock {
            if (_state.value.status == UpdateCheckStatus.CHECKING) return@withLock
            val cached = sessionResults[manager]
            if (cached != null) {
                if (_state.value.manager != manager) _state.value = cached.copy(manager = manager)
                return@withLock
            }
            performCheck(manager, silent = false)
        }
    }

    /** Snackbar「重试」/ 顶栏「重新检测」：清掉缓存强制重查 */
    suspend fun retryCheck(manager: PackageManagerType) {
        if (manager == PackageManagerType.UNKNOWN) return
        mutex.withLock {
            sessionResults.remove(manager)
            performCheck(manager, silent = false)
        }
    }

    /** 更新成功后静默重查（不显示转圈） */
    suspend fun recheckSilently(manager: PackageManagerType) {
        if (manager == PackageManagerType.UNKNOWN) return
        mutex.withLock {
            sessionResults.remove(manager)
            performCheck(manager, silent = true)
        }
    }

    /** 用户点「立即显示列表」 */
    fun dismiss() {
        val s = _state.value
        if (s.dismissed) return
        val updated = s.copy(dismissed = true)
        _state.value = updated
        sessionResults[s.manager] = updated
    }

    /** 标记已提示过（不支持 / 失败），下次不再弹 */
    fun markNoticeReported() {
        val s = _state.value
        if (s.noticeReported) return
        val updated = s.copy(noticeReported = true)
        _state.value = updated
        sessionResults[s.manager] = updated
    }

    /** 该包是否有可用更新 */
    fun hasUpdate(manager: PackageManagerType, nativeName: String?): Boolean {
        val s = _state.value
        if (s.manager != manager || nativeName.isNullOrBlank()) return false
        return nativeName.lowercase() in s.updatable
    }

    /** 可升级到的目标版本（无则 null） */
    fun availableVersion(nativeName: String?): String? {
        if (nativeName.isNullOrBlank()) return null
        return _state.value.available[nativeName.lowercase()]
    }

    /** 当前管理器的检查是否正在进行 */
    fun isChecking(): Boolean = _state.value.status == UpdateCheckStatus.CHECKING

    private suspend fun performCheck(manager: PackageManagerType, silent: Boolean) {
        val previous = _state.value

        // 不支持检查：不进入 CHECKING（不转圈），按钮保持可用
        if (!isUpdateCheckSupported(manager)) {
            val state = UpdateCheckState(
                manager = manager,
                status = UpdateCheckStatus.READY,
                supported = false,
                noticeReported = if (previous.manager == manager) previous.noticeReported else false,
                dismissed = true
            )
            _state.value = state
            sessionResults[manager] = state
            return
        }

        if (!silent) {
            _state.value = UpdateCheckState(
                manager = manager,
                status = UpdateCheckStatus.CHECKING,
                supported = true
            )
        }

        val result = queryAvailableUpdates(manager)
        val keepDismissed = if (previous.manager == manager) previous.dismissed else true
        val state = if (result == null) {
            UpdateCheckState(
                manager = manager,
                status = UpdateCheckStatus.READY,
                supported = true,
                failed = true,
                noticeReported = false,
                dismissed = true
            )
        } else {
            UpdateCheckState(
                manager = manager,
                status = UpdateCheckStatus.READY,
                supported = true,
                failed = false,
                noticeReported = false,
                updatable = result.keys,
                available = result,
                dismissed = if (silent) keepDismissed else true
            )
        }
        _state.value = state
        sessionResults[manager] = state
    }
}

// ============================================================================
// 检查实现（每个包管理器一次批量命令）
// ============================================================================

private val isWindowsHost: Boolean by lazy {
    System.getProperty("os.name").lowercase().contains("windows")
}

/** EMERGE 的更新检查需要 `emerge -uDp @world`，动辄数分钟，故标记为不支持 */
private fun isUpdateCheckSupported(manager: PackageManagerType): Boolean = when (manager) {
    PackageManagerType.EMERGE, PackageManagerType.UNKNOWN -> false
    else -> true
}

/** 返回 null 表示检查失败；空 Map 表示确认「无可用更新」 */
private suspend fun queryAvailableUpdates(manager: PackageManagerType): Map<String, String>? =
    withContext(Dispatchers.IO) {
        when (manager) {
            PackageManagerType.WINGET ->
                parseWingetUpgrade(runUpdateCommand("winget upgrade --include-unknown --accept-source-agreements", 120))
            PackageManagerType.CHOCOLATEY ->
                parseChocoOutdated(runUpdateCommand("choco outdated -r --no-progress", 120))
            PackageManagerType.SCOOP ->
                parseScoopStatus(runUpdateCommand("scoop status", 180))
            PackageManagerType.APT ->
                parseAptUpgradable(runUpdateCommand("apt list --upgradable", 120))
            PackageManagerType.PACMAN ->
                parsePacmanQu(runUpdateCommand("pacman -Qu", 60))
            PackageManagerType.DNF ->
                parseDnfCheckUpdate(runUpdateCommand("dnf -q check-update", 180))
            PackageManagerType.ZYPPER ->
                parseZypperListUpdates(runUpdateCommand("zypper --non-interactive list-updates", 180))
            PackageManagerType.NIX ->
                parseNixDryRun(runUpdateCommand("nix-env -u --dry-run", 120))
            PackageManagerType.EMERGE, PackageManagerType.UNKNOWN -> emptyMap()
        }
    }

/** 命令执行结果；保留退出码是因为 `dnf check-update` 用 100 表示"有更新" */
private data class UpdateCommandResult(val exitCode: Int, val output: String)

private fun runUpdateCommand(command: String, timeoutSeconds: Long): UpdateCommandResult? {
    return try {
        val cmdArray = if (isWindowsHost) {
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
        } else {
            UpdateCommandResult(process.exitValue(), output)
        }
    } catch (_: Exception) {
        null
    }
}

/** 退出码必须是 [expected] 之一，否则视为检查失败 */
private fun UpdateCommandResult?.accept(vararg expected: Int): UpdateCommandResult? {
    val result = this ?: return null
    return if (result.exitCode in expected) result else null
}


// ============================================================================
// 输出解析
// ============================================================================

/**
 * `winget upgrade --include-unknown`（列格式 `Name Id Version Available Source`）。
 * 不做列对齐解析：改用**已安装 ID 集合**做子串匹配（Id 不含空格）。
 * 命中后取其后第 2 个 token 作为 Available 版本（第 1 个是当前的 Version 列）。
 */
private fun parseWingetUpgrade(result: UpdateCommandResult?): Map<String, String>? {
    val ok = result.accept(0) ?: return null
    val known = PackageDetector.snapshot?.packages?.keys ?: return emptyMap()
    val updates = HashMap<String, String>()
    for (raw in ok.output.lineSequence()) {
        val line = raw.trimEnd()
        if (line.isBlank()) continue
        val lower = line.lowercase()
        for (id in known) {
            val index = lower.indexOf(id)
            if (index < 0) continue
            val after = line.substring(index + id.length)
            // 去掉 Source 列（winget / msstore），剩下的依次是「当前版本」「可升级版本」
            val tokens = after.split(Regex("\\s+"))
                .filter { it.isNotBlank() && !it.equals("winget", true) && !it.equals("msstore", true) }
            updates[id] = tokens.getOrNull(1) ?: tokens.firstOrNull().orEmpty()
            break
        }
    }
    return updates
}

/** `choco outdated -r`（管道分隔：`name|current|available|pinned`） */
private fun parseChocoOutdated(result: UpdateCommandResult?): Map<String, String>? {
    val ok = result.accept(0) ?: return null
    val updates = HashMap<String, String>()
    for (raw in ok.output.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty() || !line.contains('|')) continue
        val parts = line.split('|')
        if (parts.size < 3) continue
        val name = parts[0].trim()
        if (name.isEmpty() || name.equals("name", ignoreCase = true)) continue
        updates[name.lowercase()] = parts[2].trim()
    }
    return updates
}

/** `scoop status`（取前 3 列：Name / Installed Version / Latest Version） */
private fun parseScoopStatus(result: UpdateCommandResult?): Map<String, String>? {
    val ok = result.accept(0) ?: return null
    val updates = HashMap<String, String>()
    for (raw in ok.output.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        if (line.startsWith("Name") || line.startsWith("----") || line.startsWith("WARN")) continue
        val cols = line.split(Regex("\\s{2,}")).map { it.trim() }
        if (cols.size < 3) continue
        val name = cols[0]
        if (name.isEmpty()) continue
        updates[name.lowercase()] = cols[2]
    }
    return updates
}

/** `apt list --upgradable`（`pkg/suite arch version [upgradable from: old]`） */
private fun parseAptUpgradable(result: UpdateCommandResult?): Map<String, String>? {
    val ok = result.accept(0) ?: return null
    val updates = HashMap<String, String>()
    for (raw in ok.output.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty() || !line.contains('/')) continue
        val slash = line.indexOf('/')
        val name = line.substring(0, slash).trim()
        if (name.isEmpty()) continue
        val after = line.substring(slash + 1).trim()
        updates[name.lowercase()] = after.split(Regex("\\s+")).getOrNull(1).orEmpty()
    }
    return updates
}

/** `pacman -Qu`（`pkg old -> new`；无输出表示无更新） */
private fun parsePacmanQu(result: UpdateCommandResult?): Map<String, String>? {
    val ok = result.accept(0) ?: return null
    val updates = HashMap<String, String>()
    for (raw in ok.output.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val arrow = line.indexOf(" -> ")
        if (arrow <= 0) continue
        val name = line.substring(0, arrow).trim().substringBefore(' ')
        if (name.isEmpty()) continue
        val target = line.substring(arrow + 4).trim()
        updates[name.lowercase()] = target.split(Regex("\\s+")).firstOrNull().orEmpty()
    }
    return updates
}

/**
 * `dnf -q check-update`：退出码 **100 = 有更新**、0 = 无更新、1 = 错误。
 * 输出每行 `name.arch  version  repo`。
 */
private fun parseDnfCheckUpdate(result: UpdateCommandResult?): Map<String, String>? {
    val ok = result.accept(0, 100) ?: return null
    val updates = HashMap<String, String>()
    for (raw in ok.output.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        if (line.startsWith("Obsoleting") || line.startsWith("Last metadata")) continue
        val tokens = line.split(Regex("\\s+"))
        if (tokens.size < 3) continue
        val name = tokens[0].substringBefore('.')
        if (name.isEmpty()) continue
        updates[name.lowercase()] = tokens[1]
    }
    return updates
}

/** `zypper --non-interactive list-updates`（表格：S | Repository | Name | Current | Available | Arch） */
private fun parseZypperListUpdates(result: UpdateCommandResult?): Map<String, String>? {
    val ok = result.accept(0) ?: return null
    val updates = HashMap<String, String>()
    for (raw in ok.output.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty() || !line.contains('|')) continue
        val cols = line.split('|').map { it.trim() }
        if (cols.size < 5) continue
        val name = cols[2]
        if (name.isEmpty() || name.equals("Name", ignoreCase = true) || name.startsWith("--")) continue
        updates[name.lowercase()] = cols[4]
    }
    return updates
}

/** `nix-env -u --dry-run`（`upgrading 'git-2.45.1' to 'git-2.46.0'`） */
private fun parseNixDryRun(result: UpdateCommandResult?): Map<String, String>? {
    val ok = result.accept(0) ?: return null
    val updates = HashMap<String, String>()
    val pattern = Regex("upgrading '([^']+)' to '([^']+)'")
    for (raw in ok.output.lineSequence()) {
        val match = pattern.find(raw.trim()) ?: continue
        val oldName = match.groupValues[1]
        val newName = match.groupValues[2]
        val name = oldName.substringBeforeLast('-')
        if (name.isEmpty()) continue
        updates[name.lowercase()] = newName.substringAfterLast('-')
    }
    return updates
}




