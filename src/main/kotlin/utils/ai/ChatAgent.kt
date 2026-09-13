// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 对话编排：系统提示 + 系统状态透传 + 工具调用循环

package utils.ai

import config.loadConfig
import config.loadOfflineItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import utils.PackageDetector
import utils.PackageManagerType
import utils.SystemInfoProvider
import utils.UpdateCheckStatus
import utils.UpdateChecker
import utils.systemSnapshotFlow
import java.util.Locale

/** 对话过程中推送给界面的事件 */
sealed class ChatEvent {
    /** 当前完整可见正文（替换语义，已剔除工具调用标记） */
    data class AssistantText(val text: String) : ChatEvent()

    /** 推理内容增量（部分模型提供） */
    data class ReasoningDelta(val text: String) : ChatEvent()

    /** 工具开始执行 */
    data class ToolStarted(val call: ToolCall) : ChatEvent()

    /** 已创建容器的调用补齐了参数（流式期间先建容器、后拿到完整参数） */
    data class ToolCallUpdated(val call: ToolCall) : ChatEvent()

    /** 工具执行完成 */
    data class ToolFinished(
        val call: ToolCall,
        val result: ToolResult,
        val durationMs: Long
    ) : ChatEvent()

    /** 出错 */
    data class Failed(val message: String) : ChatEvent()

    /** 本轮结束；notice 非空表示有需要提示用户的情况 */
    data class Finished(val notice: String?) : ChatEvent()
}

/**
 * 系统状态透传文本生成器。
 * 只输出排查问题必需的字段，绝不包含密钥、代理凭据、MAC 地址、背景图路径或终端内容。
 */
object SystemContextBuilder {
    suspend fun build(detail: SystemContextDetail, manager: PackageManagerType): String {
        if (detail == SystemContextDetail.OFF) return ""
        return try {
            val snap = systemSnapshotFlow.value ?: SystemInfoProvider.collectSnapshot(waitForInitialData = false)
            val ov = snap.overview
            val si = snap.systemInfo
            val net = snap.networkIO
            val sb = StringBuilder()
            sb.append("- 操作系统：").append(ov.platform).append(' ').append(ov.osVersion)
            sb.append("（").append(ov.architecture).append("）\n")
            sb.append("- 计算机名：").append(ov.computerName).append('\n')
            sb.append("- CPU：").append(si.cpu.model).append("，当前占用 ").append(fmt(si.cpu.usage)).append("%\n")
            sb.append("- 内存：").append(fmt(si.ram.used)).append("/").append(fmt(si.ram.total))
            sb.append(" GB（").append(fmt(si.ram.usage)).append("%）\n")
            if (detail == SystemContextDetail.COMPACT) {
                val mainDisk = si.disks.maxByOrNull { it.totalGB }
                if (mainDisk != null) {
                    sb.append("- 主磁盘 ").append(mainDisk.name).append('：')
                    sb.append(fmt(mainDisk.usedGB)).append("/").append(fmt(mainDisk.totalGB)).append(" GB\n")
                }
                return sb.toString().trim()
            }
            if (si.gpus.isNotEmpty()) {
                sb.append("- GPU：").append(si.gpus.joinToString("、") { it.model }).append('\n')
            }
            if (detail == SystemContextDetail.STANDARD) {
                val disks = si.disks.joinToString("、") { d -> d.name + " " + fmt(d.usage) + "%" }
                if (disks.isNotEmpty()) sb.append("- 磁盘占用：").append(disks).append('\n')
            } else {
                for (d in si.disks) {
                    sb.append("- 磁盘 ").append(d.name).append('：').append(fmt(d.usedGB)).append("/")
                    sb.append(fmt(d.totalGB)).append(" GB（").append(fmt(d.usage)).append("%）\n")
                }
            }
            sb.append("- 网络：↓ ").append(fmt(net.downKBps)).append(" KB/s，↑ ").append(fmt(net.upKBps))
            sb.append(" KB/s，类型 ").append(net.connectionType.name).append('\n')
            if (!net.ssid.isNullOrBlank()) sb.append("- WiFi：").append(net.ssid).append('\n')
            if (!net.ipv4.isNullOrBlank()) sb.append("- 本机 IPv4：").append(net.ipv4).append('\n')
            sb.append("- 进程数：").append(snap.services.processCount)
            sb.append("，登录用户：").append(snap.services.loggedInUsers).append('\n')
            sb.append("- 屏幕：").append(snap.screen.resolution)
            sb.append("，缩放 ").append(snap.screen.scalePercent).append("%\n")
            if (snap.battery.hasBattery) {
                sb.append("- 电池：").append(fmt(snap.battery.capacityPercent)).append("%")
                sb.append(if (snap.battery.isCharging) "（充电中）" else "")
                sb.append("，健康：").append(snap.battery.healthStatus).append('\n')
            }
            sb.append("- 蓝牙：")
            sb.append(if (snap.bluetooth.hasAdapter) snap.bluetooth.adapterModel else "无适配器")
            sb.append('\n')
            sb.append("- 当前包管理器：").append(manager)
            val installed = installedCount(manager)
            if (installed >= 0) sb.append("，已安装 ").append(installed).append(" 个软件包")
            sb.append('\n')
            if (detail == SystemContextDetail.FULL) {
                val updateState = UpdateChecker.state.value
                if (updateState.manager == manager && updateState.status == UpdateCheckStatus.READY && updateState.supported) {
                    sb.append("- 可升级软件包数量：").append(updateState.updatable.size).append('\n')
                }
                sb.append("- 本地（离线）条目数量：").append(loadOfflineItems().size).append('\n')
                val c = loadConfig()
                sb.append("- 应用配置：主题=").append(if (c.dark) "深色" else "浅色")
                sb.append("，GitHub 代理=").append(if (c.useProxy) "开启" else "关闭")
                sb.append("，终端编码=").append(c.terminalEncoding)
                sb.append("，问候语模式=").append(c.greetingMode).append('\n')
            }
            sb.toString().trim()
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun installedCount(manager: PackageManagerType): Int {
        if (manager == PackageManagerType.UNKNOWN) return -1
        return try {
            PackageDetector.detectIfNeeded(manager)
            PackageDetector.state.value.snapshot?.packages?.size ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)
}
/**
 * 一次对话会话的编排器。
 *
 * 工作方式（不依赖厂商的 function calling）：
 * 1. 系统提示中写清工具清单与 JSON Schema，要求模型在需要时单独输出一行 [TOOL_CALL]{...}
 * 2. 流式输出时用 [ToolCodec.splitForStreaming] 暂存尾部，保证标记不会泄漏到界面
 * 3. 一轮回答结束后解析出所有工具调用，依次执行并把结果以 [TOOL_RESULT] 回填给模型
 * 4. 最多循环 [AiModelConfig.maxToolIterations] 轮，避免无限调用
 */
class ChatAgent(
    private val model: AiModelConfig,
    private val apiKey: String,
    private val specs: List<ToolSpec>,
    private val toolContext: ToolContext,
    private val systemContext: String
) {
    private val history = mutableListOf<LlmMessage>()
    private val turnMutex = Mutex()
    private val systemPrompt: String = buildSystemPrompt()

    /** 是否已具备调用条件（模型名 + API Key） */
    val isConfigured: Boolean get() = model.modelName.isNotBlank() && apiKey.isNotBlank()

    /** 清空会话历史（切换模型或开始新对话时调用） */
    fun reset() {
        history.clear()
    }

    /** 发送一条用户消息，返回事件流 */
    fun send(userText: String): Flow<ChatEvent> = callbackFlow {
        val job = launch {
            runTurn(userText) { event -> trySend(event) }
            close()
        }
        awaitClose { job.cancel() }
    }

    private fun buildSystemPrompt(): String {
        val sb = StringBuilder()
        val base = model.systemPrompt.trim()
        if (base.isNotEmpty()) sb.append(base) else sb.append(DEFAULT_SYSTEM_PROMPT)
        if (systemContext.isNotBlank()) {
            sb.append("\n\n当前系统状态（本机实时采集，仅供参考）：\n").append(systemContext)
        }
        val toolPrompt = ToolRegistry.buildToolPrompt(specs)
        if (toolPrompt.isNotBlank()) sb.append("\n\n").append(toolPrompt)
        return sb.toString()
    }

    private fun buildMessages(): List<LlmMessage> {
        val list = ArrayList<LlmMessage>(history.size + 1)
        list += LlmMessage("system", systemPrompt)
        list += history
        return list
    }

    private suspend fun runTurn(userText: String, emit: (ChatEvent) -> Unit) = withContext(Dispatchers.IO) {
        turnMutex.withLock {
            if (!isConfigured) {
                emit(ChatEvent.Failed(if (apiKey.isBlank()) MSG_NO_KEY else MSG_NO_MODEL))
                emit(ChatEvent.Finished(null))
                return@withLock
            }
            history += LlmMessage("user", userText)
            val maxIterations = model.maxToolIterations.coerceIn(1, 20)
            var iteration = 0
            var answered = false
            while (iteration < maxIterations) {
                iteration++
                val raw = StringBuilder()
                var lastVisibleLength = 0
                // 本轮已在流式期间提前创建过容器的工具名（按出现顺序），用于避免重复建容器
                val announcedNames = ArrayDeque<String>()
                var announcedCount = 0
                val onDelta: (String) -> Unit = { delta ->
                    raw.append(delta)
                    val text = raw.toString()
                    // 模型刚开始写调用时（哪怕 JSON 还没闭合）就把容器建出来并转圈
                    val names: List<String> = if (specs.isEmpty()) emptyList() else ToolCodec.pendingCallNames(text)
                    while (announcedCount < names.size) {
                        val name = names[announcedCount]
                        announcedCount++
                        announcedNames.addLast(name)
                        emit(ChatEvent.ToolStarted(ToolCall(tool = name)))
                    }
                    val safe = ToolCodec.splitForStreaming(text).first
                    val visible = ToolCodec.stripCalls(safe)
                    if (visible.length != lastVisibleLength) {
                        lastVisibleLength = visible.length
                        emit(ChatEvent.AssistantText(visible))
                    }
                }
                val onReasoning: (String) -> Unit = { text -> emit(ChatEvent.ReasoningDelta(text)) }
                val result = if (model.useStream) {
                    LlmApiService.stream(model, apiKey, buildMessages(), onDelta, onReasoning)
                } else {
                    LlmApiService.complete(model, apiKey, buildMessages()).onSuccess { onDelta(it) }
                }
                val error = result.exceptionOrNull()
                if (error != null) {
                    emit(ChatEvent.Failed(friendlyError(error)))
                    emit(ChatEvent.Finished(null))
                    return@withLock
                }
                val full = raw.toString()
                emit(ChatEvent.AssistantText(ToolCodec.stripCalls(full)))
                history += LlmMessage("assistant", full)
                val calls = if (specs.isEmpty()) emptyList() else ToolCodec.parseCalls(full)
                if (calls.isEmpty()) {
                    // 标记存在但解析不了（被截断 / JSON 非法 / 缺参数）：
                    // 原文已从正文中剔除，这里装进工具调用容器展示，并给模型一次改正的机会，
                    // 避免「格式错了就静默变成一句普通回答」。
                    val leftovers = ToolCodec.unparsedCalls(full)
                    if (leftovers.isNotEmpty() && specs.isNotEmpty()) {
                        for (leftover in leftovers) {
                            // 流式期间若已提前建过容器，直接复用它（把原文作为结果填进去）
                            val announced = announcedNames.removeFirstOrNull()
                            val malformed = ToolCall(tool = announced ?: UNPARSED_TOOL, arguments = emptyMap())
                            if (announced == null) emit(ChatEvent.ToolStarted(malformed))
                            emit(ChatEvent.ToolFinished(malformed, ToolResult(leftover, isError = true), 0L))
                        }
                        history += LlmMessage(
                            "user",
                            "[TOOL_RESULT] " + UNPARSED_TOOL + "\n" + MSG_MALFORMED_CALL + "\n" +
                                leftovers.joinToString("\n")
                        )
                        continue
                    }
                    if (full.isBlank()) emit(ChatEvent.Failed(MSG_EMPTY_REPLY))
                    answered = true
                    break
                }
                for (call in calls) {
                    // 流式期间已经建过容器的工具不再重复创建，只把完整参数补送过去
                    if (announcedNames.isNotEmpty() && announcedNames.first() == call.tool) {
                        announcedNames.removeFirst()
                        emit(ChatEvent.ToolCallUpdated(call))
                    } else {
                        emit(ChatEvent.ToolStarted(call))
                    }
                    val started = System.currentTimeMillis()
                    val toolResult = ToolRegistry.invoke(call, specs, toolContext)
                    emit(ChatEvent.ToolFinished(call, toolResult, System.currentTimeMillis() - started))
                    history += LlmMessage("user", "[TOOL_RESULT] " + call.tool + "\n" + toolResult.text)
                }
            }
            emit(ChatEvent.Finished(if (answered) null else MSG_MAX_ITERATIONS))
        }
    }

    private fun friendlyError(e: Throwable): String {
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

    companion object {
        private const val DEFAULT_SYSTEM_PROMPT =
            "你是 NOT Toolbox 桌面应用内置的 AI 助手，可以直接查询本机的软件包与系统状态，" +
                "并在用户确认后执行安装、更新、卸载等操作。\n" +
                "回答要求：\n" +
                "1. 使用简体中文，简洁直接，避免冗长客套；\n" +
                "2. 涉及本机数据的结论必须以工具返回结果为准，不要凭记忆猜测；\n" +
                "3. 执行会修改系统的操作前，先简要说明将要做什么。"

        const val MSG_NO_KEY = "尚未填写 API Key，请先在「AI 设置」中配置模型。"
        const val MSG_NO_MODEL = "尚未填写模型名称，请先在「AI 设置」中配置模型。"
        const val MSG_EMPTY_REPLY = "模型返回了空内容，请重试或更换模型。"
        const val MSG_MAX_ITERATIONS = "已达到工具调用轮数上限，已停止继续调用工具。"

        /** 模型输出了工具标记但格式无法解析时，在界面上使用的占位工具名 */
        const val UNPARSED_TOOL = "未解析的调用"

        /** 回传给模型的改正提示 */
        private const val MSG_MALFORMED_CALL =
            "上面的内容不是合法的工具调用，已忽略。请严格按规定格式重新输出一次（单独一行）：" +
                "[TOOL_CALL]{\"tool\":\"工具名\",\"arguments\":{...}}"
    }
}