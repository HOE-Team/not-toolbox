// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// AI 配置模型：只放「会被落盘 / 会被界面编辑」的数据；会话与工具模型见 ChatModels.kt / ToolSpec.kt

package utils.ai

import kotlinx.serialization.Serializable

/**
 * LLM 提供商预设。第一阶段只支持 OpenAI 兼容协议，可覆盖 OpenAI / DeepSeek / 百炼 及各类本地模型。
 *
 * @param supportsThinking 该网关是否认 `thinking` 参数（不认就必须留 AUTO，否则请求可能直接报错）
 */
enum class LlmProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val supportsThinking: Boolean = false
) {
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", supportsThinking = true),
    DASHSCOPE("阿里云百炼 (OpenAI 兼容)", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    CUSTOM("自定义 (OpenAI 兼容)", "");

    /** 实际使用的 Base URL：填了自定义值就优先用自定义值 */
    fun resolveBaseUrl(custom: String): String {
        val trimmed = custom.trim()
        return if (trimmed.isNotEmpty()) trimmed else defaultBaseUrl
    }

    /** 聊天补全端点 */
    fun chatCompletionsUrl(custom: String): String =
        resolveBaseUrl(custom).trimEnd('/') + "/chat/completions"
}

/**
 * 思考模式。
 *
 * DeepSeek 等网关若不显式传 `thinking`，模型可能默认开启思考：思考阶段只推送
 * reasoning_content（正文尚未开始），看上去就像「伪流式」。
 */
enum class ThinkingMode(val displayName: String) {
    AUTO("默认（不发送该参数）"),
    ENABLED("启用思考"),
    DISABLED("关闭思考（更快）")
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
    /** 加密后的 API Key（dpapi: / aesgcm: 前缀） */
    val apiKeyEnc: String = "",
    val baseUrl: String = "",
    val systemPrompt: String = "",
    val useStream: Boolean = true,
    /** 是否允许该模型调用工具 */
    val toolsEnabled: Boolean = true,
    /** 单独禁用的工具名 */
    val disabledTools: List<String> = emptyList(),
    /** 写操作（安装 / 更新 / 卸载 / 执行命令）执行前是否需要用户确认 */
    val confirmWriteOps: Boolean = true,
    /** 思考模式 */
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

    /** 用更新后的模型替换旧值（按 id） */
    fun withModel(updated: AiModelConfig): AiAppConfig =
        copy(models = models.map { if (it.id == updated.id) updated else it })

    /** 删除模型，并保证 activeModelId 始终指向存在的模型 */
    fun withoutModel(id: String): AiAppConfig {
        val rest = models.filterNot { it.id == id }
        val nextActive = activeModelId?.takeIf { it != id } ?: rest.firstOrNull()?.id
        return copy(models = rest, activeModelId = nextActive)
    }

    fun addModel(model: AiModelConfig): AiAppConfig =
        copy(models = models + model, activeModelId = activeModelId ?: model.id)
}
