// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// AI 助手页：只做接线（收集状态、转发意图），具体渲染见同包的其它文件

package screens.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import components.MaterialSymbols
import kotlinx.coroutines.delay
import utils.PackageManagerType
import utils.PackageManagerUtils
import java.util.Locale

/**
 * AI 助手页。
 *
 * - 状态与对话编排在 [AiChatState]：由 MainApp 持有（应用级），**本页不创建**，
 *   因此切到其它页面再回来对话仍在，生成中的请求也不会被取消
 * - 模型配置写入 config/ai_config.json（API Key 仅以密文落盘）
 * - 写操作类工具执行前弹出确认框
 */
@Composable
fun AiScreen(
    state: AiChatState,
    selectedPackageManager: PackageManagerType,
    useProxy: Boolean,
    proxyUrl: String,
    isDebug: Boolean,
    onNavigateToTerminal: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    var showModelDialog by remember { mutableStateOf(false) }

    // 每次进入本页（含从其它页切回来）都落在最新一条：会话状态是应用级的，
    // 但 LazyColumn 的滚动位置随页面重建，不补这一步会从最开头显示
    LaunchedEffect(Unit) {
        val lastIndex = state.messages.lastIndex
        if (lastIndex >= 0) {
            try {
                listState.scrollToItem(lastIndex, scrollOffset = Int.MAX_VALUE)
            } catch (_: Exception) {
                // 布局竞态，忽略
            }
        }
    }

    /** 实际生效的包管理器（未显式选择时自动检测；首次会探测一次，之后命中进程级缓存） */
    val deps = remember(selectedPackageManager, useProxy, proxyUrl, isDebug) {
        AiChatDeps(
            manager = if (selectedPackageManager != PackageManagerType.UNKNOWN) {
                selectedPackageManager
            } else {
                PackageManagerUtils.detectPackageManager()
            },
            useProxy = useProxy,
            proxyUrl = proxyUrl,
            isDebug = isDebug
        )
    }

    // 跟到底部。
    // 以前的做法是「每个增量都重启一次 animateScrollToItem」——正文越长，滚动动画的重新布局
    // 越贵，渲染线程会被压住，看上去就像一次性吐出。现在分两条：
    // 1. 新消息到达：立即滚到底；
    // 2. 生成期间：每 150ms 节流跟随，而且只有用户本来就在底部时才跟（不抢用户的滚动）。
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            try {
                listState.scrollToItem(state.messages.size - 1)
            } catch (_: Exception) {
                // 列表尚未完成布局，忽略
            }
        }
    }
    LaunchedEffect(state.busy) {
        while (state.busy) {
            delay(150)
            val lastIndex = state.messages.lastIndex
            if (lastIndex >= 0 && !listState.canScrollForward) {
                try {
                    listState.scrollToItem(lastIndex, scrollOffset = Int.MAX_VALUE)
                } catch (_: Exception) {
                    // 布局竞态，忽略
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val model = state.activeModel
        if (model == null) {
            // 尚未配置模型：整页只保留空状态提示（不需要模型信息栏与输入框）
            AiEmptyState(configured = false, onAddModel = { showModelDialog = true })
        } else {
            AiTopBar(
                modelName = model.name.ifBlank { model.modelName.ifBlank { "未命名模型" } },
                providerName = model.provider.displayName,
                toolsEnabled = model.toolsEnabled,
                shellEnabled = state.config.shellToolEnabled,
                busy = state.busy,
                hasMessages = state.messages.isNotEmpty(),
                onOpenModelSettings = { showModelDialog = true },
                onNewConversation = { state.startNewConversation() }
            )

            HorizontalDivider()
            AiNotices(state)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty()) {
                    AiEmptyState(configured = true, onAddModel = {})
                } else {
                    AiMessageList(
                        messages = state.messages,
                        listState = listState,
                        showReasoning = state.showReasoning,
                        showToolResults = state.showToolResults,
                        onNavigateToTerminal = onNavigateToTerminal,
                        onContinue = { state.continueRunningTool() }
                    )
                }
            }

            if (state.busy) {
                AiProgressRow(state)
            }

            HorizontalDivider()

            AiInputBar(
                value = state.input,
                onValueChange = { state.input = it },
                onSend = { state.send(deps) },
                onStop = { state.stop() },
                busy = state.busy,
                showReasoning = state.showReasoning,
                onShowReasoningChange = { state.showReasoning = it },
                showToolResults = state.showToolResults,
                onShowToolResultsChange = { state.showToolResults = it }
            )
        }
    }

    if (showModelDialog) {
        AiModelDialog(
            config = state.config,
            onConfigChange = { state.persist(it) },
            onDismiss = { showModelDialog = false }
        )
    }

    state.pendingConfirm?.let { call ->
        AiConfirmDialog(
            call = call,
            onAllow = { state.resolveConfirm(true) },
            onDeny = { state.resolveConfirm(false) }
        )
    }
}

/** 顶部提示条：错误与通知共用一套外观 */
@Composable
private fun AiNotices(state: AiChatState) {
    state.errorText?.let { text ->
        AiNoticeRow(
            text = text,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            onDismiss = { state.errorText = null }
        )
    }
    state.notice?.let { text ->
        AiNoticeRow(
            text = text,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            onDismiss = { state.notice = null }
        )
    }
}

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

/** 顶部状态条：当前模型、工具状态与两个入口按钮 */
@Composable
private fun AiTopBar(
    modelName: String,
    providerName: String,
    toolsEnabled: Boolean,
    shellEnabled: Boolean,
    busy: Boolean,
    hasMessages: Boolean,
    onOpenModelSettings: () -> Unit,
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
                if (isNotEmpty()) append(" · ")
                append(if (toolsEnabled) "工具已启用" else "未启用工具")
                if (shellEnabled) append(" · 含终端命令")
                if (busy) append(" · 正在生成")
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onNewConversation, enabled = hasMessages) {
            Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("新对话")
        }
        Button(
            onClick = onOpenModelSettings,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(MaterialSymbols.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("模型设置")
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
            Text(text = "还没有配置任何模型", style = MaterialTheme.typography.titleMedium)
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
            Text(text = "可以开始提问了", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "让它帮你操作包管理器、提供解决方案或是聊天。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 生成中的实时进度条。分阶段显示，便于「一眼定位慢在哪」：
 * - 准备会话：请求还没发出去（读环境等）
 * - 等待首字：请求已发出，尚未收到任何分块
 * - 流式接收：已收到首字，显示首字延迟 / 分块数 / 字数
 *
 * 这些字段都在**本 Composable 内部**读取，因此 200ms 的计时刷新只会重组这一行，
 * 不会带着整页（含消息列表）一起重组。
 */
@Composable
private fun AiProgressRow(state: AiChatState) {
    val seconds = String.format(Locale.US, "%.1f", state.elapsedMs / 1000.0)
    val detail = buildString {
        when (state.stage) {
            AiChatState.Stage.PREPARING -> append("准备会话…")
            AiChatState.Stage.WAITING -> append("已等待 ").append(seconds).append("s · 等待首字")
            AiChatState.Stage.STREAMING -> {
                val first = String.format(Locale.US, "%.1f", state.firstChunkMs / 1000.0)
                append("首字 ").append(first).append("s · 分块 ").append(state.chunkCount)
                if (state.thinkingChars > 0) append(" · 思考中 ").append(state.thinkingChars).append(" 字")
                if (state.receivedChars > 0) append(" · 正文 ").append(state.receivedChars).append(" 字")
            }
            AiChatState.Stage.IDLE -> append("已等待 ").append(seconds).append("s")
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
