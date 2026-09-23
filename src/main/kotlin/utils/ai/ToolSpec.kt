// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 工具定义与运行环境：ToolDef 把「给模型看的清单」与「实际实现」绑在一处，避免两处漂移

package utils.ai

import utils.PackageManagerType

/** 工具危险级别 */
enum class ToolDanger {
    /** 只读查询 */
    READ,

    /** 会修改系统（安装 / 更新 / 卸载 / 执行本地条目 / 执行命令），调用前需用户确认 */
    WRITE,

    /** 在终端执行任意命令（默认全关，需用户在设置里显式开启） */
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
    /** 执行期间的简短动作说明（卡片副标题），例如「读取已安装列表」；留空则只显示工具名 */
    val runningHint: String = "",

    /** 首次执行可能明显较慢（需要扫描本机软件包等），卡片会额外提示 */
    val slowFirstRun: Boolean = false,
    val params: List<ToolParamSpec> = emptyList(),
    val danger: ToolDanger = ToolDanger.READ
)

/** 工具定义 + 实现 */
data class ToolDef(
    val spec: ToolSpec,
    val handler: suspend (ToolCall, ToolEnv) -> ToolResult
)

/**
 * 工具执行环境：告诉工具「当前应用处于什么状态」。
 *
 * @param confirm 写操作确认回调；返回 false 表示用户拒绝。为 null 时直接执行（不经过界面）。
 */
data class ToolEnv(
    val manager: PackageManagerType,
    val useProxy: Boolean = false,
    val proxyUrl: String = "",
    val isDebug: Boolean = true,
    val confirm: (suspend (ToolCall) -> Boolean)? = null
)

/** 生成给模型看的工具说明（追加到系统提示之后） */
object ToolPrompt {
    fun build(specs: List<ToolSpec>): String {
        if (specs.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("你可以调用本应用的内置工具来获取实时信息或执行操作。\n\n")
        sb.append("调用格式（必须独占一行，行首是标记，标记后紧跟单行 JSON）：\n")
        sb.append(TOOL_CALL_MARKER).append("{\"tool\":\"工具名\",\"arguments\":{\"参数名\":\"值\"}}\n\n")
        sb.append("规则：\n")
        sb.append("1. 可以连续输出多个调用行，它们会被依次执行。\n")
        sb.append("2. 调用行必须完整写在同一行内：不要换行、不要包进 Markdown 代码块、不要在行内写解释。\n")
        sb.append("3. 执行结果会以 [TOOL_RESULT] 开头回传给你，请据此继续回答或再次调用工具。\n")
        sb.append("4. 不要编造工具结果；信息不足时先调用工具，而不是猜测。\n")
        sb.append("5. 需要修改系统或执行命令的工具会先弹确认框，用户拒绝时请停止重试并说明原因。\n\n")
        sb.append("可用工具：\n")
        for (spec in specs) {
            sb.append("- ").append(spec.name)
            if (spec.params.isNotEmpty()) {
                sb.append('(')
                sb.append(spec.params.joinToString(", ") { p ->
                    p.name + ":" + p.type + if (p.required) "必填" else "可选"
                })
                sb.append(')')
            }
            sb.append(" — ").append(spec.description)
            if (spec.danger != ToolDanger.READ) sb.append("【需用户确认】")
            sb.append('\n')
        }
        return sb.toString()
    }
}
