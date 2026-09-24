// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 一次对话会话：系统提示 + 工具调用循环 + 助手回答的时间线

package utils.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 对话过程中推送给界面的事件（只有三种，界面无需理解任何时序） */
sealed class ChatEvent {
    /**
     * 本轮助手回答的最新全貌：正文 + 线性时间线分段。
     * 界面拿到后整份替换即可（分段是不可变的，复制列表很便宜）。
     *
     * @param firstChunkMs 本轮请求发出到**首个分块**的毫秒数（0 表示还没收到任何分块）
     * @param chunks 本轮已收到的分块数（用于区分「服务端没在分块」与「在分块但很慢」）
     */
    data class Update(
        val text: String,
        val segments: List<ChatSegment>,
        val firstChunkMs: Long = 0L,
        val chunks: Int = 0
    ) : ChatEvent()

    /** 出错（已翻译成可读文案） */
    data class Failed(val message: String) : ChatEvent()

    /** 本轮结束；notice 非空表示有需要提示用户的情况 */
    data class Finished(val notice: String?) : ChatEvent()
}

/**
 * 一次对话会话的编排器。
 *
 * 工作方式（不依赖厂商的 function calling）：
 * 1. 系统提示中写清工具清单与调用格式，要求模型把调用写成单独一行 `[TOOL_CALL]{...}`
 * 2. 流式输出时用 [ToolCodec.splitForStreaming] 暂存尾部，保证标记不会泄漏到界面
 * 3. 一轮回答结束后解析出所有工具调用，依次执行并把结果以 [TOOL_RESULT] 回填给模型
 * 4. **不限制工具调用轮数**：模型不再调用工具（给出最终回答）、出错或用户点「停止」时才结束
 *
 * 时间线（思考段 / 工具调用卡片）由本类**独占**维护：界面只消费 [ChatEvent.Update] 里的快照，
 * 不再自己拼装分段（以前 ChatAgent + ChatTimeline + 界面三处各维护一份，工具卡片还要按名字回找）。
 */
class ChatSession(
    private val model: AiModelConfig,
    private val apiKey: String,
    private val specs: List<ToolSpec>,
    private val toolEnv: ToolEnv,
    envSummary: String
) {
    private val history = mutableListOf<LlmMessage>()
    private val turnMutex = Mutex()
    private val systemPrompt: String = buildSystemPrompt(envSummary)

    /** 当前回答的时间线分段（只追加 / 就地更新，绝不重排） */
    private val segments = mutableListOf<ChatSegment>()

    /** 本轮可见正文 */
    private var body = ""

    private var nextToolId = 0L

    /** 本轮流式期间已经建出容器的工具调用 id（按出现顺序），用于把结果填回同一个容器 */
    private val announcedIds = mutableListOf<Long>()

    /** 本轮「参数还在生成」的那张卡片（标记一写出就建，JSON 闭口后并入 [announcedIds]） */
    private var openCardId: Long? = null

    /** 用户是否请求「在执行时继续」（针对当前正在执行的那个工具） */
    private var continueRequested = false

    /**
     * 界面调用：对当前正在执行的工具请求「在执行时继续」。
     * 工具会在下一次轮询时放弃等待，并把「未完成」如实回报给模型（进程继续在终端运行）。
     */
    fun requestContinue() {
        continueRequested = true
    }

    /** 是否已具备调用条件（模型名 + API Key） */
    val isConfigured: Boolean get() = model.modelName.isNotBlank() && apiKey.isNotBlank()

    /** 清空会话历史（开始新对话时调用） */
    fun reset() {
        history.clear()
    }

    /** 发送一条用户消息，返回事件流 */
    fun send(userText: String): Flow<ChatEvent> = callbackFlow {
        val job = launch {
            runTurn(userText) { trySend(it) }
            close()
        }
        awaitClose { job.cancel() }
    }

    private fun buildSystemPrompt(envSummary: String): String {
        val sb = StringBuilder()
        val base = model.systemPrompt.trim()
        sb.append(base.ifEmpty { DEFAULT_SYSTEM_PROMPT })
        if (envSummary.isNotBlank()) {
            sb.append("\n\n当前环境（本机实时采集，仅供参考）：\n").append(envSummary)
        }
        val toolPrompt = ToolPrompt.build(specs)
        if (toolPrompt.isNotBlank()) sb.append("\n\n").append(toolPrompt)
        return sb.toString()
    }

    private fun buildMessages(): List<LlmMessage> {
        val list = ArrayList<LlmMessage>(history.size + 1)
        list += LlmMessage("system", systemPrompt)
        list += history
        return list
    }

    /** 本轮请求发出时刻 / 首块耗时 / 分块数（用于进度显示，能一眼看出是不是服务端没在分块） */
    private var requestStartedAt = 0L
    private var firstChunkMs = 0L
    private var chunks = 0

    /** 发布合并用：最近一次派发的时刻与内容（内容没变就不派发，太快就合并） */
    private var lastEmitAt = 0L
    private var lastEmittedText = ""
    private var lastEmittedSegments: List<ChatSegment> = emptyList()

    /**
     * 派发给界面。
     *
     * 三件以前没做对的事：
     * 1. 内容没变（例如工具调用的 JSON 正在生成、正文被暂存）就不派发——旧实现每个分块都发；
     * 2. [coalesce] 时按 [PUBLISH_INTERVAL_MS] 合并，避免 100+ 分块/秒直接砸到重组上；
     * 3. 丢弃过的中间态不影响正确性：快照是全量状态，下一次派发（或回合结束时的强制派发）会补齐。
     */
    private fun publish(emit: (ChatEvent) -> Unit, coalesce: Boolean) {
        val snapshotSegments = segments.toList()
        if (body == lastEmittedText && snapshotSegments == lastEmittedSegments) return
        val now = System.currentTimeMillis()
        if (coalesce && now - lastEmitAt < PUBLISH_INTERVAL_MS) return
        lastEmitAt = now
        lastEmittedText = body
        lastEmittedSegments = snapshotSegments
        emit(ChatEvent.Update(body, snapshotSegments, firstChunkMs, chunks))
    }

    /** 记录一个分块到达 */
    private fun countChunk() {
        if (firstChunkMs == 0L && requestStartedAt > 0) {
            firstChunkMs = System.currentTimeMillis() - requestStartedAt
        }
        chunks++
    }

    // ---------- 一轮对话 ----------

    private suspend fun runTurn(userText: String, emit: (ChatEvent) -> Unit) = withContext(Dispatchers.IO) {
        turnMutex.withLock {
            body = ""
            segments.clear()
            announcedIds.clear()
            openCardId = null
            continueRequested = false
            if (!isConfigured) {
                emit(ChatEvent.Failed(if (apiKey.isBlank()) MSG_NO_KEY else MSG_NO_MODEL))
                emit(ChatEvent.Finished(null))
                return@withLock
            }
            history += LlmMessage("user", userText)
            // 本轮需要提示用户的说明（如工具调用格式错误）
            var notice: String? = null
            // 不限制工具调用轮数：模型给出最终回答（不再调用工具）、出错或用户点「停止」时才结束。
            // 用户随时可以用「停止」中断（协程可取消），因此不需要轮数上限。
            while (true) {
                // 每轮（一次模型请求）都有一份新的调用清单，而 [announcedIds] 是**按位置**对应这份
                // 清单的锚点，所以必须按轮清空：否则第二轮会复用上一轮的卡片（把旧卡片改写掉，
                // 看起来就像时间线错位），同时提前建好的卡片永远拿不到执行结果（永久转圈）。
                announcedIds.clear()
                openCardId = null
                // 增量扫描：只处理新增分块（旧实现每块都重扫整段累积文本）
                val scanner = ToolCodec.StreamScanner()
                requestStartedAt = System.currentTimeMillis()
                firstChunkMs = 0L
                chunks = 0
                val onDelta: (String) -> Unit = { delta ->
                    scanner.append(delta)
                    countChunk()
                    // 先把容器建出来（参数还没生成完也显示「生成参数」），JSON 闭口后再补全并转「排队中」
                    if (specs.isNotEmpty()) announceCards(scanner)
                    val visible = scanner.visible()
                    if (visible.isNotBlank()) sealThinking()
                    body = visible
                    publish(emit, coalesce = true)
                }
                val onReasoning: (String) -> Unit = { delta ->
                    countChunk()
                    pushThinking(delta)
                    publish(emit, coalesce = true)
                }
                val result = if (model.useStream) {
                    LlmClient.stream(model, apiKey, buildMessages(), onDelta, onReasoning)
                } else {
                    LlmClient.complete(model, apiKey, buildMessages()).onSuccess { onDelta(it) }
                }
                val error = result.exceptionOrNull()
                if (error != null) {
                    emit(ChatEvent.Failed(mapAiError(error)))
                    break
                }

                val full = scanner.rawText
                sealThinking()
                // 回合结束：以整段原文做一次权威解析，并强制派发（丢弃过的中间态在这里补齐）
                body = ToolCodec.visibleText(full)
                publish(emit, coalesce = false)
                history += LlmMessage("assistant", full)
                val calls = if (specs.isEmpty()) emptyList() else ToolCodec.parseCalls(full)
                if (calls.isEmpty()) {
                    val malformed = if (specs.isEmpty()) emptyList() else ToolCodec.malformedCalls(full)
                    if (malformed.isNotEmpty()) {
                        // 标记写了但格式不对：不再自动重试（重复烧 token），
                        // 把原文当错误卡片展示，并提示用户重试。
                        // 流式期间已经建过容器的，直接复用那张（否则会多出一张空气泡）
                        val reuseId = openCardId
                        openCardId = null
                        malformed.forEachIndexed { index, text ->
                            if (index == 0 && reuseId != null) {
                                replaceSegment(reuseId) {
                                    it.copy(
                                        call = ToolCall(UNPARSED_TOOL),
                                        result = ToolResult(text, isError = true),
                                        phase = ToolPhase.FAILED
                                    )
                                }
                            } else {
                                segments += ChatSegment.ToolCallSegment(
                                    id = nextToolId++,
                                    call = ToolCall(UNPARSED_TOOL),
                                    result = ToolResult(text, isError = true),
                                    phase = ToolPhase.FAILED
                                )
                            }
                        }
                        notice = MSG_MALFORMED
                        publish(emit, coalesce = false)
                        break
                    }
                    if (full.isBlank()) emit(ChatEvent.Failed(MSG_EMPTY_REPLY))
                    break
                }

                for ((index, call) in calls.withIndex()) {
                    val id = containerFor(index, call)
                    publish(emit, coalesce = false)
                    val started = System.currentTimeMillis()
                    // 危险级别非 READ 的工具要等用户授权：先把卡片切到「待确认」，
                    // 批准后再转「执行中」，被拒绝则「已拒绝」——不再一律显示成「执行中」
                    val originalConfirm = toolEnv.confirm
                    val spec = ToolDispatcher.specOf(call.tool)
                    val needsConfirm = spec != null && spec.danger != ToolDanger.READ && originalConfirm != null
                    continueRequested = false
                    val callEnv = toolEnv.copy(
                        confirm = if (needsConfirm) {
                            { pending ->
                                setPhase(id, ToolPhase.AWAITING_CONFIRM)
                                publish(emit, coalesce = false)
                                val allowed = originalConfirm.invoke(pending)
                                setPhase(id, if (allowed) ToolPhase.RUNNING else ToolPhase.REJECTED)
                                publish(emit, coalesce = false)
                                allowed
                            }
                        } else {
                            originalConfirm
                        },
                        // 长任务（安装 / 命令）把输出尾部实时刷到卡片上
                        onProgress = { tail ->
                            replaceSegment(id) { it.copy(outputTail = tail) }
                            publish(emit, coalesce = true)
                        },
                        continueRequested = { continueRequested }
                    )
                    val toolResult = ToolDispatcher.invoke(call, specs, callEnv)
                    fillToolResult(id, toolResult, System.currentTimeMillis() - started)
                    publish(emit, coalesce = false)
                    history += LlmMessage("user", "[TOOL_RESULT] " + call.tool + "\n" + toolResult.text)
                }
            }
            settleDanglingToolCards()
            emit(ChatEvent.Finished(notice))
        }
    }

    // ---------- 时间线维护 ----------

    /** 追加一段思考增量：最后一段思考还没封口就续写，已封口则新起一段 */
    private fun pushThinking(delta: String) {
        val last = segments.lastOrNull()
        if (last is ChatSegment.Thinking && last.streaming) {
            segments[segments.size - 1] = last.copy(text = last.text + delta)
        } else {
            segments += ChatSegment.Thinking(text = delta, streaming = true)
        }
    }

    /** 封口当前思考段：正文开始输出或开始调用工具都表示这一段思考到此为止 */
    private fun sealThinking() {
        val index = segments.indexOfLast { it is ChatSegment.Thinking && it.streaming }
        if (index < 0) return
        segments[index] = (segments[index] as ChatSegment.Thinking).copy(streaming = false)
    }

    /**
     * 流式期间把工具卡片尽早建出来（"先给容器、再填内容"）：
     * 1. 标记一写出、JSON 还没闭口 → 先建一张「生成参数」卡片；工具名能在 `"tool":"…"` 闭合时读出就顺手填上；
     * 2. 调用闭口 → 复用那张卡片补全参数并转「排队中」，同时记入 [announcedIds]（执行阶段按序号复用）。
     */
    private fun announceCards(scanner: ToolCodec.StreamScanner) {
        val openId = openCardId
        if (openId != null) {
            scanner.peekToolName()?.let { name -> replaceSegment(openId) { it.copy(call = ToolCall(name)) } }
        } else if (scanner.hasOpenCallBlock) {
            val id = nextToolId++
            segments += ChatSegment.ToolCallSegment(
                id = id,
                call = ToolCall(scanner.peekToolName() ?: TOOL_PLACEHOLDER),
                phase = ToolPhase.AWAITING_ARGS
            )
            openCardId = id
        }
        while (announcedIds.size < scanner.completeCalls.size) {
            val call = scanner.completeCalls[announcedIds.size]
            val reuseId = openCardId
            openCardId = null
            if (reuseId != null) {
                replaceSegment(reuseId) { it.copy(call = call, phase = ToolPhase.QUEUED) }
                announcedIds += reuseId
            } else {
                val id = nextToolId++
                segments += ChatSegment.ToolCallSegment(id = id, call = call, phase = ToolPhase.QUEUED)
                announcedIds += id
            }
        }
    }

    /**
     * 取第 [index] 个调用的容器并置为「执行中」。
     *
     * 只复用「这一轮里为该调用建好、且尚未执行过」的卡片；名称不符或已不处于排队态，
     * 说明锚点错配——此时**宁可在末尾新建一张，也绝不去改写别的卡片**：
     * 改写会让时间线错位（旧操作被顶掉、甚至看起来被"重排"）。
     */
    private fun containerFor(index: Int, call: ToolCall): Long {
        val startedAt = System.currentTimeMillis()
        val announcedId = announcedIds.getOrNull(index)
        if (announcedId != null) {
            val candidate = segments.firstOrNull { it is ChatSegment.ToolCallSegment && it.id == announcedId }
                as? ChatSegment.ToolCallSegment
            if (candidate != null && candidate.phase == ToolPhase.QUEUED && candidate.call.tool == call.tool) {
                replaceSegment(announcedId) {
                    it.copy(call = call, phase = ToolPhase.RUNNING, startedAtMs = startedAt)
                }
                return announcedId
            }
        }
        val id = nextToolId++
        segments += ChatSegment.ToolCallSegment(
            id = id,
            call = call,
            phase = ToolPhase.RUNNING,
            startedAtMs = startedAt
        )
        return id
    }

    private fun fillToolResult(id: Long, result: ToolResult, durationMs: Long) {
        replaceSegment(id) { segment ->
            val phase = when {
                segment.phase == ToolPhase.REJECTED -> ToolPhase.REJECTED
                result.isError -> ToolPhase.FAILED
                else -> ToolPhase.DONE
            }
            segment.copy(result = result, durationMs = durationMs, phase = phase)
        }
    }

    /** 就地更新卡片阶段；[startedAtMs] 大于 0 时一并记录执行开始时刻 */
    private fun setPhase(id: Long, phase: ToolPhase, startedAtMs: Long = 0L) {
        replaceSegment(id) {
            it.copy(phase = phase, startedAtMs = if (startedAtMs > 0) startedAtMs else it.startedAtMs)
        }
    }

    /**
     * 收尾兜底：正常路径下每张卡片都会被落定，但万一还有处于挂起阶段的卡片
     * （锚点错配、异常分支等），也在这里落定为失败——界面绝不该出现永远转圈的卡片。
     */
    private fun settleDanglingToolCards() {
        for (i in segments.indices) {
            val segment = segments[i]
            if (segment is ChatSegment.ToolCallSegment && segment.phase.isPending) {
                segments[i] = segment.copy(
                    phase = ToolPhase.FAILED,
                    result = ToolResult("未执行（本轮已结束）。", isError = true)
                )
            }
        }
    }

    private fun replaceSegment(
        id: Long,
        transform: (ChatSegment.ToolCallSegment) -> ChatSegment.ToolCallSegment
    ) {
        val index = segments.indexOfFirst { it is ChatSegment.ToolCallSegment && it.id == id }
        if (index < 0) return
        segments[index] = transform(segments[index] as ChatSegment.ToolCallSegment)
    }

    companion object {
        /** 快照发布的最小间隔：约 30fps。分块率可以到几百/秒，没必要每个分块都重组一次界面 */
        private const val PUBLISH_INTERVAL_MS = 33L

        private const val DEFAULT_SYSTEM_PROMPT =
            "你是 NOT Toolbox 桌面应用内置的 AI 助手，可以直接查询本机的软件包与系统状态，" +
                "并在用户确认后执行安装、更新、卸载等操作。\n" +
                "回答要求：\n" +
                "1. 使用简体中文，简洁直接，避免冗长客套；\n" +
                "2. 涉及本机数据的结论必须以工具返回结果为准，不要凭记忆猜测；\n" +
                "3. 执行会修改系统的操作前，先简要说明将要做什么。"

        const val MSG_NO_KEY = "尚未填写 API Key，请先在「模型设置」中配置模型。"
        const val MSG_NO_MODEL = "尚未填写模型名称，请先在「模型设置」中配置模型。"
        const val MSG_EMPTY_REPLY = "模型返回了空内容，请重试或更换模型。"
        const val MSG_MALFORMED = "模型的工具调用格式不正确，本轮已停止，请重试或更换模型。"

        /** 模型输出了工具标记但格式无法解析时，界面上使用的占位工具名 */
        const val UNPARSED_TOOL = "未解析的调用"
    }
}
