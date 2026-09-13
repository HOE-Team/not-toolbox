// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// OpenAI 兼容协议的聊天补全客户端（Ktor CIO，支持流式 SSE）

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
import io.ktor.utils.io.readUTF8Line
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

/** 流式增量：正文与推理内容 */
private data class LlmDelta(val content: String, val reasoning: String)

object LlmApiService {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 进程内共享的 HTTP 客户端。
     * 之前每次请求都新建再关闭客户端，导致每轮对话（尤其多轮工具调用）都要重新做
     * DNS + TCP + TLS 握手，首字延迟被放大，观感上更像「一次性吐出」。
     */
    private val client: HttpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                // 与 OpenDroidChat 一致：给长回答留足时间，流式期间按 socket 空闲计时
                requestTimeoutMillis = 300_000
                socketTimeoutMillis = 300_000
            }
        }
    }

    private fun buildBody(model: AiModelConfig, messages: List<LlmMessage>, stream: Boolean): String {
        val array = buildJsonArray {
            for ((role, content1) in messages) {
                add(buildJsonObject {
                    put("role", role)
                    put("content", content1)
                })
            }
        }
        val obj = buildJsonObject {
            put("model", model.modelName.ifBlank { "gpt-4o-mini" })
            put("messages", array)
            put("stream", stream)
            // DeepSeek 系必须显式传 thinking，否则模型可能默认开启思考：
            // 思考阶段只推送 reasoning_content（正文还没开始），界面看起来就像「伪流式」。
            if (model.provider == LlmProvider.DEEPSEEK && model.thinkingMode != ThinkingMode.AUTO) {
                put("thinking", buildJsonObject {
                    put("type", if (model.thinkingMode == ThinkingMode.ENABLED) "enabled" else "disabled")
                })
            }
            if (stream) {
                // 让服务端在最后一个分块里回传用量信息（部分网关支持，不支持则被忽略）
                put("stream_options", buildJsonObject { put("include_usage", false) })
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
                    Result.failure(IllegalStateException("响应中没有可用内容： " + text.take(300)))
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
     * 关键点（参考 OpenDroidChat 的实现）：
     * 必须用 `preparePost(...).execute { }` 来发起请求。`HttpClient.post/request` 会先把**整个响应体**
     * 读进内存再返回（Ktor 的默认行为），此时 `bodyAsChannel()` 拿到的是内存里的完整字节流，
     * SSE 增量会被合并成一次性返回，界面上就看不到逐字输出；只有 `execute` 里的响应体
     * 才是与连接绑定的实时通道。
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
                // 少数网关在 stream=true 时仍返回整段 JSON，这里做个回落用缓冲
                val rawFallback = StringBuilder()
                var sawEvent = false
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    // SSE 允许 ":" 开头的注释行
                    if (trimmed.startsWith(":")) continue
                    if (!trimmed.startsWith("data:")) {
                        if (!sawEvent) rawFallback.append(line)
                        continue
                    }
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
                if (!sawEvent && full.isEmpty()) {
                    val fallback = extractContent(rawFallback.toString())
                    if (!fallback.isNullOrEmpty()) {
                        full.append(fallback)
                        onDelta(fallback)
                    }
                }
            }
            if (full.isEmpty()) {
                Result.failure(IllegalStateException("流式响应没有返回任何内容"))
            } else {
                Result.success(full.toString())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractContent(body: String): String? {
        val root = json.parseToJsonElement(body).jsonObject
        val choices = root["choices"] as? JsonArray ?: return null
        val first = choices.firstOrNull()?.jsonObject ?: return null
        val message = first["message"]?.jsonObject ?: return null
        val content = message["content"]?.jsonPrimitive?.contentOrNull
        if (!content.isNullOrBlank()) return content
        val reasoning = message["reasoning_content"]?.jsonPrimitive?.contentOrNull
        if (!reasoning.isNullOrBlank()) return reasoning
        return null
    }

    private fun extractDelta(payload: String): LlmDelta {
        return try {
            val root = json.parseToJsonElement(payload).jsonObject
            val choices = root["choices"] as? JsonArray ?: return LlmDelta("", "")
            val first = choices.firstOrNull()?.jsonObject ?: return LlmDelta("", "")
            val delta = first["delta"]?.jsonObject ?: return LlmDelta("", "")
            val content = delta["content"]?.jsonPrimitive?.contentOrNull ?: ""
            val reasoning = delta["reasoning_content"]?.jsonPrimitive?.contentOrNull ?: ""
            LlmDelta(content, reasoning)
        } catch (_: Exception) {
            LlmDelta("", "")
        }
    }
}