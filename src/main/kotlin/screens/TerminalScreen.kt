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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
    var terminalOutput by remember { mutableStateOf("") }

    // 终端直接输入：当前在终端输入框中输入但尚未发送的文本
    var pendingInput by remember { mutableStateOf("") }
    // 终端输入框是否获得焦点（用于显示操作提示）
    var inputFieldFocus by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }
    // 活动会话的进程是否已结束（用于提示\"按回车关闭\"并拦截回车）
    var sessionEnded by remember { mutableStateOf(false) }

    // 会话列表（Compose 状态，增删标签页时自动重组）
    val sessions = TerminalSessionManager.sessions
    var activeId by remember { mutableStateOf(TerminalSessionManager.activeSessionId.value) }
    // 活动会话是否为默认会话（默认会话不可关闭，故不受\"按回车关闭\"影响）
    val isActiveDefault = remember(activeId) { TerminalSessionManager.isDefaultSession(activeId ?: -1L) }

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

    // 收集活动会话的\"进程已结束\"状态
    LaunchedEffect(activeId) {
        sessionEnded = TerminalSessionManager.getSession(activeId ?: -1L)?.ended?.value ?: false
        TerminalSessionManager.getSession(activeId ?: -1L)?.ended?.collect { sessionEnded = it }
    }

    // 解析 ANSI 转义序列为 AnnotatedString
    val annotatedOutput = remember(terminalOutput) {
        parseAnsiToAnnotatedString(terminalOutput)
    }

    // 发送当前输入行到活动会话并清空输入框
    val sendPendingInput: () -> Unit = {
        if (pendingInput.isNotBlank()) {
            val targetId = activeId
            if (targetId != null) {
                TerminalSessionManager.executeCommand(targetId, pendingInput)
                pendingInput = ""
            }
        }
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
                            // 右侧按钮区：清空输出按钮位于关闭按钮左边
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 清空该会话输出
                                Icon(
                                    MaterialSymbols.ClearAll,
                                    contentDescription = "清空 ${s.title} 输出",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { TerminalSessionManager.clearOutput(s.id) }
                                )
                                // 默认会话不可关闭，其余会话显示关闭按钮
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
                    }
                )
            }
        }

        // 终端区域（输出 + 内嵌输入行）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E) // 深色终端背景
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 拦截按键：非默认会话进程结束后按回车关闭会话；Ctrl+C 中断（通过父级冒泡接收输入框未消费的按键）
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when {
                            // 会话已结束（且非默认会话，默认会话不可关闭）：回车关闭该会话
                            sessionEnded && !isActiveDefault && event.key == Key.Enter -> {
                                activeId?.let { TerminalSessionManager.closeSession(it) }
                                true
                            }
                            // Ctrl+C：向 Shell 发送中断字符
                            event.key == Key.C && event.isCtrlPressed -> {
                                activeId?.let { TerminalSessionManager.sendRawInput(it, "\u0003") }
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                // 输出区域：点击任意位置将焦点交给输入框，从而可直接输入
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable { inputFocusRequester.requestFocus() }
                ) {
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

                    // 未聚焦时的操作提示（仅终端为空且会话未结束时显示）
                    if (terminalOutput.isEmpty() && !inputFieldFocus && pendingInput.isEmpty() && !sessionEnded) {
                        Text(
                            text = "点击此处可直接输入指令（回车执行，Ctrl+C 中断）",
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp),
                            color = Color(0xFF6E6E6E),
                            fontSize = 12.sp
                        )
                    }

                    // 进程结束后：提示（默认会话不可关闭，故仅提示进程结束，不提示关闭）
                    if (sessionEnded) {
                        Text(
                            text = if (isActiveDefault) "进程已结束" else "进程已结束，按回车关闭该会话",
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp),
                            color = Color(0xFF6E6E6E),
                            fontSize = 12.sp
                        )
                    }
                }

                // 输入行：提示符 + 真正的文本输入框
                // （字符由系统输入框架处理，可正确支持 Shift / CapsLock / 输入法，不再依赖原始按键映射）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "> ",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFCCCCCC)
                        )
                    )

                    BasicTextField(
                        value = pendingInput,
                        onValueChange = { pendingInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(inputFocusRequester)
                            .onFocusChanged { inputFieldFocus = it.isFocused },
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFCCCCCC)
                        ),
                        cursorBrush = SolidColor(Color(0xFFCCCCCC)),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                // 非默认会话已结束时按回车是关闭会话；默认会话不可关闭，仍发送命令
                                if (sessionEnded && !isActiveDefault) {
                                    activeId?.let { TerminalSessionManager.closeSession(it) }
                                } else {
                                    sendPendingInput()
                                }
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (pendingInput.isEmpty() && !inputFieldFocus) {
                                    Text(
                                        text = if (sessionEnded && !isActiveDefault) "按回车关闭该会话" else "输入指令，回车执行",
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF6E6E6E)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }
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
