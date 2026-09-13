// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 一次助手回答的时间线：按发生顺序累积思考段 / 工具调用段 / 正文

package utils.ai

/**
 * 助手回答的时间线构建器。
 *
 * 分段只能**按发生顺序追加**（思考 → 工具 → 思考 → 工具 → …），界面按列表顺序自上而下渲染，
 * 所以这里刻意不提供任何「插入 / 重排」入口：顺序错了界面就会错。
 *
 * 两处容易搞错的地方单独说明：
 * - 推理增量续写的是**最后一段、且还没封口**的思考段；一段思考结束后（正文开始或开始调用工具），
 *   之后的推理增量会新起一段，否则多轮思考会粘成一块，时间线里就只剩一张卡片。
 * - 工具容器在参数还没拿全时就要先建出来（转圈），所以 [toolStarted] 先建容器，
 *   [toolCallUpdated] / [toolFinished] 只负责补齐同一个容器。
 *
 * 逻辑与 Compose 无关，便于用探针脚本直接验证分段与顺序。
 */
class ChatTimeline {
    private val items = mutableListOf<ChatSegment>()

    /** 当前时间线（界面渲染用） */
    val segments: List<ChatSegment>
        get() = items.toList()

    /** 追加一段思考增量：最后一段思考还没封口就续写，已经封口则新起一段 */
    fun thinkingDelta(delta: String) {
        val last = items.lastOrNull()
        if (last is ChatSegment.Thinking && last.streaming) {
            items[items.size - 1] = last.copy(text = last.text + delta)
        } else {
            items.add(ChatSegment.Thinking(text = delta, streaming = true))
        }
    }

    /** 封口当前思考段：正文开始输出或开始调用工具，都表示这一段思考到此为止 */
    fun sealThinking() {
        val index = items.indexOfLast { it is ChatSegment.Thinking && it.streaming }
        if (index < 0) return
        val current = items[index] as ChatSegment.Thinking
        items[index] = current.copy(streaming = false)
    }

    /** 开始一次工具调用：先建出容器（参数可能是稍后才补齐的），并顺手结束当前思考段 */
    fun toolStarted(call: ToolCall) {
        sealThinking()
        items.add(ChatSegment.ToolCallSegment(call = call, pending = true))
    }

    /** 流式期间先建好的容器补齐完整参数（复用同一个容器，不新起一张卡片） */
    fun toolCallUpdated(call: ToolCall) {
        val index = items.indexOfLast {
            it is ChatSegment.ToolCallSegment && it.pending && it.call.tool == call.tool
        }
        if (index < 0) return
        val existing = items[index] as ChatSegment.ToolCallSegment
        items[index] = existing.copy(call = call)
    }

    /** 工具执行完成：结果填进对应的容器 */
    fun toolFinished(call: ToolCall, result: ToolResult, durationMs: Long) {
        val finished = ChatSegment.ToolCallSegment(
            call = call,
            result = result,
            durationMs = durationMs,
            pending = false
        )
        val index = items.indexOfFirst {
            it is ChatSegment.ToolCallSegment && it.pending && it.call.tool == call.tool
        }
        if (index >= 0) items[index] = finished else items.add(finished)
    }
}
