// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// OpenAI 兼容协议的聊天补全客户端（Ktor CIO，支持流式 SSE）。这是与网络打交道的唯一一层。

package utils.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 发送给模型的一条消息 */
data class LlmMessage(val role: String, val content: String)

object LlmClient {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 进程内共享的 HTTP 客户端。
     * 每次请求都新建再关闭客户端会导致每轮对话（尤其多轮工具调用）重新做 DNS + TCP + TLS 握手，
     * 首字延迟被放大，观感上更像「一次性吐出」。
     */
    private val client: HttpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                // 给长回答留足时间；流式期间按 socket 空闲计时
                requestTimeoutMillis = 300_000
                socketTimeoutMillis = 300_000
            }
        }
    }

    /** 非流式补全 */
    suspend fun complete(model: AiModelConfig, apiKey: String, messages: List<LlmMessage>): Result<String> {
        return try {
            val response = client.post(model.provider.chatCompletionsUrl(model.baseUrl)) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                header("Accept", "application/json")
                setBody(buildBody(model, messages, stream = false))
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                Result.failure(IllegalStateException(describeFailure(response.status.value, text)))
            } else {
                val content = extractContent(text)
                if (content == null) {
                    Result.failure(IllegalStateException("响应中没有可用内容：" + text.take(300)))
                } else {
                    Result.success(content)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 流式补全。onDelta 收到正文增量，onReasoning 收到推理增量（如 DeepSeek 的 reasoning_content）。
     * 返回完整正文。
     *
     * 关键点：必须用 `preparePost(...).execute { }` 发起请求。`HttpClient.post/request` 会先把**整个响应体**
     * 读进内存再返回（Ktor 默认行为），此时 `bodyAsChannel()` 拿到的是内存里的完整字节流，SSE 增量会被
     * 合并成一次性返回，界面上就看不到逐字输出；只有 `execute` 里的响应体才是与连接绑定的实时通道。
     */
    suspend fun stream(
        model: AiModelConfig,
        apiKey: String,
        messages: List<LlmMessage>,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit = {}
    ): Result<String> {
        val full = StringBuilder()
        return try {
            var sawEvent = false
            client.preparePost(model.provider.chatCompletionsUrl(model.baseUrl)) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                header("Accept", "text/event-stream")
                setBody(buildBody(model, messages, stream = true))
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw IllegalStateException(describeFailure(response.status.value, response.bodyAsText()))
                }
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    // SSE 允许 ":" 开头的注释行（部分网关用作心跳）
                    if (trimmed.startsWith(":")) continue
                    if (!trimmed.startsWith("data:")) continue
                    sawEvent = true
                    val payload = trimmed.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    val delta = extractDelta(payload)
                    if (delta.reasoning.isNotEmpty()) onReasoning(delta.reasoning)
                    if (delta.content.isNotEmpty()) {
                        full.append(delta.content)
                        onDelta(delta.content)
                    }
                }
            }
            if (!sawEvent) {
                Result.failure(
                    IllegalStateException("流式响应没有返回任何数据，该网关可能不支持 stream；请在模型设置里关闭「流式输出」。")
                )
            } else if (full.isEmpty()) {
                Result.failure(IllegalStateException("流式响应没有返回任何内容"))
            } else {
                Result.success(full.toString())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------- 请求体与响应解析 ----------

    private fun buildBody(model: AiModelConfig, messages: List<LlmMessage>, stream: Boolean): String {
        val array = buildJsonArray {
            for (message in messages) {
                add(buildJsonObject {
                    put("role", message.role)
                    put("content", message.content)
                })
            }
        }
        val obj = buildJsonObject {
            put("model", model.modelName.ifBlank { "gpt-4o-mini" })
            put("messages", array)
            put("stream", stream)
            // 仅对认这个参数的网关显式声明思考开关；不认的网关（thinkingMode=AUTO）不发送。
            // 不显式声明时，部分 DeepSeek 系模型会默认开启思考，思考阶段只推 reasoning_content，
            // 正文迟迟不开始，界面上看着就像「伪流式」。
            if (model.provider.supportsThinking && model.thinkingMode != ThinkingMode.AUTO) {
                put("thinking", buildJsonObject {
                    put("type", if (model.thinkingMode == ThinkingMode.ENABLED) "enabled" else "disabled")
                })
            }
        }
        return obj.toString()
    }

    private fun describeFailure(status: Int, body: String): String {
        val apiMessage = try {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }
        if (!apiMessage.isNullOrBlank()) return "接口返回 HTTP $status：$apiMessage"
        return "接口返回 HTTP " + status + "：" + body.take(300)
    }

    /** 非流式响应里的正文（部分模型只给 reasoning_content，也一并取用） */
    private fun extractContent(body: String): String? = try {
        val root = json.parseToJsonElement(body).jsonObject
        val first = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject
        val message = first?.get("message")?.jsonObject
        val content = message?.get("content")?.jsonPrimitive?.contentOrNull
        if (!content.isNullOrBlank()) {
            content
        } else {
            message?.get("reasoning_content")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        null
    }

    /** 一个流式分块里的正文与推理内容 */
    private data class Delta(val content: String, val reasoning: String)

    private fun extractDelta(payload: String): Delta = try {
        val root = json.parseToJsonElement(payload).jsonObject
        val first = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject
        val delta = first?.get("delta")?.jsonObject
        Delta(
            content = delta?.get("content")?.jsonPrimitive?.contentOrNull ?: "",
            reasoning = delta?.get("reasoning_content")?.jsonPrimitive?.contentOrNull ?: ""
        )
    } catch (_: Exception) {
        Delta("", "")
    }
}

/**
 * 把底层异常翻译成用户能看懂的提示。
 * 全项目只在这里做一次状态码 → 文案的映射（以前 LlmClient 的 describeFailure 与 ChatAgent 各写了一份）。
 */
fun mapAiError(e: Throwable): String {
    val message = e.message ?: e.javaClass.simpleName
    val lower = message.lowercase()
    return when {
        message.contains("401") -> "认证失败（401）：请检查 API Key 是否正确。"
        message.contains("403") -> "没有访问权限（403）：请检查 API Key 的可用范围或额度。"
        message.contains("404") -> "接口不存在（404）：请检查 Base URL 与模型名称。"
        message.contains("429") -> "请求过于频繁或额度不足（429）。"
        lower.contains("timeout") || lower.contains("timed out") -> "请求超时：请检查网络后重试。"
        lower.contains("unknownhost") || lower.contains("unresolved") -> "域名解析失败：请检查网络或 Base URL。"
        lower.contains("connect") -> "连接失败：请检查网络、Base URL 或是否需要代理。"
        else -> "请求失败：" + message
    }
}

