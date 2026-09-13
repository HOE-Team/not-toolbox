// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils.ai

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import utils.PackageManagerType

/** 工具危险级别 */
enum class ToolDanger {
    /** 只读查询 */
    READ,

    /** 会修改系统（安装 / 更新 / 卸载 / 执行本地条目），调用前需用户确认 */
    WRITE,

    /** 在终端执行任意命令（默认关闭） */
    SHELL
}

/** 工具参数定义 */
data class ToolParamSpec(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = false
)

/** 工具定义 */
data class ToolSpec(
    val name: String,
    val description: String,
    val params: List<ToolParamSpec> = emptyList(),
    val danger: ToolDanger = ToolDanger.READ
) {
    /** 生成给模型看的 JSON Schema 文本 */
    fun schemaJson(): String {
        val props = params.map { p ->
            "\"" + p.name + "\":{\"type\":\"" + p.type + "\",\"description\":\"" + sanitize(p.description) + "\"}"
        }
        val required = params.filter { it.required }.map { "\"" + it.name + "\"" }
        return "{\"type\":\"object\",\"properties\":{" + props.joinToString(",") + "},\"required\":[" + required.joinToString(",") + "]}"
    }

    private fun sanitize(text: String): String {
        return text.replace("\\", " ").replace("\"", "'").replace("\n", " ")
    }
}

/**
 * 工具执行上下文：告诉工具「当前应用处于什么状态」。
 *
 * @param confirm 写操作确认回调；返回 false 表示用户拒绝。为 null 时直接执行（仅内部调用使用）。
 */
data class ToolContext(
    val manager: PackageManagerType,
    val useProxy: Boolean = false,
    val proxyUrl: String = "",
    val isDebug: Boolean = true,
    val confirm: (suspend (ToolCall) -> Boolean)? = null
)

/** 参数读取辅助 */
fun ToolCall.stringArg(name: String): String? = (arguments[name] as? JsonPrimitive)?.contentOrNull
fun ToolCall.intArg(name: String): Int? = (arguments[name] as? JsonPrimitive)?.intOrNull
fun ToolCall.boolArg(name: String): Boolean? = (arguments[name] as? JsonPrimitive)?.booleanOrNull

object ToolRegistry {
    /** 工具的归属服务名（对齐 MCP 的 server 概念，便于界面展示） */
    const val SERVER_NAME = "not-toolbox"

    /** 全部内建工具 */
    val allSpecs: List<ToolSpec> = listOf(
        ToolSpec(
            name = "list_packages",
            description = "列出软件目录中的条目（可在线获取，离线时回退到内置目录）。支持按分类或关键词过滤。",
            params = listOf(
                ToolParamSpec("category", "string", "分类名过滤，如 浏览器、开发工具；留空表示不限"),
                ToolParamSpec("query", "string", "关键词过滤（匹配名称或描述）"),
                ToolParamSpec("limit", "integer", "最多返回条数，默认 30，最大 200")
            )
        ),
        ToolSpec(
            name = "search_packages",
            description = "按关键词搜索软件目录（名称或描述匹配），返回匹配条目及其在目标平台上的原生包名。",
            params = listOf(
                ToolParamSpec("query", "string", "搜索关键词", required = true),
                ToolParamSpec("limit", "integer", "最多返回条数，默认 30，最大 200")
            )
        ),
        ToolSpec(
            name = "list_installed",
            description = "列出本机已安装的软件包（使用包管理器原生包名），可按关键词过滤。",
            params = listOf(
                ToolParamSpec("query", "string", "关键词过滤（原生包名包含该字符串）"),
                ToolParamSpec("limit", "integer", "最多返回条数，默认 50，最大 500")
            )
        ),
        ToolSpec(
            name = "get_installed_detail",
            description = "查询某个软件包是否已安装、已安装版本，以及是否有可用更新。参数可以是目录中的显示名或原生包名。",
            params = listOf(
                ToolParamSpec("name", "string", "软件名（目录显示名或原生包名）", required = true)
            )
        ),
        ToolSpec(
            name = "check_updates",
            description = "检查本机已安装软件包的可升级列表（与原「检查更新」功能一致）。",
            params = listOf(
                ToolParamSpec("limit", "integer", "最多返回条数，默认 100，最大 500")
            )
        ),
        ToolSpec(
            name = "get_system_info",
            description = "获取当前系统状态：操作系统、CPU、内存、GPU、磁盘、网络速率与流量、进程数、屏幕、电池、蓝牙。",
            params = emptyList()
        ),
        ToolSpec(
            name = "get_app_config",
            description = "获取本应用当前配置：主题、代理、终端编码、用户称谓、当前包管理器等（不含任何密钥或隐私路径）。",
            params = emptyList()
        ),
        ToolSpec(
            name = "list_offline_items",
            description = "列出「本地」页面保存的离线执行项（可执行文件路径或自定义指令）及其 id。",
            params = emptyList()
        ),
        ToolSpec(
            name = "install_package",
            description = "通过系统包管理器安装指定软件包。会修改系统，执行前需用户确认。",
            params = listOf(
                ToolParamSpec("name", "string", "原生包名（可通过 search_packages 获取）", required = true)
            ),
            danger = ToolDanger.WRITE
        ),
        ToolSpec(
            name = "update_package",
            description = "升级指定的已安装软件包。会修改系统，执行前需用户确认。",
            params = listOf(
                ToolParamSpec("name", "string", "原生包名", required = true)
            ),
            danger = ToolDanger.WRITE
        ),
        ToolSpec(
            name = "uninstall_package",
            description = "卸载指定的已安装软件包。会修改系统，执行前需用户确认。",
            params = listOf(
                ToolParamSpec("name", "string", "原生包名", required = true)
            ),
            danger = ToolDanger.WRITE
        ),
        ToolSpec(
            name = "run_offline_item",
            description = "执行「本地」页面保存的某个条目（按 id）。会修改系统，执行前需用户确认。",
            params = listOf(
                ToolParamSpec("id", "string", "条目 id（先用 list_offline_items 获取）", required = true)
            ),
            danger = ToolDanger.WRITE
        ),
        ToolSpec(
            name = "run_command",
            description = "在终端执行任意 Shell 命令。仅在用户于设置中显式开启后可用，执行前需用户确认。",
            params = listOf(
                ToolParamSpec("command", "string", "要执行的完整命令", required = true)
            ),
            danger = ToolDanger.SHELL
        )
    )

    fun specOf(name: String): ToolSpec? = allSpecs.firstOrNull { it.name == name }

    /** 依据设置过滤出本次对话可用的工具 */
    fun enabledSpecs(toolsEnabled: Boolean, disabled: Set<String>, shellEnabled: Boolean): List<ToolSpec> {
        if (!toolsEnabled) return emptyList()
        return allSpecs.filter { it.name !in disabled && (shellEnabled || it.danger != ToolDanger.SHELL) }
    }

    /** 生成工具使用提示词（追加到系统提示之后） */
    fun buildToolPrompt(specs: List<ToolSpec>): String {
        if (specs.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("你可以调用本应用的内置工具来获取实时信息或执行操作。\n")
        sb.append("需要调用工具时，必须单独占一行，格式严格如下（arguments 为 JSON 对象）：\n")
        sb.append(TOOL_CALL_MARKER).append("{\"tool\":\"工具名\",\"arguments\":{\"参数名\":\"值\"}}\n")
        sb.append("规则：\n")
        sb.append("1. 可以连续输出多个工具调用行，它们会被依次执行。\n")
        sb.append("2. 工具调用行内不要写解释、不要包在 Markdown 代码块里。\n")
        sb.append("3. 执行结果会以 [TOOL_RESULT] 开头回传给你，请据此继续回答或再次调用工具。\n")
        sb.append("4. 不要编造工具结果；信息不足时先调用工具，而不是猜测。\n")
        sb.append("5. 需要修改系统的工具会先弹出确认框，用户拒绝时请停止重试并说明原因。\n\n")
        sb.append("可用工具（格式：工具名(参数) — 说明）：\n")
        for (spec in specs) {
            sb.append("- ").append(spec.name)
            if (spec.params.isNotEmpty()) {
                sb.append('(')
                sb.append(spec.params.joinToString(", ") { param ->
                    param.name + ":" + param.type + (if (param.required) "必填" else "可选")
                })
                sb.append(')')
            }
            sb.append(" — ").append(spec.description)
            if (spec.danger != ToolDanger.READ) sb.append("【需用户确认】")
            sb.append('\n')
        }
        return sb.toString()
    }

    /** 执行一次工具调用 */
    suspend fun invoke(call: ToolCall, specs: List<ToolSpec>, ctx: ToolContext): ToolResult {
        val spec = specs.firstOrNull { it.name == call.tool }
            ?: return ToolResult("未知或已禁用的工具：" + call.tool, isError = true)
        if (spec.danger != ToolDanger.READ) {
            val confirmer = ctx.confirm
            if (confirmer != null) {
                val approved = try {
                    confirmer(call)
                } catch (_: Exception) {
                    false
                }
                if (!approved) {
                    return ToolResult("用户拒绝执行 " + call.tool + "，请勿重试，改为向用户说明原因。")
                }
            }
        }
        return try {
            BuiltinToolHandlers.execute(call, ctx)
        } catch (e: Exception) {
            ToolResult("工具执行失败：" + (e.message ?: e.javaClass.simpleName), isError = true)
        }
    }
}