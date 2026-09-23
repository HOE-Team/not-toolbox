// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// AI 会话页的状态持有者：会话状态与对话编排都收在这里，界面只负责渲染与转发意图

package screens.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import config.loadAiConfig
import config.saveAiConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utils.PackageManagerType
import utils.ai.AiAppConfig
import utils.ai.AiModelConfig
import utils.ai.ChatEvent
import utils.ai.ChatMessage
import utils.ai.ChatRole
import utils.ai.ChatSegment
import utils.ai.ChatSession
import utils.ai.EnvironmentSummary
import utils.ai.SecretStore
import utils.ai.ToolCall
import utils.ai.ToolDispatcher
import utils.ai.ToolEnv
import kotlin.time.Duration.Companion.milliseconds

/** 会话页需要的外部环境（由 MainApp 透传） */
data class AiChatDeps(
    val manager: PackageManagerType,
    val useProxy: Boolean = false,
    val proxyUrl: String = "",
    val isDebug: Boolean = true
)

/**
 * AI 会话页的状态持有者。
 *
 * 由 MainApp 持有（应用级作用域），页面只读状态、只调用方法；切页不再丢对话，生成中切页也不会被取消。
 *
 * **对话只存在于内存中：绝不落盘。** 不写任何对话日志/缓存文件，进程退出即清空；
 * 只有模型配置（`config/ai_config.json`）与加密后的 API Key 会落盘。
 * 后续如需"恢复上次对话"，应先与产品/隐私要求确认，不要顺手加文件写入。
 *
 * 以前这些状态与派生值散在 1800 行的 Composable 里（十几个 `mutableStateOf` + 每 200ms 的计时器 +
 * 三个手算的派生统计 + agent/job/deferred 交错），现在集中在这里。
 */
class AiChatState(private val scope: CoroutineScope) {

    var config by mutableStateOf(loadAiConfig())
        private set
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var input by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
    var errorText by mutableStateOf<String?>(null)
    var elapsedMs by mutableStateOf(0L)
        private set
    var showReasoning by mutableStateOf(true)
    var showToolResults by mutableStateOf(true)
    var pendingConfirm by mutableStateOf<ToolCall?>(null)
        private set

    /**
     * 生成阶段。以前界面只有一句「等待模型首字」，但那时请求可能**还没发出去**
     * （例如会话初始化要读本机信息），用户就会以为是模型慢。分开显示才能一眼定位。
     */
    enum class Stage { IDLE, PREPARING, WAITING, STREAMING }

    var stage by mutableStateOf(Stage.IDLE)
        private set

    /** 本轮请求发出到首个分块的毫秒数（0 表示还没收到） */
    var firstChunkMs by mutableStateOf(0L)
        private set

    /** 本轮已收到的分块数（0/极少 = 服务端没在分块；持续增长 = 正在流式） */
    var chunkCount by mutableStateOf(0)
        private set

    /** 会话级 ChatSession（持有对话历史）；置空即开始新对话 */
    private var session: ChatSession? = null
    private var job: Job? = null
    private var timerJob: Job? = null
    private var confirmDeferred: CompletableDeferred<Boolean>? = null
    private var idSeq = System.nanoTime()

    val activeModel: AiModelConfig? get() = config.activeModel()

    /** 生成过程中已接收的正文长度 */
    val receivedChars: Int get() = if (busy) messages.lastOrNull()?.text?.length ?: 0 else 0

    /** 生成过程中累计的思考字数（时间线上可能有多段思考） */
    val thinkingChars: Int
        get() = if (busy) {
            messages.lastOrNull()?.segments
                ?.filterIsInstance<ChatSegment.Thinking>()
                ?.sumOf { it.text.length } ?: 0
        } else {
            0
        }

    private fun nextId(): Long = idSeq++

    /** 保存配置：模型 / 密钥 / 提示词 / 工具集都可能变了，旧会话随之作废 */
    fun persist(newConfig: AiAppConfig) {
        config = newConfig
        saveAiConfig(newConfig)
        session = null
    }

    fun startNewConversation() {
        job?.cancel()
        job = null
        session = null
        confirmDeferred?.complete(false)
        confirmDeferred = null
        pendingConfirm = null
        messages = emptyList()
        busy = false
        stage = Stage.IDLE
        firstChunkMs = 0L
        chunkCount = 0
        notice = null
        errorText = null
        stopTimer()
    }

    /** 用户主动停止生成（协程取消不算错误） */
    fun stop() {
        job?.cancel()
        job = null
        busy = false
        stage = Stage.IDLE
        stopTimer()
    }

    /**
     * 让正在执行的工具「在执行时继续」：不再等待它跑完，
     * 由工具把「未完成」如实回报给模型（终端里的进程继续运行）。
     */
    fun continueRunningTool() {
        session?.requestContinue()
        notice = "已请求继续（任务仍在后台运行）"
    }

    /** 写操作确认框的结果 */
    fun resolveConfirm(allow: Boolean) {
        pendingConfirm = null
        confirmDeferred?.complete(allow)
        confirmDeferred = null
    }

    fun send(deps: AiChatDeps) {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        val model = activeModel
        if (model == null) {
            errorText = "尚未配置模型，请先点击「模型设置」添加一个模型。"
            return
        }
        input = ""
        errorText = null
        notice = null
        messages = messages + ChatMessage(id = nextId(), role = ChatRole.USER, text = text)
        val assistantId = nextId()
        messages = messages + ChatMessage(id = assistantId, role = ChatRole.ASSISTANT, streaming = true)
        busy = true
        stage = Stage.PREPARING
        firstChunkMs = 0L
        chunkCount = 0
        startTimer()

        job = scope.launch {
            // 解密密钥、采集环境摘要、构建会话都可能阻塞，统一放到 IO 线程
            val current = session ?: withContext(Dispatchers.IO) {
                val apiKey = SecretStore.unprotect(model.apiKeyEnc)
                val env = try {
                    EnvironmentSummary.build(deps.manager)
                } catch (_: Exception) {
                    ""
                }
                val specs = ToolDispatcher.enabledSpecs(
                    toolsEnabled = model.toolsEnabled,
                    disabled = model.disabledTools.toSet(),
                    shellEnabled = config.shellToolEnabled
                )
                val toolEnv = ToolEnv(
                    manager = deps.manager,
                    useProxy = deps.useProxy,
                    proxyUrl = deps.proxyUrl,
                    isDebug = deps.isDebug,
                    confirm = { call -> !model.confirmWriteOps || requestConfirm(call).await() }
                )
                ChatSession(
                    model = model,
                    apiKey = apiKey,
                    specs = specs,
                    toolEnv = toolEnv,
                    envSummary = env
                ).also { session = it }
            }

            try {
                // 会话已就绪，请求即将发出：从这里开始才是真正的「等待模型首字」
                stage = Stage.WAITING
                current.send(text).collect { event -> applyEvent(assistantId, event) }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    val message = e.message ?: e.javaClass.simpleName
                    messages = messages.updateMessage(assistantId) { it.copy(streaming = false, error = message) }
                }
            } finally {
                // 思考段的封口由 ChatSession 负责，这里只落定「生成结束」
                messages = messages.updateMessage(assistantId) { it.copy(streaming = false) }
                busy = false
                stage = Stage.IDLE
                job = null
                stopTimer()
            }
        }
    }

    private fun applyEvent(assistantId: Long, event: ChatEvent) {
        when (event) {
            is ChatEvent.Update -> {
                if (event.chunks > 0) {
                    stage = Stage.STREAMING
                    firstChunkMs = event.firstChunkMs
                    chunkCount = event.chunks
                }
                messages = messages.updateMessage(assistantId) {
                    it.copy(text = event.text, segments = event.segments, streaming = true)
                }
            }

            is ChatEvent.Failed -> messages = messages.updateMessage(assistantId) {
                it.copy(streaming = false, error = event.message)
            }

            is ChatEvent.Finished -> if (event.notice != null) notice = event.notice
        }
    }

    /** 写操作确认：由工具调用线程（IO）发起，界面弹框后通过 [resolveConfirm] 回填 */
    private fun requestConfirm(call: ToolCall): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        confirmDeferred?.complete(false)
        confirmDeferred = deferred
        // 状态写入统一切回主线程，避免在 IO 线程改 Compose 状态
        scope.launch { pendingConfirm = call }
        return deferred
    }

    private fun startTimer() {
        stopTimer()
        elapsedMs = 0
        timerJob = scope.launch {
            while (busy) {
                delay(200.milliseconds)
                elapsedMs += 200
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}

/** 只替换指定 id 的那条消息 */
private fun List<ChatMessage>.updateMessage(
    id: Long,
    transform: (ChatMessage) -> ChatMessage
): List<ChatMessage> = map { if (it.id == id) transform(it) else it }
