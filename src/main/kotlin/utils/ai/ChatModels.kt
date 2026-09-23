// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 会话模型：消息、时间线分段、工具调用与其结果

package utils.ai

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** 聊天消息发送方 */
enum class ChatRole { USER, ASSISTANT }

/**
 * 一次工具调用（由模型输出解析而来）。
 */
@Immutable
data class ToolCall(
    val tool: String,
    val arguments: Map<String, JsonElement> = emptyMap()
) {
    /** 供界面/确认框展示的简要说明 */
    fun summary(): String {
        if (arguments.isEmpty()) return tool
        val parts = arguments.entries.map { entry -> entry.key + "=" + shorten(entry.value.toString()) }
        return tool + "(" + parts.joinToString(", ") + ")"
    }

    private fun shorten(raw: String): String {
        val text = raw.trim('"')
        return if (text.length <= 60) text else text.take(60) + "..."
    }
}

/** 从调用参数里读取一个字符串（缺失或类型不符返回 null） */
fun ToolCall.stringArg(name: String): String? = (arguments[name] as? JsonPrimitive)?.contentOrNull

/** 从调用参数里读取一个整数 */
fun ToolCall.intArg(name: String): Int? = (arguments[name] as? JsonPrimitive)?.intOrNull

/** 从调用参数里读取一个布尔值 */
fun ToolCall.boolArg(name: String): Boolean? = (arguments[name] as? JsonPrimitive)?.booleanOrNull

/** 工具执行结果 */
@Immutable
data class ToolResult(
    val text: String,
    val isError: Boolean = false
)

/**
 * 助手消息中的分段：思考块 / 工具调用块。
 *
 * 分段列表是一条**线性时间线**：列表顺序 = 界面展示顺序。分段只按发生顺序追加，
 * 界面按列表顺序自上而下渲染。
 *
 * 两处约定：
 * - 一段思考 = 一次连续的推理输出：正文开始输出或开始调用工具即封口（`streaming = false`），
 *   之后的推理增量会新起一段，否则多轮思考会粘成一块。
 * - 工具容器由 [ChatSession] 分配 [ToolCallSegment.id]，界面按 id 就地更新；
 *   **不要**按工具名匹配（模型可能连续调用同名工具）。
 */
/**
 * 工具卡片的阶段。
 *
 * 以前只有一个 `pending` 布尔量，于是「排队等前序调用」和「等待用户确认」都被显示成了「执行中」——
 * 界面在骗人。现在阶段显式化，界面按阶段给出对应文案。
 */
enum class ToolPhase(val isPending: Boolean) {
    /** 生成参数（调用的 JSON 还没闭口） */
    AWAITING_ARGS(true),

    /** 已就绪、排在同轮其它调用之后等待执行 */
    QUEUED(true),

    /** 等待用户授权（写操作 / 终端命令） */
    AWAITING_CONFIRM(true),

    /** 正在执行 */
    RUNNING(true),

    /** 执行完成 */
    DONE(false),

    /** 用户拒绝执行 */
    REJECTED(false),

    /** 执行失败或被判定为格式错误 */
    FAILED(false)
}

/** 工具名尚未识别时的占位徽标 */
const val TOOL_PLACEHOLDER = "工具调用"

@Immutable
sealed class ChatSegment {
    /** 一段思考；[streaming] 为 true 表示这段还在增长（界面显示「思考中」并自动展开） */
    @Immutable
    data class Thinking(val text: String, val streaming: Boolean = false) : ChatSegment()

    /**
     * 一次工具调用。卡片由 [ChatSession] 分配 [id]，界面按 id 就地更新。
     *
     * @param phase 卡片阶段（决定界面文案与是否转圈）
     * @param startedAtMs 执行开始时刻（0 = 还没开始），界面据此显示已耗时
     */
    @Immutable
    data class ToolCallSegment(
        val id: Long,
        val call: ToolCall,
        val phase: ToolPhase = ToolPhase.AWAITING_ARGS,
        val startedAtMs: Long = 0L,
        val result: ToolResult? = null,
        val durationMs: Long = 0L
    ) : ChatSegment() {
        val pending: Boolean get() = phase.isPending
    }
}

/** 界面上的一条消息 */
@Immutable
data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String = "",
    val segments: List<ChatSegment> = emptyList(),
    val streaming: Boolean = false,
    val error: String? = null
)
