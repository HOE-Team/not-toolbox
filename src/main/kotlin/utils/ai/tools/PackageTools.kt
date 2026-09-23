// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 软件目录与已安装软件包相关工具

package utils.ai.tools

import utils.CommonPackages
import utils.PackageDetector
import utils.PackageInfo
import utils.PackageListLoader
import utils.PackageManagerType
import utils.PackageManagerUtils
import utils.TerminalSessionManager
import utils.UpdateCheckStatus
import utils.UpdateChecker
import utils.ai.ToolCall
import utils.ai.ToolDanger
import utils.ai.ToolDef
import utils.ai.ToolEnv
import utils.ai.ToolParamSpec
import utils.ai.ToolResult
import utils.ai.ToolSpec
import utils.ai.intArg
import utils.ai.stringArg

object PackageTools {
    /** 目录缓存（按包管理器区分），避免一次对话内反复联网 */
    private var catalogCache: List<PackageInfo> = emptyList()
    private var catalogManager: PackageManagerType = PackageManagerType.UNKNOWN

    val all: List<ToolDef> = listOf(
        ToolDef(
            spec = ToolSpec(
                name = "list_packages",
                description = "列出软件目录条目（可在线获取，离线时回退到内置目录）。支持按分类或关键词过滤；" +
                    "返回的条目会带上目标平台的原生包名，可直接用于安装。",
                runningHint = "读取软件目录",
                params = listOf(
                    ToolParamSpec("query", "string", "关键词过滤（匹配名称或描述）"),
                    ToolParamSpec("category", "string", "分类名过滤，如 浏览器、开发工具；留空表示不限"),
                    ToolParamSpec("limit", "integer", "最多返回条数，默认 30，最大 200")
                )
            ),
            handler = { call, env ->
                filterCatalog(env, call.stringArg("query"), call.stringArg("category"), call.intArg("limit") ?: 30)
            }
        ),
        ToolDef(
            spec = ToolSpec(
                name = "list_installed",
                description = "列出本机已安装的软件包（使用包管理器原生包名），可按关键词过滤。",
                runningHint = "读取已安装列表",
                slowFirstRun = true,
                params = listOf(
                    ToolParamSpec("query", "string", "关键词过滤（原生包名包含该字符串）"),
                    ToolParamSpec("limit", "integer", "最多返回条数，默认 50，最大 500")
                )
            ),
            handler = { call, env -> listInstalled(call, env) }
        ),
        ToolDef(
            spec = ToolSpec(
                name = "get_package_detail",
                description = "查询某个软件包是否已安装、已安装版本，以及是否有可用更新。参数可以是目录显示名或原生包名。",
                runningHint = "查询软件包状态",
                slowFirstRun = true,
                params = listOf(
                    ToolParamSpec("name", "string", "软件名（目录显示名或原生包名）", required = true)
                )
            ),
            handler = { call, env -> packageDetail(call, env) }
        ),
        ToolDef(
            spec = ToolSpec(
                name = "check_updates",
                description = "检查本机已安装软件包的可升级列表（与原「检查更新」功能一致）。",
                runningHint = "检查可升级项",
                slowFirstRun = true,
                params = listOf(
                    ToolParamSpec("limit", "integer", "最多返回条数，默认 100，最大 500")
                )
            ),
            handler = { call, env -> checkUpdates(call, env) }
        ),
        ToolDef(
            spec = ToolSpec(
                name = "install_package",
                description = "通过系统包管理器安装指定软件包。会修改系统，执行前需用户确认。",
                runningHint = "安装软件包",
                canContinue = true,
                params = listOf(
                    ToolParamSpec("name", "string", "原生包名（可通过 list_packages 获取）", required = true)
                ),
                danger = ToolDanger.WRITE
            ),
            handler = { call, env -> changePackage(call, env, "install") }
        ),
        ToolDef(
            spec = ToolSpec(
                name = "update_package",
                description = "升级指定的已安装软件包。会修改系统，执行前需用户确认。",
                runningHint = "升级软件包",
                canContinue = true,
                params = listOf(
                    ToolParamSpec("name", "string", "原生包名", required = true)
                ),
                danger = ToolDanger.WRITE
            ),
            handler = { call, env -> changePackage(call, env, "update") }
        ),
        ToolDef(
            spec = ToolSpec(
                name = "uninstall_package",
                description = "卸载指定的已安装软件包。会修改系统，执行前需用户确认。",
                runningHint = "卸载软件包",
                canContinue = true,
                params = listOf(
                    ToolParamSpec("name", "string", "原生包名", required = true)
                ),
                danger = ToolDanger.WRITE
            ),
            handler = { call, env -> changePackage(call, env, "uninstall") }
        )
    )

    // ---------- 只读 ----------

    private suspend fun filterCatalog(
        env: ToolEnv,
        query: String?,
        category: String?,
        limitRaw: Int
    ): ToolResult {
        val limit = limitRaw.coerceIn(1, 200)
        val all = loadCatalog(env)
        if (all.isEmpty()) return ToolResult("暂时无法获取软件目录（网络不可用且本地目录为空）。", isError = true)
        var list = all
        if (!query.isNullOrBlank()) {
            val q = query.lowercase()
            list = list.filter { item ->
                item.name.lowercase().contains(q) || (item.description?.lowercase()?.contains(q) == true)
            }
        }
        if (!category.isNullOrBlank()) {
            val c = category.lowercase()
            list = list.filter { item -> item.category?.lowercase()?.contains(c) == true }
        }
        if (list.isEmpty()) {
            return ToolResult("目录共 " + all.size + " 条，没有匹配的条目。可换关键词或去掉分类过滤。")
        }
        val shown = list.take(limit)
        val sb = StringBuilder()
        sb.append("目录共 ").append(all.size).append(" 条，匹配 ").append(list.size).append(" 条，显示 ")
        sb.append(shown.size).append(" 条（包管理器：").append(env.manager).append("）：\n")
        for (item in shown) {
            sb.append("- ").append(item.name)
            val cat = item.category?.trim()
            if (!cat.isNullOrEmpty()) sb.append(" [").append(cat).append("]")
            val nativeName = item.getPackageNameForManager(env.manager)
            if (!nativeName.isNullOrBlank() && nativeName != item.name) {
                sb.append("（原生包名：").append(nativeName).append("）")
            }
            val desc = item.description?.replace("\n", " ")?.trim()
            if (!desc.isNullOrEmpty()) sb.append("：").append(desc)
            sb.append('\n')
        }
        if (list.size > shown.size) {
            sb.append("如需更多条目，请提高 limit 或提供更精确的关键词。")
        }
        return ToolResult(sb.toString().trim())
    }

    private suspend fun loadCatalog(env: ToolEnv): List<PackageInfo> {
        if (catalogManager == env.manager && catalogCache.isNotEmpty()) return catalogCache
        val proxy = if (env.useProxy) env.proxyUrl else null
        val remote = try {
            PackageListLoader.fetchPackagesFromRemote(env.manager, proxy).getOrNull()
        } catch (_: Exception) {
            null
        }
        val list = if (!remote.isNullOrEmpty()) {
            remote
        } else {
            CommonPackages.loadPackagesForManager(env.manager, env.isDebug)
        }
        catalogCache = list
        catalogManager = env.manager
        return list
    }

    /** 取已安装快照；包管理器未知或数据未就绪时返回 null */
    private suspend fun installedSnapshot(env: ToolEnv) = if (env.manager == PackageManagerType.UNKNOWN) {
        null
    } else {
        PackageDetector.detectIfNeeded(env.manager)
        PackageDetector.state.value.snapshot
    }

    private suspend fun listInstalled(call: ToolCall, env: ToolEnv): ToolResult {
        val snap = installedSnapshot(env) ?: return ToolResult("已安装软件包信息尚未就绪，请稍后重试。", isError = true)
        val query = call.stringArg("query")
        val limit = (call.intArg("limit") ?: 50).coerceIn(1, 500)
        val entries = if (query.isNullOrBlank()) {
            snap.packages.entries.toList()
        } else {
            val q = query.lowercase()
            snap.packages.entries.filter { entry -> entry.key.contains(q) }
        }
        if (entries.isEmpty()) {
            return ToolResult("已安装 " + snap.packages.size + " 个包，没有匹配的关键词。")
        }
        val shown = entries.take(limit)
        val sb = StringBuilder()
        sb.append("包管理器 ").append(env.manager).append("：已安装 ").append(snap.packages.size)
        sb.append(" 个，匹配 ").append(entries.size).append(" 个，显示 ").append(shown.size).append(" 个：\n")
        for (entry in shown) {
            sb.append("- ").append(entry.key)
            val ver = entry.value.version
            if (!ver.isNullOrBlank()) sb.append("（").append(ver).append("）")
            sb.append('\n')
        }
        return ToolResult(sb.toString().trim())
    }

    private suspend fun packageDetail(call: ToolCall, env: ToolEnv): ToolResult {
        val name = call.stringArg("name")
        if (name.isNullOrBlank()) return ToolResult("缺少参数 name。", isError = true)
        val snap = installedSnapshot(env) ?: return ToolResult("已安装软件包信息尚未就绪，请稍后重试。", isError = true)
        val catalogItem = loadCatalog(env).firstOrNull { it.name.equals(name, ignoreCase = true) }
        val nativeName = catalogItem?.getPackageNameForManager(env.manager) ?: name
        val entry = snap.lookup(nativeName)
        val sb = StringBuilder()
        sb.append("查询：").append(name).append("\n")
        sb.append("原生包名：").append(nativeName).append("\n")
        if (entry == null) {
            sb.append("状态：未安装")
        } else {
            sb.append("状态：已安装\n")
            sb.append("版本：").append(entry.version ?: "未知")
        }
        if (UpdateChecker.hasUpdate(env.manager, nativeName)) {
            sb.append("\n可更新至：").append(UpdateChecker.availableVersion(nativeName) ?: "有新版本")
        }
        return ToolResult(sb.toString())
    }

    private suspend fun checkUpdates(call: ToolCall, env: ToolEnv): ToolResult {
        if (env.manager == PackageManagerType.UNKNOWN) {
            return ToolResult("未能识别当前系统的包管理器。", isError = true)
        }
        PackageDetector.detectIfNeeded(env.manager)
        UpdateChecker.checkIfNeeded(env.manager)
        val st = UpdateChecker.state.value
        if (st.status == UpdateCheckStatus.CHECKING) return ToolResult("正在检查更新，请稍后重试。")
        if (st.failed) return ToolResult("检查更新失败（可能是网络不可用）。", isError = true)
        if (!st.supported) return ToolResult("当前包管理器（" + env.manager + "）不支持检查可升级列表。")
        if (st.updatable.isEmpty()) return ToolResult("没有可用更新：已安装软件包均为最新版本。")
        val limit = (call.intArg("limit") ?: 100).coerceIn(1, 500)
        val names = st.updatable.sorted()
        val shown = names.take(limit)
        val sb = StringBuilder()
        sb.append("可升级 ").append(names.size).append(" 个，显示 ").append(shown.size).append(" 个：\n")
        for (n in shown) {
            sb.append("- ").append(n)
            val target = st.available[n]
            if (!target.isNullOrBlank()) sb.append(" → ").append(target)
            sb.append('\n')
        }
        return ToolResult(sb.toString().trim())
    }

    // ---------- 写操作（在此之前已由 ToolDispatcher 征得用户同意） ----------

    /**
     * 安装 / 升级 / 卸载。
     *
     * 关键：`executeCommandAndWait` 只是把命令**投递**到终端就返回，必须再用
     * [TerminalSessionManager.awaitCommandOutcome] 等它真正结束，否则会立刻回给模型
     * 「已执行」，模型据此误判成功——这正是"点了允许就报告安装完毕"的根因。
     */
    private suspend fun changePackage(call: ToolCall, env: ToolEnv, action: String): ToolResult {
        if (env.manager == PackageManagerType.UNKNOWN) {
            return ToolResult("未能识别当前系统的包管理器。", isError = true)
        }
        val name = call.stringArg("name")
        if (name.isNullOrBlank()) return ToolResult("缺少参数 name。", isError = true)
        val command = when (action) {
            "install" -> PackageManagerUtils.getInstallCommand(env.manager, name)
            "update" -> PackageManagerUtils.getPackageUpdateCommand(env.manager, name)
            else -> PackageManagerUtils.getUninstallCommand(env.manager, name)
        } ?: return ToolResult("当前包管理器不支持该操作。", isError = true)
        val verb = when (action) {
            "install" -> "安装"
            "update" -> "升级"
            else -> "卸载"
        }
        val sessionId = TerminalSessionManager.executeCommandAndWait(command)
        val outcome = TerminalSessionManager.awaitCommandOutcome(
            sessionId = sessionId,
            onProgress = { tail -> env.onProgress?.invoke(tail) },
            continueRequested = { env.continueRequested?.invoke() == true }
        )
        val tailNote = if (outcome.outputTail.isBlank()) "" else "\n输出尾部：\n" + outcome.outputTail
        return when {
            !outcome.finished -> ToolResult(
                "【未完成】" + verb + "命令仍在终端运行（用户选择继续），退出码未知。" +
                    "请勿报告已" + verb + "成功；稍后可调用 get_package_detail 复核。" + tailNote
            )

            outcome.cancelled -> ToolResult(verb + "已被用户中断。" + tailNote, isError = true)

            outcome.exitCode == 0 -> ToolResult(verb + "完成（退出码 0）。" + tailNote)

            else -> ToolResult(
                verb + "失败（退出码 " + (outcome.exitCode ?: "未知") + "）。" + tailNote,
                isError = true
            )
        }
    }
}
