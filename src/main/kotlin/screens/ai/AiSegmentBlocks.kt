// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 时间线上的两种卡片：思考容器与工具调用容器

package screens.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import components.MaterialSymbols
import kotlinx.coroutines.delay
import utils.ai.ChatSegment
import utils.ai.ToolDanger
import utils.ai.ToolDispatcher
import utils.ai.ToolPhase
import java.util.Locale

/**
 * 思考过程容器（与工具调用容器同款外观）。
 * 思考正在流式且正文还没开始时自动展开，让过程可见；用户手动点过之后以其选择为准。
 */
@Composable
internal fun AiThinkingBlock(text: String, autoExpand: Boolean) {
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
                Icon(
                    imageVector = if (expanded) {
                        MaterialSymbols.KeyboardArrowDown
                    } else {
                        MaterialSymbols.KeyboardArrowRight
                    },
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(18.dp),
                    tint = accent
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

/**
 * 工具调用容器：标题行 + 参数 + 结果都装在里面，调用源文本与结果不会裸放在气泡里。
 * 卡片在参数还没拿全时就会建出来（转圈），同一张卡片随后补齐参数与结果——按 id 就地更新。
 * 工具返回一律展示（不再有「显示工具返回」开关）。
 */
@Composable
internal fun AiToolCallBlock(
    segment: ChatSegment.ToolCallSegment,
    onNavigateToTerminal: () -> Unit,
    onContinue: () -> Unit
) {
    val spec = ToolDispatcher.specOf(segment.call.tool)
    val isWrite = spec != null && spec.danger != ToolDanger.READ
    val isError = segment.result?.isError == true || spec == null
    val accent = timelineAccent(segment)
    // 长结果展开状态
    var resultExpanded by remember { mutableStateOf(false) }
    // 整个容器是否折叠（点击标题行切换）
    var collapsed by remember { mutableStateOf(false) }

    // 执行中每 200ms 自刷新一次已耗时（只重组这一张卡片，不牵动消息列表）
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(segment.phase) {
        while (segment.phase == ToolPhase.RUNNING) {
            nowMs = System.currentTimeMillis()
            delay(200)
        }
    }
    val runningMs = if (segment.phase == ToolPhase.RUNNING && segment.startedAtMs > 0) {
        nowMs - segment.startedAtMs
    } else {
        0L
    }
    val statusText = when (segment.phase) {
        ToolPhase.AWAITING_ARGS -> "生成参数"
        ToolPhase.QUEUED -> "排队中"
        ToolPhase.AWAITING_CONFIRM -> "待确认"
        ToolPhase.RUNNING -> "执行中 " + formatDuration(maxOf(runningMs, 1L))
        ToolPhase.DONE -> "完成 " + formatDuration(segment.durationMs)
        ToolPhase.REJECTED -> "已拒绝"
        ToolPhase.FAILED -> if (segment.durationMs > 0) "失败 " + formatDuration(segment.durationMs) else "失败"
    }

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
                when {
                    segment.phase == ToolPhase.AWAITING_ARGS || segment.phase == ToolPhase.RUNNING ->
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)

                    else -> Icon(
                        imageVector = when (segment.phase) {
                            ToolPhase.QUEUED -> MaterialSymbols.PlayArrow
                            ToolPhase.AWAITING_CONFIRM, ToolPhase.FAILED -> MaterialSymbols.Warning
                            ToolPhase.REJECTED -> MaterialSymbols.Close
                            else -> if (isError) MaterialSymbols.Warning else MaterialSymbols.Check
                        },
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
                        text = if (spec.danger == ToolDanger.SHELL) "终端命令" else "写操作",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (collapsed) {
                        MaterialSymbols.KeyboardArrowRight
                    } else {
                        MaterialSymbols.KeyboardArrowDown
                    },
                    contentDescription = if (collapsed) "展开" else "收起",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (!collapsed) {
                // 动作说明：只在「还没跑完」的阶段显示，避免完成后信息冗余
                val hint = spec?.runningHint.orEmpty()
                if (hint.isNotBlank() && segment.phase.isPending) {
                    Text(
                        text = if (spec != null && spec.slowFirstRun) "$hint · 首次读取较慢" else hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
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

                // 长任务（安装 / 命令）的实时输出尾部：运行中持续刷新，只展示最新一段
                if (segment.phase.isPending && segment.outputTail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = segment.outputTail.takeLast(OUTPUT_PREVIEW_CHARS),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // 左下角：用户可选择不再等待（工具会如实回报「未完成」，进程继续在终端运行）
                if (segment.phase == ToolPhase.RUNNING && spec != null && spec.canContinue) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Material 3 的 Filled 按钮即默认 Button（主色填充容器）
                    Button(onClick = onContinue) {
                        Text("在执行时继续")
                    }
                }

                val result = segment.result
                if (result != null) {
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

                if (isWrite && !segment.phase.isPending) {
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

/** 耗时显示：不足 1 秒用 ms，否则保留一位小数的秒 */
private fun formatDuration(ms: Long): String =
    if (ms < 1000) ms.toString() + "ms" else String.format(Locale.US, "%.1fs", ms / 1000.0)

/** 卡片内实时输出的展示上限（只显示最新一段，避免卡片被刷屏） */
private const val OUTPUT_PREVIEW_CHARS = 800
