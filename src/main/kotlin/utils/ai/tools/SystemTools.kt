// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 系统状态 / 应用配置 / 本地条目相关工具

package utils.ai.tools

import config.MOfflineEntryType
import config.loadConfig
import config.loadOfflineItems
import utils.SystemInfoProvider
import utils.TerminalSessionManager
import utils.ai.ToolCall
import utils.ai.ToolDanger
import utils.ai.ToolDef
import utils.ai.ToolEnv
import utils.ai.ToolParamSpec
import utils.ai.ToolResult
import utils.ai.ToolSpec
import utils.ai.stringArg
import utils.systemSnapshotFlow
import java.io.File
import java.util.Locale

object SystemTools {
    val all: List<ToolDef> = listOf(
        ToolDef(
            spec = ToolSpec(
                name = "get_system_info",
                description = "获取当前系统状态：操作系统、CPU、内存、GPU、磁盘、网络速率与流量、进程数、屏幕、电池、蓝牙。",
                runningHint = "读取系统状态",
                params = emptyList()
            ),
            handler = { _, _ -> systemInfo() }
        ),
        ToolDef(
            spec = ToolSpec(
                name = "get_app_config",
                description = "获取本应用当前配置：主题、代理、终端编码、用户称谓、当前包管理器等（不含任何密钥或隐私路径）。",
                runningHint = "读取应用配置",
                params = emptyList()
            ),
            handler = { _, env -> appConfig(env) }
        ),
        ToolDef(
            spec = ToolSpec(
                name = "list_offline_items",
                description = "列出「本地」页面保存的离线执行项（可执行文件路径或自定义指令）及其 id。",
                runningHint = "读取本地条目",
                params = emptyList()
            ),
            handler = { _, _ -> offlineItems() }
        ),
        ToolDef(
            spec = ToolSpec(
                name = "run_offline_item",
                description = "执行「本地」页面保存的某个条目（按 id）。会修改系统，执行前需用户确认。",
                runningHint = "执行本地条目",
                params = listOf(
                    ToolParamSpec("id", "string", "条目 id（先用 list_offline_items 获取）", required = true)
                ),
                danger = ToolDanger.WRITE
            ),
            handler = { call, _ -> runOfflineItem(call) }
        )
    )

    // ---------- 实现 ----------

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

    private fun appConfig(env: ToolEnv): ToolResult {
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
        sb.append("当前包管理器：").append(env.manager)
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

    private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)
}
