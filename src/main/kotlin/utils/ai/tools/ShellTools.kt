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
import utils.ai.ToolCall
import utils.ai.ToolDanger
import utils.ai.ToolDef
import utils.ai.ToolEnv
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
                canContinue = true,
                params = listOf(
                    ToolParamSpec("command", "string", "要执行的完整命令", required = true)
                ),
                danger = ToolDanger.SHELL
            ),
            handler = { call, env -> runCommand(call, env) }
        )
    )

    /**
     * 执行命令并等它真正结束（`executeCommandAndWait` 只是投递，见其实现注释），
     * 把退出码与输出尾部如实回报给模型；用户点「在执行时继续」时回报「未完成」。
     */
    private suspend fun runCommand(call: ToolCall, env: ToolEnv): ToolResult {
        val command = call.stringArg("command")
        if (command.isNullOrBlank()) return ToolResult("缺少参数 command。", isError = true)
        val sessionId = TerminalSessionManager.executeCommandAndWait(command)
        val outcome = TerminalSessionManager.awaitCommandOutcome(
            sessionId = sessionId,
            onProgress = { tail -> env.onProgress?.invoke(tail) },
            continueRequested = { env.continueRequested?.invoke() == true }
        )
        val tailNote = if (outcome.outputTail.isBlank()) "" else "\n输出尾部：\n" + outcome.outputTail
        return when {
            !outcome.finished -> ToolResult("【未完成】命令仍在终端运行（用户选择继续），退出码未知。请勿报告成功。" + tailNote)
            outcome.cancelled -> ToolResult("命令已被用户中断。" + tailNote, isError = true)
            outcome.exitCode == 0 -> ToolResult("命令完成（退出码 0）。" + tailNote)
            else -> ToolResult("命令失败（退出码 " + (outcome.exitCode ?: "未知") + "）。" + tailNote, isError = true)
        }
    }
}
