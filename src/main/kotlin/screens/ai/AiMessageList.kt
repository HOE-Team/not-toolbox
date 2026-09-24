// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 消息列表：助手回答渲染成一条线性时间线（思考卡片 / 工具卡片 / 正文）

package screens.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import components.MarkdownText
import utils.ai.ChatMessage
import utils.ai.ChatRole
import utils.ai.ChatSegment
import utils.ai.ToolDanger
import utils.ai.ToolDispatcher

/** 消息列表：用户消息是普通气泡，助手消息是时间线 */
@Composable
internal fun AiMessageList(
    messages: List<ChatMessage>,
    listState: LazyListState,
    onNavigateToTerminal: () -> Unit,
    onContinue: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            AiMessageBubble(
                message = message,
                onNavigateToTerminal = onNavigateToTerminal,
                onContinue = onContinue
            )
        }
    }
}

@Composable
private fun AiMessageBubble(
    message: ChatMessage,
    onNavigateToTerminal: () -> Unit,
    onContinue: () -> Unit
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
                onNavigateToTerminal = onNavigateToTerminal,
                onContinue = onContinue
            )
        }
    }
}

/** 时间线节点配色：思考用主题色；工具按 只读 / 写操作 / 出错 区分，与卡片边框、图标保持一致 */
@Composable
internal fun timelineAccent(segment: ChatSegment): Color {
    if (segment !is ChatSegment.ToolCallSegment) return MaterialTheme.colorScheme.primary
    val spec = ToolDispatcher.specOf(segment.call.tool)
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
 * 思考内容与工具返回**一律展示**（原先的「显示思考过程 / 显示工具返回」开关已移除）；
 * 卡片顺序完全由 `segments` 的列表顺序决定（累积事件时只追加、不插队），
 * 因此「思考 → 工具 → 思考 → 工具 → 正文」会如实呈现，不会把思考全挤到最前面。
 */
@Composable
private fun AiTimeline(
    message: ChatMessage,
    onNavigateToTerminal: () -> Unit,
    onContinue: () -> Unit
) {
    val nodes = message.segments.filter { segment ->
        when (segment) {
            is ChatSegment.Thinking -> segment.text.isNotBlank()
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
                        onNavigateToTerminal = onNavigateToTerminal,
                        onContinue = onContinue
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
                if (isUser) {
                    SelectionContainer {
                        Text(text = text, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    // 助手回答按 Markdown 渲染（标题 / 列表 / 表格 / 代码块 / 链接等）
                    MarkdownText(text = text)
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
                    text = "⚠ $message",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
