// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 内建工具的具体实现：直接调用应用自身的包管理 / 系统信息 / 本地列表能力

package utils.ai

import config.MOfflineEntryType
import config.loadConfig
import config.loadOfflineItems
import utils.CommonPackages
import utils.PackageDetector
import utils.PackageInfo
import utils.PackageListLoader
import utils.PackageManagerType
import utils.PackageManagerUtils
import utils.SystemInfoProvider
import utils.TerminalSessionManager
import utils.UpdateCheckStatus
import utils.UpdateChecker
import utils.systemSnapshotFlow
import java.io.File
import java.util.Locale

object BuiltinToolHandlers {
    /** 目录缓存（按包管理器区分），避免一次对话内反复联网 */
    private var catalogCache: List<PackageInfo> = emptyList()
    private var catalogManager: PackageManagerType = PackageManagerType.UNKNOWN

    suspend fun execute(call: ToolCall, ctx: ToolContext): ToolResult {
        return when (call.tool) {
            "list_packages" -> filterCatalog(ctx, call.stringArg("query"), call.stringArg("category"), call.intArg("limit") ?: 30)
            "search_packages" -> searchPackages(call, ctx)
            "list_installed" -> listInstalled(call, ctx)
            "get_installed_detail" -> installedDetail(call, ctx)
            "check_updates" -> checkUpdates(call, ctx)
            "get_system_info" -> systemInfo()
            "get_app_config" -> appConfig(ctx)
            "list_offline_items" -> offlineItems()
            "install_package" -> changePackage(call, ctx, "install")
            "update_package" -> changePackage(call, ctx, "update")
            "uninstall_package" -> changePackage(call, ctx, "uninstall")
            "run_offline_item" -> runOfflineItem(call)
            "run_command" -> runCommand(call)
            else -> ToolResult("未实现的工具：" + call.tool, isError = true)
        }
    }

    // ---------- 只读：目录与已安装 ----------

    private suspend fun searchPackages(call: ToolCall, ctx: ToolContext): ToolResult {
        val query = call.stringArg("query")
        if (query.isNullOrBlank()) return ToolResult("缺少参数 query。", isError = true)
        return filterCatalog(ctx, query, null, call.intArg("limit") ?: 30)
    }

    private suspend fun filterCatalog(
        ctx: ToolContext,
        query: String?,
        category: String?,
        limitRaw: Int
    ): ToolResult {
        val limit = limitRaw.coerceIn(1, 200)
        val all = loadCatalog(ctx)
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
        sb.append(shown.size).append(" 条（包管理器：").append(ctx.manager).append("）：\n")
        for (item in shown) {
            sb.append("- ").append(item.name)
            val cat = item.category?.trim()
            if (!cat.isNullOrEmpty()) sb.append(" [").append(cat).append("]")
            val nativeName = item.getPackageNameForManager(ctx.manager)
            if (!nativeName.isNullOrBlank() && nativeName != item.name) sb.append("（原生包名：").append(nativeName).append("）")
            val desc = item.description?.replace("\n", " ")?.trim()
            if (!desc.isNullOrEmpty()) sb.append("：").append(desc)
            sb.append('\n')
        }
        if (list.size > shown.size) {
            sb.append("如需更多条目，请提高 limit 或提供更精确的关键词。")
        }
        return ToolResult(sb.toString().trim())
    }

    private suspend fun loadCatalog(ctx: ToolContext): List<PackageInfo> {
        if (catalogManager == ctx.manager && catalogCache.isNotEmpty()) return catalogCache
        val proxy = if (ctx.useProxy) ctx.proxyUrl else null
        val remote = try {
            PackageListLoader.fetchPackagesFromRemote(ctx.manager, proxy).getOrNull()
        } catch (_: Exception) {
            null
        }
        val list = if (!remote.isNullOrEmpty()) remote else CommonPackages.loadPackagesForManager(ctx.manager, ctx.isDebug)
        catalogCache = list
        catalogManager = ctx.manager
        return list
    }

    private suspend fun listInstalled(call: ToolCall, ctx: ToolContext): ToolResult {
        if (ctx.manager == PackageManagerType.UNKNOWN) return ToolResult("未能识别当前系统的包管理器。", isError = true)
        PackageDetector.detectIfNeeded(ctx.manager)
        val snap = PackageDetector.state.value.snapshot
            ?: return ToolResult("已安装软件包信息尚未就绪，请稍后重试。", isError = true)
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
        sb.append("包管理器 ").append(ctx.manager).append("：已安装 ").append(snap.packages.size)
        sb.append(" 个，匹配 ").append(entries.size).append(" 个，显示 ").append(shown.size).append(" 个：\n")
        for (entry in shown) {
            sb.append("- ").append(entry.key)
            val ver = entry.value.version
            if (!ver.isNullOrBlank()) sb.append("（").append(ver).append("）")
            sb.append('\n')
        }
        return ToolResult(sb.toString().trim())
    }

    private suspend fun installedDetail(call: ToolCall, ctx: ToolContext): ToolResult {
        val name = call.stringArg("name")
        if (name.isNullOrBlank()) return ToolResult("缺少参数 name。", isError = true)
        if (ctx.manager == PackageManagerType.UNKNOWN) return ToolResult("未能识别当前系统的包管理器。", isError = true)
        PackageDetector.detectIfNeeded(ctx.manager)
        val snap = PackageDetector.state.value.snapshot
            ?: return ToolResult("已安装软件包信息尚未就绪，请稍后重试。", isError = true)
        val catalogItem = loadCatalog(ctx).firstOrNull { it.name.equals(name, ignoreCase = true) }
        val nativeName = catalogItem?.getPackageNameForManager(ctx.manager) ?: name
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
        if (UpdateChecker.hasUpdate(ctx.manager, nativeName)) {
            val target = UpdateChecker.availableVersion(nativeName)
            sb.append("\n可更新至：").append(target ?: "有新版本")
        }
        return ToolResult(sb.toString())
    }

    private suspend fun checkUpdates(call: ToolCall, ctx: ToolContext): ToolResult {
        if (ctx.manager == PackageManagerType.UNKNOWN) return ToolResult("未能识别当前系统的包管理器。", isError = true)
        PackageDetector.detectIfNeeded(ctx.manager)
        UpdateChecker.checkIfNeeded(ctx.manager)
        val st = UpdateChecker.state.value
        if (st.status == UpdateCheckStatus.CHECKING) return ToolResult("正在检查更新，请稍后重试。")
        if (st.failed) return ToolResult("检查更新失败（可能是网络不可用）。", isError = true)
        if (!st.supported) return ToolResult("当前包管理器（" + ctx.manager + "）不支持检查可升级列表。")
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

    // ---------- 只读：系统 / 配置 / 本地列表 ----------

    private suspend fun systemInfo(): ToolResult {
        val snap = systemSnapshotFlow.value ?: SystemInfoProvider.collectSnapshot(waitForInitialData = false)
        val ov = snap.overview
        val si = snap.systemInfo
        val net = snap.networkIO
        val sb = StringBuilder()
        sb.append("系统：").append(ov.platform).append(" ").append(ov.osVersion)
        sb.append("（").append(ov.architecture).append("）\n")
        sb.append("计算机名：").append(ov.computerName).append("\n")
        sb.append("CPU：").append(si.cpu.model).append("，占用 ").append(fmt(si.cpu.usage)).append("%\n")
        sb.append("内存：").append(fmt(si.ram.used)).append("/").append(fmt(si.ram.total)).append(" GB，占用 ")
        sb.append(fmt(si.ram.usage)).append("%\n")
        for (gpu in si.gpus) {
            sb.append("GPU：").append(gpu.model).append("，占用 ").append(fmt(gpu.usage)).append("%\n")
        }
        for (disk in si.disks) {
            sb.append("磁盘 ").append(disk.name.ifBlank { disk.mount }).append("：")
            sb.append(fmt(disk.usedGB)).append("/").append(fmt(disk.totalGB)).append(" GB，占用 ")
            sb.append(fmt(disk.usage)).append("%\n")
        }
        sb.append("网络：↓ ").append(fmt(net.downKBps)).append(" KB/s，↑ ").append(fmt(net.upKBps)).append(" KB/s")
        sb.append("，累计 ↓ ").append(fmt(net.downTotalGB)).append(" GB / ↑ ").append(fmt(net.upTotalGB)).append(" GB\n")
        if (!net.ssid.isNullOrBlank()) sb.append("WiFi：").append(net.ssid).append("\n")
        if (!net.ipv4.isNullOrBlank()) sb.append("IPv4：").append(net.ipv4).append("\n")
        sb.append("进程数：").append(snap.services.processCount)
        sb.append("，登录用户：").append(snap.services.loggedInUsers).append("\n")
        sb.append("屏幕：").append(snap.screen.resolution).append("，缩放 ").append(snap.screen.scalePercent).append("%\n")
        if (snap.battery.hasBattery) {
            sb.append("电池：").append(fmt(snap.battery.capacityPercent)).append("%")
            sb.append(if (snap.battery.isCharging) "（充电中）" else "")
            sb.append("，健康：").append(snap.battery.healthStatus).append("\n")
        }
        sb.append("蓝牙：")
        sb.append(if (snap.bluetooth.hasAdapter) snap.bluetooth.adapterModel else "无适配器")
        return ToolResult(sb.toString().trim())
    }

    private fun appConfig(ctx: ToolContext): ToolResult {
        val c = loadConfig()
        val sb = StringBuilder()
        sb.append("主题：").append(if (c.dark) "深色" else "浅色").append("\n")
        val color = c.color
        if (!color.isNullOrBlank()) sb.append("主题色：").append(color).append("\n")
        sb.append("GitHub 代理：")
        sb.append(if (c.useProxy) "开启（" + c.proxyUrl + "）" else "关闭").append("\n")
        sb.append("终端编码：").append(c.terminalEncoding).append("\n")
        val displayName = c.displayName
        if (!displayName.isNullOrBlank()) sb.append("用户称谓：").append(displayName).append("\n")
        sb.append("问候语模式：").append(c.greetingMode).append("\n")
        sb.append("本地指令会话模式：").append(c.toolCommandSession).append("\n")
        sb.append("当前包管理器：").append(ctx.manager)
        return ToolResult(sb.toString().trim())
    }

    private fun offlineItems(): ToolResult {
        val items = loadOfflineItems()
        if (items.isEmpty()) return ToolResult("本地列表为空。")
        val sb = StringBuilder()
        sb.append("共 ").append(items.size).append(" 个本地条目：\n")
        for (item in items) {
            sb.append("- [").append(item.id).append("] ")
            sb.append(if (item.type == MOfflineEntryType.PATH) "可执行文件" else "指令")
            sb.append("：").append(item.value).append('\n')
        }
        return ToolResult(sb.toString().trim())
    }

    // ---------- 写操作（确认后执行） ----------

    private fun changePackage(call: ToolCall, ctx: ToolContext, action: String): ToolResult {
        if (ctx.manager == PackageManagerType.UNKNOWN) return ToolResult("未能识别当前系统的包管理器。", isError = true)
        val name = call.stringArg("name")
        if (name.isNullOrBlank()) return ToolResult("缺少参数 name。", isError = true)
        val command = when (action) {
            "install" -> PackageManagerUtils.getInstallCommand(ctx.manager, name)
            "update" -> PackageManagerUtils.getPackageUpdateCommand(ctx.manager, name)
            else -> PackageManagerUtils.getUninstallCommand(ctx.manager, name)
        } ?: return ToolResult("当前包管理器不支持该操作。", isError = true)
        val verb = when (action) {
            "install" -> "安装"
            "update" -> "更新"
            else -> "卸载"
        }
        TerminalSessionManager.executeCommandAndWait(command)
        return ToolResult("已在终端执行" + verb + "命令：" + command + "\n请提示用户在终端查看执行结果；稍后可调用 get_installed_detail 复核状态。")
    }

    private fun runOfflineItem(call: ToolCall): ToolResult {
        val id = call.stringArg("id")
        if (id.isNullOrBlank()) return ToolResult("缺少参数 id。", isError = true)
        val item = loadOfflineItems().firstOrNull { it.id == id }
            ?: return ToolResult("未找到 id 为 " + id + " 的本地条目。", isError = true)
        if (item.type == MOfflineEntryType.PATH) {
            val workingDir = File(item.value).parent ?: ""
            TerminalSessionManager.executeCommandAndWait("\"" + item.value + "\"", workingDir)
        } else {
            TerminalSessionManager.executeCommandAndWait(item.value)
        }
        return ToolResult("已执行本地条目：" + item.value + "\n请在终端查看执行结果。")
    }

    private fun runCommand(call: ToolCall): ToolResult {
        val command = call.stringArg("command")
        if (command.isNullOrBlank()) return ToolResult("缺少参数 command。", isError = true)
        TerminalSessionManager.executeCommandAndWait(command)
        return ToolResult("已在终端执行命令：" + command + "\n请在终端查看输出。")
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)
}