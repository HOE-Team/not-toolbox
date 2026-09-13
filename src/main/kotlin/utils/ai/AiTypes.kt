// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// AI 数据模型（参考 OpenDroidChat 实现，去除 Android 依赖）

package utils.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * LLM 提供商。第一阶段只支持 OpenAI 兼容协议，可覆盖 OpenAI / DeepSeek / 百炼 及各类本地模型。
 */
enum class LlmProvider(val displayName: String, val defaultBaseUrl: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1"),
    DASHSCOPE("阿里云百炼 (OpenAI 兼容)", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    CUSTOM("自定义 (OpenAI 兼容)", "");

    /** 实际使用的 Base URL：填了自定义值就优先用自定义值 */
    fun resolveBaseUrl(custom: String): String {
        val trimmed = custom.trim()
        if (trimmed.isNotEmpty()) return trimmed
        return defaultBaseUrl
    }

    /** 聊天补全端点 */
    fun chatCompletionsUrl(custom: String): String {
        return resolveBaseUrl(custom).trimEnd('/') + "/chat/completions"
    }
}

/**
 * 系统状态透传的详细程度。
 */
enum class SystemContextDetail(val displayName: String) {
    OFF("不携带"),
    COMPACT("精简"),
    STANDARD("标准"),
    FULL("详细")
}

/**
 * 思考模式。
 * DeepSeek 等网关若不显式传 `thinking`，模型可能默认开启思考：
 * 思考阶段只推送 reasoning_content（正文尚未开始），看上去就像「伪流式」。
 * 参考 OpenDroidChat：对 DeepSeek 始终显式传 thinking 开关（其默认值为关闭）。
 */
enum class ThinkingMode(val displayName: String) {
    AUTO("默认（不发送该参数）"),
    ENABLED("启用思考"),
    DISABLED("关闭思考（更快、正文立即流式）")
}

/**
 * 一个模型的配置。API Key 仅以密文（apiKeyEnc）落盘，见 SecretStore。
 */
@Serializable
data class AiModelConfig(
    val id: String = "",
    val name: String = "",
    val provider: LlmProvider = LlmProvider.OPENAI,
    val modelName: String = "",
    /** 加密后的 API Key（dpapi: / keyring: / aesgcm: 前缀） */
    val apiKeyEnc: String = "",
    val baseUrl: String = "",
    val systemPrompt: String = "",
    val useStream: Boolean = true,
    /** 是否允许该模型调用工具 */
    val toolsEnabled: Boolean = true,
    /** 单独禁用的工具名 */
    val disabledTools: List<String> = emptyList(),
    /** 写操作（安装 / 更新 / 卸载）执行前是否需要用户确认 */
    val confirmWriteOps: Boolean = true,
    /** 系统状态透传级别 */
    val systemContext: SystemContextDetail = SystemContextDetail.STANDARD,
    /** 工具调用循环的最大轮数 */
    val maxToolIterations: Int = 6,
    /** 思考模式（目前仅对 DeepSeek 生效） */
    val thinkingMode: ThinkingMode = ThinkingMode.DISABLED
)

/**
 * AI 功能总配置（config/ai_config.json）。
 */
@Serializable
data class AiAppConfig(
    val models: List<AiModelConfig> = emptyList(),
    val activeModelId: String? = null,
    /** 「在终端执行任意命令」工具：默认关闭 */
    val shellToolEnabled: Boolean = false
) {
    fun activeModel(): AiModelConfig? {
        models.firstOrNull { it.id == activeModelId }?.let { return it }
        return models.firstOrNull()
    }
}

/** 聊天消息发送方 */
enum class ChatRole { USER, ASSISTANT }

/**
 * 一次工具调用（由模型输出解析而来）。
 */
data class ToolCall(
    val tool: String,
    val arguments: Map<String, JsonElement> = emptyMap()
) {
    /** 供界面展示的简要说明 */
    fun summary(): String {
        if (arguments.isEmpty()) return tool
        val parts = arguments.entries.map { entry -> entry.key + "=" + shortenArg(entry.value.toString()) }
        return tool + "(" + parts.joinToString(", ") + ")"
    }

    private fun shortenArg(raw: String): String {
        val text = raw.trim('"')
        if (text.length <= 60) return text
        return text.take(60) + "..."
    }
}

/** 工具执行结果 */
data class ToolResult(
    val text: String,
    val isError: Boolean = false
)

/**
 * 助手消息中的分段：思考块 / 工具调用块。
 *
 * 分段列表就是一条**线性时间线**：列表顺序 = 界面展示顺序，
 * 因此调用方只能按发生顺序**追加**分段（不能插队），界面按列表顺序自上而下渲染。
 *
 * 一段思考 = 一次连续的推理输出：正文开始输出或开始调用工具即结束该段，
 * 之后的推理增量会新起一段（否则多轮思考会粘成一块，时间线就错位了）。
 */
sealed class ChatSegment {
    /** 一段思考；[streaming] 为 true 表示这段还在增长（界面显示「思考中」并自动展开） */
    data class Thinking(val text: String, val streaming: Boolean = false) : ChatSegment()

    data class ToolCallSegment(
        val call: ToolCall,
        val result: ToolResult? = null,
        val durationMs: Long = 0L,
        val pending: Boolean = false
    ) : ChatSegment()
}

/** 界面上的一条消息 */
data class ChatMessage(
    val id: Long = System.nanoTime(),
    val role: ChatRole,
    val text: String = "",
    val segments: List<ChatSegment> = emptyList(),
    val streaming: Boolean = false,
    val error: String? = null
)