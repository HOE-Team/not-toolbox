// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 终端命令工具（默认关闭，需用户在设置里显式开启总开关）

package utils.ai.tools

import utils.TerminalSessionManager
import utils.ai.ToolDanger
import utils.ai.ToolDef
import utils.ai.ToolParamSpec
import utils.ai.ToolResult
import utils.ai.ToolSpec
import utils.ai.stringArg

object ShellTools {
    val all: List<ToolDef> = listOf(
        ToolDef(
            spec = ToolSpec(
                name = "run_command",
                description = "在终端执行任意 Shell 命令。仅在用户于设置中显式开启后可用，执行前需用户确认。",
                runningHint = "执行终端命令",
                params = listOf(
                    ToolParamSpec("command", "string", "要执行的完整命令", required = true)
                ),
                danger = ToolDanger.SHELL
            ),
            handler = { call, _ -> runCommand(call) }
        )
    )

    private fun runCommand(call: utils.ai.ToolCall): ToolResult {
        val command = call.stringArg("command")
        if (command.isNullOrBlank()) return ToolResult("缺少参数 command。", isError = true)
        TerminalSessionManager.executeCommandAndWait(command)
        return ToolResult("已在终端执行命令：" + command + "\n请在终端查看输出。")
    }
}
