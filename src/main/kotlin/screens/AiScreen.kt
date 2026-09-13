// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// AI 助手页：模型配置 + 对话（流式输出 / 思考段 / 工具调用卡片 / 线性时间线）

package screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import components.MaterialSymbols
import config.loadAiConfig
import config.saveAiConfig
import kotlinx.coroutines.*
import utils.PackageManagerType
import utils.PackageManagerUtils
import utils.ai.*

/**
 * AI 助手页。
 *
 * 职责：
 * - 维护 config/ai_config.json（模型列表、当前模型、工具开关）
 * - 维护一次会话的消息列表（用户消息 / 助手消息 / 思考块 / 工具调用块）
 * - 把 [ChatAgent] 的事件流映射为界面状态
 * - 写操作类工具执行前弹出确认框
 *
 * 对话历史保存在进程内的 [ChatAgent] 中（不落盘，重启即清空）；API Key 仅以密文存于配置文件。
 */
@Composable
fun AiScreen(
    selectedPackageManager: PackageManagerType,
    useProxy: Boolean,
    proxyUrl: String,
    isDebug: Boolean,
    onNavigateToTerminal: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf(loadAiConfig()) }
    val activeModel = config.activeModel()

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showContextDialog by remember { mutableStateOf(false) }
    var contextPreview by remember { mutableStateOf("") }
    var showReasoning by remember { mutableStateOf(true) }
    var showToolResults by remember { mutableStateOf(true) }

    // 待确认的工具调用（写操作 / Shell）
    var pendingConfirm by remember { mutableStateOf<ToolCall?>(null) }
    var confirmResult by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }

    // 会话级 ChatAgent（持有对话历史）；置空即开始新对话
    var agent by remember { mutableStateOf<ChatAgent?>(null) }
    var runningJob by remember { mutableStateOf<Job?>(null) }

    val listState = rememberLazyListState()

    /** 实际生效的包管理器（未显式选择时自动检测；首次会探测一次，之后命中进程级缓存） */
    val effectiveManager = remember(selectedPackageManager) {
        if (selectedPackageManager != PackageManagerType.UNKNOWN) {
            selectedPackageManager
        } else {
            PackageManagerUtils.detectPackageManager()
        }
    }

    // 新消息到达、正文增长或时间线卡片增长时滚到底部
    LaunchedEffect(messages.size, messages.lastOrNull()?.text, timelineRevision(messages.lastOrNull())) {
        if (messages.isNotEmpty()) {
            try {
                listState.animateScrollToItem(messages.size - 1)
            } catch (_: Exception) {
                // 列表尚未完成布局，忽略
            }
        }
    }

    // 生成中的实时进度：模型「思考 / 等待首字」阶段界面也要持续更新，
    // 否则用户看不到任何变化，会以为是一次性吐出（伪流式）。
    var elapsedMs by remember { mutableStateOf(0L) }
    LaunchedEffect(busy) {
        elapsedMs = 0
        while (busy) {
            delay(200)
            elapsedMs += 200
        }
    }

    val liveMessage = messages.lastOrNull()
    val liveReceivedChars = if (busy) liveMessage?.text?.length ?: 0 else 0
    // 时间线上可能有多个思考段，这里累计所有思考段的字数
    val liveThinkingChars = if (busy) {
        liveMessage?.segments
            ?.filterIsInstance<ChatSegment.Thinking>()
            ?.sumOf { it.text.length } ?: 0
    } else {
        0
    }

    // 打开系统上下文预览时实时生成一次（采集过程有阻塞调用，放到 IO 线程）
    LaunchedEffect(showContextDialog) {
        if (showContextDialog) {
            contextPreview = try {
                withContext(Dispatchers.IO) {
                    SystemContextBuilder.build(
                        activeModel?.systemContext ?: SystemContextDetail.OFF,
                        effectiveManager
                    )
                }
            } catch (e: Exception) {
                "生成失败：" + (e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun persist(newConfig: AiAppConfig) {
        config = newConfig
        saveAiConfig(newConfig)
        // 配置变化后旧 agent 的模型 / 密钥 / 系统提示均已过期，丢弃会话
        agent = null
    }

    fun requestConfirm(call: ToolCall): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        confirmResult = deferred
        pendingConfirm = call
        return deferred
    }

    fun startNewConversation() {
        runningJob?.cancel()
        runningJob = null
        agent = null
        messages = emptyList()
        busy = false
        notice = null
        errorText = null
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        if (activeModel == null) {
            errorText = "尚未配置模型，请先点击「模型设置」添加一个模型。"
            return
        }
        input = ""
        errorText = null
        notice = null
        messages = messages + ChatMessage(role = ChatRole.USER, text = text)
        val assistantId = System.nanoTime()
        messages = messages + ChatMessage(id = assistantId, role = ChatRole.ASSISTANT, streaming = true)
        busy = true

        runningJob = scope.launch {
            // 解密密钥与系统状态采集都可能阻塞，放到 IO 线程
            val apiKey = withContext(Dispatchers.IO) { SecretStore.unprotect(activeModel.apiKeyEnc) }
            // 一次会话只构造一次 agent；后续轮次复用其内部历史
            val current = agent ?: run {
                val context = try {
                    withContext(Dispatchers.IO) {
                        SystemContextBuilder.build(activeModel.systemContext, effectiveManager)
                    }
                } catch (_: Exception) {
                    ""
                }
                val specs = ToolRegistry.enabledSpecs(
                    toolsEnabled = activeModel.toolsEnabled,
                    disabled = activeModel.disabledTools.toSet(),
                    shellEnabled = config.shellToolEnabled
                )
                val toolContext = ToolContext(
                    manager = effectiveManager,
                    useProxy = useProxy,
                    proxyUrl = proxyUrl,
                    isDebug = isDebug,
                    confirm = { call ->
                        !activeModel.confirmWriteOps || requestConfirm(call).await()
                    }
                )
                ChatAgent(
                    model = activeModel,
                    apiKey = apiKey,
                    specs = specs,
                    toolContext = toolContext,
                    systemContext = context
                ).also { agent = it }
            }

            // 分段即时间线：只按发生顺序追加，界面按列表顺序自上而下渲染
            val timeline = ChatTimeline()
            var body = ""

            fun publish() {
                val snapshotSegments = timeline.segments
                messages = messages.map { message ->
                    if (message.id == assistantId) {
                        message.copy(text = body, segments = snapshotSegments, streaming = true)
                    } else {
                        message
                    }
                }
            }

            try {
                current.send(text).collect { event ->
                    when (event) {
                        is ChatEvent.AssistantText -> {
                            // 正文开始输出 → 当前思考段封口，后续推理会另起一段
                            timeline.sealThinking()
                            body = event.text
                            publish()
                        }

                        is ChatEvent.ReasoningDelta -> {
                            timeline.thinkingDelta(event.text)
                            publish()
                        }

                        is ChatEvent.ToolStarted -> {
                            timeline.toolStarted(event.call)
                            publish()
                        }

                        is ChatEvent.ToolCallUpdated -> {
                            timeline.toolCallUpdated(event.call)
                            publish()
                        }

                        is ChatEvent.ToolFinished -> {
                            timeline.toolFinished(event.call, event.result, event.durationMs)
                            publish()
                        }

                        is ChatEvent.Failed -> {
                            messages = messages.map { message ->
                                if (message.id == assistantId) {
                                    message.copy(streaming = false, error = event.message)
                                } else {
                                    message
                                }
                            }
                        }

                        is ChatEvent.Finished -> {
                            if (event.notice != null) notice = event.notice
                        }
                    }
                }
            } catch (e: Exception) {
                // 用户主动停止（协程取消）不算错误
                if (e !is CancellationException) {
                    val message = e.message ?: e.javaClass.simpleName
                    messages = messages.map { item ->
                        if (item.id == assistantId) item.copy(streaming = false, error = message) else item
                    }
                }
            } finally {
                // 收尾：最后一段思考若还在增长则封口，避免界面一直显示「思考中」
                timeline.sealThinking()
                val snapshotSegments = timeline.segments
                messages = messages.map { item ->
                    if (item.id == assistantId) {
                        item.copy(text = body, segments = snapshotSegments, streaming = false)
                    } else {
                        item
                    }
                }
                busy = false
                runningJob = null
            }
        }
    }

    val displayModelName = activeModel?.let { model ->
        model.name.ifBlank { model.modelName.ifBlank { "未命名模型" } }
    } ?: "未配置模型"

    Column(modifier = Modifier.fillMaxSize()) {
        if (activeModel == null) {
            // 尚未配置模型：整页只保留空状态提示（不需要模型信息栏与输入框）
            AiEmptyState(configured = false, onAddModel = { showModelDialog = true })
        } else {
            AiTopBar(
                modelName = displayModelName,
                providerName = activeModel.provider.displayName,
                toolsEnabled = activeModel.toolsEnabled,
                shellEnabled = config.shellToolEnabled,
                busy = busy,
                hasMessages = messages.isNotEmpty(),
                onOpenModelSettings = { showModelDialog = true },
                onOpenContext = { showContextDialog = true },
                onNewConversation = { startNewConversation() }
            )

            HorizontalDivider()

            errorText?.let { text ->
                AiNoticeRow(
                    text = text,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    onDismiss = { errorText = null }
                )
            }

            notice?.let { text ->
                AiNoticeRow(
                    text = text,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    onDismiss = { notice = null }
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    AiEmptyState(configured = true, onAddModel = {})
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            AiMessageBubble(
                                message = message,
                                showReasoning = showReasoning,
                                showToolResults = showToolResults,
                                onNavigateToTerminal = onNavigateToTerminal
                            )
                        }
                    }
                }
            }

            if (busy) {
                AiProgressRow(
                    elapsedMs = elapsedMs,
                    receivedChars = liveReceivedChars,
                    thinkingChars = liveThinkingChars
                )
            }

            HorizontalDivider()

            AiInputBar(
                value = input,
                onValueChange = { input = it },
                onSend = { send() },
                onStop = {
                    runningJob?.cancel()
                    runningJob = null
                    busy = false
                },
                busy = busy,
                showReasoning = showReasoning,
                onShowReasoningChange = { showReasoning = it },
                showToolResults = showToolResults,
                onShowToolResultsChange = { showToolResults = it }
            )
        }
    }

    if (showModelDialog) {
        AiModelDialog(
            config = config,
            onConfigChange = { persist(it) },
            onDismiss = { showModelDialog = false }
        )
    }

    if (showContextDialog) {
        AiContextPreviewDialog(
            detailName = activeModel?.systemContext?.displayName ?: SystemContextDetail.OFF.displayName,
            text = contextPreview,
            onDismiss = { showContextDialog = false }
        )
    }

    pendingConfirm?.let { call ->
        AiConfirmDialog(
            call = call,
            onAllow = {
                pendingConfirm = null
                confirmResult?.complete(true)
                confirmResult = null
            },
            onDeny = {
                pendingConfirm = null
                confirmResult?.complete(false)
                confirmResult = null
            }
        )
    }
}

/** 顶部状态条：当前模型、工具状态与三个入口按钮 */
@Composable
private fun AiTopBar(
    modelName: String,
    providerName: String,
    toolsEnabled: Boolean,
    shellEnabled: Boolean,
    busy: Boolean,
    hasMessages: Boolean,
    onOpenModelSettings: () -> Unit,
    onOpenContext: () -> Unit,
    onNewConversation: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = MaterialSymbols.Robot2,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = modelName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            val detail = buildString {
                if (providerName.isNotBlank()) append(providerName)
                if (toolsEnabled) {
                    if (isNotEmpty()) append(" · ")
                    append("工具已启用")
                } else {
                    if (isNotEmpty()) append(" · ")
                    append("未启用工具")
                }
                if (shellEnabled) append(" · 含终端命令")
                if (busy) append(" · 正在生成")
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onOpenContext) {
            Icon(MaterialSymbols.Description, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("系统上下文")
        }
        TextButton(onClick = onNewConversation, enabled = hasMessages) {
            Icon(MaterialSymbols.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("新对话")
        }
        Button(onClick = onOpenModelSettings, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
            Icon(MaterialSymbols.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("模型设置")
        }
    }
}

/** 顶部提示条（错误 / 通知） */
@Composable
private fun AiNoticeRow(
    text: String,
    container: Color,
    content: Color,
    onDismiss: () -> Unit
) {
    Surface(color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("关闭", color = content)
            }
        }
    }
}

/**
 * 空状态。
 * - 尚未配置模型：只显示空状态图标、说明与「添加一个模型」按钮
 * - 已配置但还没开始对话：显示助手图标与用法提示
 */
@Composable
private fun AiEmptyState(configured: Boolean, onAddModel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!configured) {
            Icon(
                imageVector = MaterialSymbols.BrightnessEmpty,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "还没有配置任何模型",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "添加一个 OpenAI 兼容的模型后即可开始对话。\n" +
                    "API Key 会加密保存在 config/ai_config.json，不会明文落盘。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onAddModel) {
                Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加一个模型")
            }
        } else {
            Icon(
                imageVector = MaterialSymbols.Robot2,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "可以开始提问了",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "可以直接询问本机软件与系统状态，也可以让它帮你安装、更新、卸载软件包。\n" +
                    "会修改系统的操作在执行前都会先弹窗征求你的同意。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 单条消息。助手消息渲染成一条**线性时间线**：思考卡片 / 工具调用卡片按发生顺序
 * （[ChatMessage.segments] 的列表顺序）自上而下排列，正文作为最后一个节点；
 * 气泡里只放正文，不出现 CALL 源文本。
 */
@Composable
private fun AiMessageBubble(
    message: ChatMessage,
    showReasoning: Boolean,
    showToolResults: Boolean,
    onNavigateToTerminal: () -> Unit
) {
    val isUser = message.role == ChatRole.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = if (isUser) "你" else "AI 助手",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (isUser) {
            if (message.text.isNotBlank()) {
                AiTextBubble(text = message.text, streaming = false, error = null, isUser = true)
            }
        } else {
            AiTimeline(
                message = message,
                showReasoning = showReasoning,
                showToolResults = showToolResults,
                onNavigateToTerminal = onNavigateToTerminal
            )
        }
    }
}

/** 时间线内容指纹（分段数量 + 各分段文本长度）：用于判断过程中是否需要跟随滚动 */
private fun timelineRevision(message: ChatMessage?): Int {
    if (message == null) return 0
    var sum = message.segments.size * 31
    message.segments.forEach { segment ->
        sum += when (segment) {
            is ChatSegment.Thinking -> segment.text.length
            is ChatSegment.ToolCallSegment -> segment.call.tool.length + (segment.result?.text?.length ?: 0)
        }
    }
    return sum
}

/** 时间线节点配色：思考用主题色；工具按 只读 / 写操作 / 出错 区分，与卡片边框、图标保持一致 */
@Composable
private fun timelineAccent(segment: ChatSegment): Color {
    if (segment !is ChatSegment.ToolCallSegment) return MaterialTheme.colorScheme.primary
    val spec = ToolRegistry.specOf(segment.call.tool)
    val isWrite = spec != null && spec.danger != ToolDanger.READ
    val isError = segment.result?.isError == true || spec == null
    return when {
        isError -> MaterialTheme.colorScheme.error
        isWrite -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
}

/**
 * 助手回答的线性时间线：[ChatMessage.segments] 里的分段按**发生顺序**自上而下排列，
 * 正文（回答）作为最后一个节点接在同一条竖线上。
 *
 * 卡片顺序完全由 `segments` 的列表顺序决定（累积事件时只追加、不插队），
 * 因此「思考 → 工具 → 思考 → 工具 → 正文」会如实呈现，不会把思考全挤到最前面。
 */
@Composable
private fun AiTimeline(
    message: ChatMessage,
    showReasoning: Boolean,
    showToolResults: Boolean,
    onNavigateToTerminal: () -> Unit
) {
    val nodes = message.segments.filter { segment ->
        when (segment) {
            is ChatSegment.Thinking -> showReasoning && segment.text.isNotBlank()
            is ChatSegment.ToolCallSegment -> true
        }
    }
    val bubbleVisible = message.text.isNotBlank() || message.streaming || message.error != null
    // 没有卡片（只有正文）时不画时间线，保持原来的单气泡外观
    val railVisible = nodes.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        nodes.forEachIndexed { index, segment ->
            AiTimelineNode(
                order = index + 1,
                accent = timelineAccent(segment),
                rail = railVisible,
                showTopLine = railVisible && index > 0,
                showBottomLine = railVisible && (index < nodes.size - 1 || bubbleVisible)
            ) {
                when (segment) {
                    is ChatSegment.Thinking -> AiThinkingBlock(
                        text = segment.text,
                        autoExpand = segment.streaming && message.text.isBlank()
                    )

                    is ChatSegment.ToolCallSegment -> AiToolCallBlock(
                        segment = segment,
                        showResult = showToolResults,
                        onNavigateToTerminal = onNavigateToTerminal
                    )
                }
            }
        }

        if (bubbleVisible) {
            AiTimelineNode(
                order = nodes.size + 1,
                accent = MaterialTheme.colorScheme.primary,
                rail = railVisible,
                showTopLine = railVisible,
                showBottomLine = false
            ) {
                AiTextBubble(
                    text = message.text,
                    streaming = message.streaming,
                    error = message.error,
                    isUser = false
                )
            }
        }
    }
}

/**
 * 时间线上的一个节点：左侧是竖线与序号圆点，右侧是卡片内容。
 *
 * 竖线用 [drawBehind] 按节点实际高度绘制（从上一个节点一路连到下一个节点）；
 * 节点之间不放外部间距（间距放在内容内部的底部），否则竖线会断开。
 */
@Composable
private fun AiTimelineNode(
    order: Int,
    accent: Color,
    rail: Boolean,
    showTopLine: Boolean,
    showBottomLine: Boolean,
    content: @Composable () -> Unit
) {
    val railWidth = 30.dp
    val badgeSize = 18.dp
    val badgeTop = 12.dp
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val badgeFill = MaterialTheme.colorScheme.background

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (!rail) return@drawBehind
                val centerX = railWidth.toPx() / 2f
                val lineWidth = 2.dp.toPx()
                val badgeBottom = (badgeTop + badgeSize).toPx()
                if (showTopLine) {
                    drawRect(
                        color = lineColor,
                        topLeft = Offset(centerX - lineWidth / 2f, 0f),
                        size = Size(lineWidth, badgeBottom)
                    )
                }
                if (showBottomLine) {
                    drawRect(
                        color = lineColor,
                        topLeft = Offset(centerX - lineWidth / 2f, badgeBottom),
                        size = Size(lineWidth, (size.height - badgeBottom).coerceAtLeast(0f))
                    )
                }
            }
    ) {
        if (rail) {
            Column(
                modifier = Modifier.width(railWidth).padding(top = badgeTop),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 圆点用页面底色填充，正好盖住下面的竖线
                Box(
                    modifier = Modifier
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(badgeFill)
                        .border(BorderStroke(1.dp, accent), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = order.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (rail) 8.dp else 0.dp)
        ) { content() }
    }
}

/** 正文气泡：用户消息与助手回答共用，只有配色与是否显示进度 / 错误不同 */
@Composable
private fun AiTextBubble(
    text: String,
    streaming: Boolean,
    error: String?,
    isUser: Boolean
) {
    Surface(
        color = if (isUser) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.widthIn(max = 760.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (text.isNotBlank()) {
                SelectionContainer {
                    Text(text = text, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (streaming && text.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "正在生成…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            error?.let { message ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⚠ " + message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}


/**
 * 思考过程容器（与工具调用容器同款外观）。
 * 思考正在流式且正文还没开始时自动展开，让过程可见；用户手动点过之后以其选择为准。
 */
@Composable
private fun AiThinkingBlock(text: String, autoExpand: Boolean) {
    var userExpanded by remember { mutableStateOf<Boolean?>(null) }
    val expanded = userExpanded ?: autoExpand
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { userExpanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (autoExpand) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = MaterialSymbols.Neurology,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accent
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "思考过程",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = text.length.toString() + " 字" + if (autoExpand) " · 思考中" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
            if (expanded && text.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 工具调用：独立容器（带边框与标题行），CALL 的源文本与结果都装在里面，不裸放在气泡里 */
@Composable
private fun AiToolCallBlock(
    segment: ChatSegment.ToolCallSegment,
    showResult: Boolean,
    onNavigateToTerminal: () -> Unit
) {
    val spec = ToolRegistry.specOf(segment.call.tool)
    val isWrite = spec != null && spec.danger != ToolDanger.READ
    val isError = segment.result?.isError == true || spec == null
    val accent = when {
        isError -> MaterialTheme.colorScheme.error
        isWrite -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    // 长结果展开状态
    var resultExpanded by remember { mutableStateOf(false) }
    // 整个容器是否折叠（点击标题行切换）
    var collapsed by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { collapsed = !collapsed },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (segment.pending) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (isError) MaterialSymbols.Warning else MaterialSymbols.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accent
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = segment.call.tool,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (isWrite) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (spec?.danger == ToolDanger.SHELL) "终端命令" else "写操作",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                val statusText = when {
                    !segment.pending -> segment.durationMs.toString() + " ms"
                    spec != null && spec.params.isNotEmpty() && segment.call.arguments.isEmpty() -> "生成参数…"
                    else -> "执行中…"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (collapsed) "展开" else "收起",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!collapsed) {
                val args = segment.call.arguments.entries.joinToString("   ") { entry ->
                    entry.key + "=" + entry.value.toString().trim('"')
                }
                if (args.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = args,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val result = segment.result
                if (result != null && showResult) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val full = result.text
                    val visible = if (resultExpanded || full.length <= 400) full else full.take(400) + "…"
                    SelectionContainer {
                        Text(
                            text = visible,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (result.isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (full.length > 400) {
                        TextButton(onClick = { resultExpanded = !resultExpanded }) {
                            Text(if (resultExpanded) "收起结果" else "展开完整结果（" + full.length + " 字符）")
                        }
                    }
                }

                if (isWrite && !segment.pending) {
                    TextButton(onClick = onNavigateToTerminal) {
                        Icon(MaterialSymbols.Terminal2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("在终端查看输出")
                    }
                }
            }
        }
    }
}

/** 底部输入区：上方为显示开关（Chip），下方为输入框与发送按钮 */
@Composable
private fun AiInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    busy: Boolean,
    showReasoning: Boolean,
    onShowReasoningChange: (Boolean) -> Unit,
    showToolResults: Boolean,
    onShowToolResultsChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = showReasoning,
                onClick = { onShowReasoningChange(!showReasoning) },
                label = { Text("显示思考过程", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = if (showReasoning) {
                    {
                        Icon(
                            imageVector = MaterialSymbols.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    null
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = showToolResults,
                onClick = { onShowToolResultsChange(!showToolResults) },
                label = { Text("显示工具返回", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = if (showToolResults) {
                    {
                        Icon(
                            imageVector = MaterialSymbols.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    null
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                    val enter = event.key == Key.Enter && event.type == KeyEventType.KeyDown
                    if (enter && !event.isShiftPressed) {
                        onSend()
                        true
                    } else {
                        false
                    }
                },
                placeholder = { Text("键入以发送（Enter 发送，Shift+Enter 换行）") },
                maxLines = 6,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (busy) {
                FilledTonalButton(onClick = onStop, modifier = Modifier.height(56.dp)) {
                    Icon(MaterialSymbols.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("停止")
                }
            } else {
                Button(onClick = onSend, enabled = value.isNotBlank(), modifier = Modifier.height(56.dp)) {
                    Icon(MaterialSymbols.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("发送")
                }
            }
        }
    }
}

/** 写操作确认框 */
@Composable
private fun AiConfirmDialog(
    call: ToolCall,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    val spec = ToolRegistry.specOf(call.tool)
    val isShell = spec?.danger == ToolDanger.SHELL
    AlertDialog(
        onDismissRequest = onDeny,
        icon = { Icon(MaterialSymbols.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text(if (isShell) "允许执行终端命令？" else "允许修改系统？") },
        text = {
            Column {
                Text(
                    text = if (isShell) {
                        "AI 请求在终端执行以下命令，命令将以你的用户权限运行："
                    } else {
                        "AI 请求通过包管理器执行以下操作，会修改本机的软件状态："
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = call.summary(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                if (isShell) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "仅在你完全信任模型输出时才允许执行。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onAllow) { Text("允许执行") }
        },
        dismissButton = {
            TextButton(onClick = onDeny) { Text("拒绝") }
        }
    )
}

/** 系统上下文预览（只读，展示实际会发送给模型的内容） */
@Composable
private fun AiContextPreviewDialog(
    detailName: String,
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("系统上下文（当前级别：$detailName）") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = text.ifBlank {
                        "当前级别不携带系统状态，或暂时读取不到数据。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/**
 * 模型设置对话框：左侧模型列表，右侧模型详情表单。
 * 任一字段改动都会立即写入 config/ai_config.json。
 */
@Composable
private fun AiModelDialog(
    config: AiAppConfig,
    onConfigChange: (AiAppConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by remember { mutableStateOf(config.activeModel()?.id ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.width(960.dp).height(640.dp)) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "AI 模型设置", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        text = "密钥保护：" + SecretStore.protectionDescription(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("完成")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.width(280.dp).fillMaxHeight()) {
                        Button(
                            onClick = {
                                val created = AiModelConfig(
                                    id = "m" + System.currentTimeMillis(),
                                    name = "新模型",
                                    modelName = "",
                                    provider = LlmProvider.OPENAI
                                )
                                selectedId = created.id
                                onConfigChange(
                                    config.copy(
                                        models = config.models + created,
                                        activeModelId = config.activeModelId ?: created.id
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("新增模型")
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        ) {
                            config.models.forEach { model ->
                                AiModelListItem(
                                    model = model,
                                    selected = model.id == selectedId,
                                    active = model.id == config.activeModelId,
                                    onClick = { selectedId = model.id },
                                    onActivate = { onConfigChange(config.copy(activeModelId = model.id)) },
                                    onDelete = {
                                        val rest = config.models.filterNot { it.id == model.id }
                                        if (selectedId == model.id) selectedId = rest.firstOrNull()?.id ?: ""
                                        onConfigChange(
                                            config.copy(
                                                models = rest,
                                                activeModelId = config.activeModelId.takeIf { it != model.id }
                                                    ?: rest.firstOrNull()?.id
                                            )
                                        )
                                    }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            if (config.models.isEmpty()) {
                                Text(
                                    text = "还没有模型，点击上方「新增模型」开始配置。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = config.shellToolEnabled,
                                onCheckedChange = { onConfigChange(config.copy(shellToolEnabled = it)) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("允许终端命令工具", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "默认关闭；开启后 AI 可请求执行任意命令",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(modifier = Modifier.width(16.dp))

                    val selected = config.models.firstOrNull { it.id == selectedId }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (selected == null) {
                            Text(
                                text = "从左侧选择或新增一个模型。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            AiModelForm(
                                model = selected,
                                isActive = selected.id == config.activeModelId,
                                onModelChange = { updated ->
                                    onConfigChange(
                                        config.copy(models = config.models.map { if (it.id == updated.id) updated else it })
                                    )

                                },
                                onActivate = { onConfigChange(config.copy(activeModelId = selected.id)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 左侧模型列表中的一项 */
@Composable
private fun AiModelListItem(
    model: AiModelConfig,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.name.ifBlank { "未命名模型" },
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (active) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "使用中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = model.modelName.ifBlank { "未填写模型名" } + " · " + model.provider.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!active) {
                IconButton(onClick = onActivate, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = MaterialSymbols.Check,
                        contentDescription = "设为当前模型",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = MaterialSymbols.Delete,
                    contentDescription = "删除模型",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 单个模型的详细配置表单。所有改动即时保存。
 * API Key 需要点「保存密钥」才写入（避免每次输入都触发一次加密与落盘）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiModelForm(
    model: AiModelConfig,
    isActive: Boolean,
    onModelChange: (AiModelConfig) -> Unit,
    onActivate: () -> Unit
) {
    var keyInput by remember(model.id) { mutableStateOf("") }
    var providerExpanded by remember { mutableStateOf(false) }
    var contextExpanded by remember { mutableStateOf(false) }
    var thinkingExpanded by remember { mutableStateOf(false) }
    val hasKey = model.apiKeyEnc.isNotBlank()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "模型详情",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (!isActive) {
                TextButton(onClick = onActivate) { Text("设为当前模型") }
            }
            Text(
                text = if (hasKey) "密钥已保存" else "尚未保存密钥",
                style = MaterialTheme.typography.labelSmall,
                color = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = model.name,
            onValueChange = { onModelChange(model.copy(name = it)) },
            label = { Text("显示名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = { providerExpanded = it }) {
            OutlinedTextField(
                value = model.provider.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("提供商") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                LlmProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.displayName) },
                        onClick = {
                            providerExpanded = false
                            val keepBaseUrl = model.baseUrl.isNotBlank() && model.baseUrl != model.provider.defaultBaseUrl
                            onModelChange(
                                model.copy(
                                    provider = provider,
                                    baseUrl = if (keepBaseUrl) model.baseUrl else ""
                                )
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = model.modelName,
            onValueChange = { onModelChange(model.copy(modelName = it)) },
            label = { Text("模型名称") },
            placeholder = { Text("例如 gpt-4o-mini / deepseek-chat / qwen-plus") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = model.baseUrl,
            onValueChange = { onModelChange(model.copy(baseUrl = it)) },
            label = { Text("Base URL（留空使用默认）") },
            placeholder = { Text(model.provider.defaultBaseUrl.ifBlank { "https://example.com/v1" }) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "实际请求地址：" + model.provider.chatCompletionsUrl(model.baseUrl),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("API Key") },
                placeholder = { Text(if (hasKey) "已加密保存，留空表示不修改" else "粘贴 API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (keyInput.isNotBlank()) {
                        onModelChange(model.copy(apiKeyEnc = SecretStore.protect(keyInput.trim())))
                        keyInput = ""
                    }
                },
                enabled = keyInput.isNotBlank()
            ) {
                Text("保存密钥")
            }
            if (hasKey) {
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onModelChange(model.copy(apiKeyEnc = "")) }) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = model.systemPrompt,
            onValueChange = { onModelChange(model.copy(systemPrompt = it)) },
            label = { Text("系统提示词（留空使用默认）") },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        AiSwitchRow(
            title = "流式输出",
            subtitle = "逐字显示回答；部分网关不支持时可关闭",
            checked = model.useStream,
            onCheckedChange = { onModelChange(model.copy(useStream = it)) }
        )
        AiSwitchRow(
            title = "允许调用内置工具",
            subtitle = "关闭后模型只能用自身知识回答，无法读取本机状态",
            checked = model.toolsEnabled,
            onCheckedChange = { onModelChange(model.copy(toolsEnabled = it)) }
        )
        AiSwitchRow(
            title = "修改系统的操作需要确认",
            subtitle = "安装 / 更新 / 卸载 / 执行命令前弹窗征求同意（强烈建议保持开启）",
            checked = model.confirmWriteOps,
            onCheckedChange = { onModelChange(model.copy(confirmWriteOps = it)) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        ExposedDropdownMenuBox(expanded = contextExpanded, onExpandedChange = { contextExpanded = it }) {
            OutlinedTextField(
                value = model.systemContext.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("系统状态透传") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = contextExpanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = contextExpanded, onDismissRequest = { contextExpanded = false }) {
                SystemContextDetail.entries.forEach { detail ->
                    DropdownMenuItem(
                        text = { Text(detail.displayName) },
                        onClick = {
                            contextExpanded = false
                            onModelChange(model.copy(systemContext = detail))
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "该级别的内容可在页面「系统上下文」按钮中预览；任何级别都不会包含密钥、代理凭据、MAC 地址或终端内容。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        ExposedDropdownMenuBox(expanded = thinkingExpanded, onExpandedChange = { thinkingExpanded = it }) {
            OutlinedTextField(
                value = model.thinkingMode.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("思考模式（DeepSeek 生效）") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = thinkingExpanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = thinkingExpanded, onDismissRequest = { thinkingExpanded = false }) {
                ThinkingMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.displayName) },
                        onClick = {
                            thinkingExpanded = false
                            onModelChange(model.copy(thinkingMode = mode))
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "DeepSeek 等网关若不显式传 thinking，可能默认开始「思考」：这段时间只回传推理内容、正文迟迟不出现，" +
                "看起来就像一次性吐出。默认的「关闭思考」会让正文立即开始流式；若该网关不支持此参数并报错，" +
                "改成「默认」即可。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = model.maxToolIterations.toString(),
            onValueChange = { raw ->
                val parsed = raw.filter { it.isDigit() }.take(2).toIntOrNull()
                if (parsed != null) onModelChange(model.copy(maxToolIterations = parsed.coerceIn(1, 20)))
            },
            label = { Text("单轮回复内最多调用工具轮数") },
            singleLine = true,
            modifier = Modifier.width(260.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))

        Text(text = "工具清单", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "取消勾选即禁用该工具（不会写入提示词，模型也无法调用）。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ToolRegistry.allSpecs.forEach { spec ->
            val enabled = spec.name !in model.disabledTools
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        val next = if (checked) {
                            model.disabledTools - spec.name
                        } else {
                            model.disabledTools + spec.name
                        }
                        onModelChange(model.copy(disabledTools = next))
                    }
                )
                Column(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = spec.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace
                        )
                        if (spec.danger != ToolDanger.READ) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (spec.danger == ToolDanger.SHELL) "终端命令" else "写操作",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (spec.danger == ToolDanger.SHELL && !model.disabledTools.contains(spec.name)) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "还需在左下角打开总开关",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = spec.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** 表单里的开关行 */
@Composable
private fun AiSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 生成中的实时进度条：等待首字 / 思考中 / 已接收字数，每 200ms 刷新一次 */
@Composable
private fun AiProgressRow(elapsedMs: Long, receivedChars: Int, thinkingChars: Int) {
    val seconds = String.format(java.util.Locale.US, "%.1f", elapsedMs / 1000.0)
    val detail = buildString {
        append("已等待 ").append(seconds).append(" 秒")
        if (thinkingChars > 0) append(" · 思考中 ").append(thinkingChars).append(" 字")
        if (receivedChars > 0) {
            append(" · 已接收 ").append(receivedChars).append(" 字")
        } else if (thinkingChars == 0) {
            append(" · 等待模型首字")
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
