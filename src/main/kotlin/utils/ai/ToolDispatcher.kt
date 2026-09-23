// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 工具分发：清单、按设置过滤、危险级别与确认判定都只在这一处

package utils.ai

import utils.ai.tools.PackageTools
import utils.ai.tools.ShellTools
import utils.ai.tools.SystemTools

object ToolDispatcher {
    /** 全部内建工具：清单与实现绑在一处（[ToolDef]），不存在「清单写了但没实现」的漂移 */
    private val defs: List<ToolDef> = PackageTools.all + SystemTools.all + ShellTools.all

    /** 供界面展示的工具清单 */
    val allSpecs: List<ToolSpec> = defs.map { it.spec }

    fun specOf(name: String): ToolSpec? = allSpecs.firstOrNull { it.name == name }

    /** 依据设置过滤出本次对话可用的工具（Shell 类工具还要看全局总开关） */
    fun enabledSpecs(toolsEnabled: Boolean, disabled: Set<String>, shellEnabled: Boolean): List<ToolSpec> {
        if (!toolsEnabled) return emptyList()
        return allSpecs.filter { it.name !in disabled && (shellEnabled || it.danger != ToolDanger.SHELL) }
    }

    /**
     * 执行一次工具调用。
     *
     * 这里**唯一**负责「是否可以调」：不在 [specs] 里的工具一律拒绝；
     * 危险级别非 READ 的工具在真正执行前先走 [ToolEnv.confirm]。
     */
    suspend fun invoke(call: ToolCall, specs: List<ToolSpec>, env: ToolEnv): ToolResult {
        val def = defs.firstOrNull { it.spec.name == call.tool && specs.any { s -> s.name == call.tool } }
            ?: return ToolResult("未知或已禁用的工具：" + call.tool, isError = true)
        if (def.spec.danger != ToolDanger.READ) {
            val confirmer = env.confirm
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
            def.handler(call, env)
        } catch (e: Exception) {
            ToolResult("工具执行失败：" + (e.message ?: e.javaClass.simpleName), isError = true)
        }
    }
}
