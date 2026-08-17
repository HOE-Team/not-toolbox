// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import components.MaterialSymbols
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import utils.AnsiStyledText
import utils.TerminalSessionManager
import config.ToolCommandSessionMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen() {
    var commandInput by remember { mutableStateOf("") }
    var terminalOutput by remember { mutableStateOf("") }

    // 会话列表（Compose 状态，增删标签页时自动重组）
    val sessions = TerminalSessionManager.sessions
    var activeId by remember { mutableStateOf(TerminalSessionManager.activeSessionId.value) }

    // 首次进入：确保至少存在一个会话且设为活动
    // 在"使用默认会话"模式下若无会话则创建默认会话；"每次新建会话"模式下保持空状态
    LaunchedEffect(Unit) {
        if (TerminalSessionManager.sessions.isEmpty() &&
            TerminalSessionManager.toolCommandSessionMode == ToolCommandSessionMode.DEFAULT
        ) {
            TerminalSessionManager.getOrCreateDefaultSession()
        }
        if (TerminalSessionManager.activeSessionId.value == null && TerminalSessionManager.sessions.isNotEmpty()) {
            TerminalSessionManager.setActiveSession(TerminalSessionManager.sessions.last().id)
        }
    }

    // 监听活动会话切换
    LaunchedEffect(Unit) {
        TerminalSessionManager.activeSessionId.collect { activeId = it }
    }

    // 收集活动会话的输出（全局别名指向活动会话）
    LaunchedEffect(activeId) {
        terminalOutput = TerminalSessionManager.getOutput(activeId ?: -1L)
    }
    LaunchedEffect(Unit) {
        TerminalSessionManager.outputFlow.collect { terminalOutput = it }
    }

    // 解析 ANSI 转义序列为 AnnotatedString
    val annotatedOutput = remember(terminalOutput) {
        parseAnsiToAnnotatedString(terminalOutput)
    }

    // 当前活动会话在标签列表中的下标
    val activeIndex = sessions.indexOfFirst { it.id == activeId }.coerceAtLeast(0)

    if (sessions.isEmpty()) {
        // 空状态：没有活跃的终端会话
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "没有活跃的终端会话",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "按下“+”或下方按钮来创建一个会话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = {
                    TerminalSessionManager.createAndActivateSession()
                }) {
                    Icon(MaterialSymbols.Add, contentDescription = "新会话", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("新会话")
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // 标签页行：每个标签页内部带有各自的关闭按钮
        TabRow(
            selectedTabIndex = activeIndex,
            modifier = Modifier.fillMaxWidth()
        ) {
            sessions.forEach { s ->
                Tab(
                    selected = s.id == activeId,
                    onClick = { TerminalSessionManager.setActiveSession(s.id) },
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(s.title)
                            // 默认会话不可关闭，其余会话在 Tab 内部右侧显示关闭按钮
                            if (!TerminalSessionManager.isDefaultSession(s.id)) {
                                Icon(
                                    MaterialSymbols.Close,
                                    contentDescription = "关闭 ${s.title} 会话",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { TerminalSessionManager.closeSession(s.id) }
                                )
                            }
                        }
                    }
                )
            }
        }

        // 终端输出区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E) // 深色终端背景
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val scrollState = rememberScrollState()
                val horizontalScrollState = rememberScrollState()

                LaunchedEffect(terminalOutput) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                Text(
                    text = annotatedOutput,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(scrollState)
                        .horizontalScroll(horizontalScrollState),
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFCCCCCC)
                    )
                )
            }
        }

        // 指令输入区域：清空输出按钮在左侧，命令输入框在右侧
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 清空输出按钮（移动到输入框左侧）
            IconButton(
                onClick = {
                    activeId?.let { TerminalSessionManager.clearOutput(it) }
                },
                enabled = terminalOutput.isNotEmpty()
            ) {
                Icon(MaterialSymbols.ClearAll, contentDescription = "清空输出", modifier = Modifier.size(20.dp))
            }

            // 命令输入框
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("指令输入") },
                placeholder = { Text("按下回车发送") },
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (commandInput.isNotBlank()) {
                            val targetId = activeId
                            if (targetId != null) {
                                TerminalSessionManager.executeCommand(targetId, commandInput)
                                commandInput = ""
                            }
                        }
                    }
                )
            )
        }
        }
    }
}

/**
 * 将包含 ANSI 转义序列的文本解析为 AnnotatedString
 */
private fun parseAnsiToAnnotatedString(text: String): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    
    val styledTexts = TerminalSessionManager.parseAnsiText(text)
    
    return buildAnnotatedString {
        for (styled in styledTexts) {
            val color = TerminalSessionManager.ansiColorToComposeColor(styled.foregroundColor)
            val bgColor = TerminalSessionManager.ansiColorToComposeColor(styled.backgroundColor)
            
            withStyle(
                SpanStyle(
                    color = color ?: Color(0xFFCCCCCC), // 默认浅灰色
                    background = bgColor ?: Color.Transparent,
                    fontWeight = if (styled.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (styled.isItalic) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = FontFamily.Monospace
                )
            ) {
                append(styled.text)
            }
        }
    }
}
