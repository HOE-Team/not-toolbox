// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Stop
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen() {
    var commandInput by remember { mutableStateOf("") }
    var terminalOutput by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    
    // Collect flow updates
    LaunchedEffect(Unit) {
        TerminalSessionManager.outputFlow.collect { output ->
            terminalOutput = output
        }
    }
    
    LaunchedEffect(Unit) {
        TerminalSessionManager.isRunning.collect { running ->
            isRunning = running
        }
    }
    
    // 解析 ANSI 转义序列为 AnnotatedString
    val annotatedOutput = remember(terminalOutput) {
        parseAnsiToAnnotatedString(terminalOutput)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = Int.MAX_VALUE
                )
            }
        }
        
        // 控制按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 清空按钮
            OutlinedButton(
                onClick = { TerminalSessionManager.clearOutput() },
                modifier = Modifier.weight(1f),
                enabled = terminalOutput.isNotEmpty()
            ) {
                Icon(Icons.Default.Clear, contentDescription = "清空", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("清空输出")
            }
            
            // 停止按钮
            OutlinedButton(
                onClick = { TerminalSessionManager.stopCurrentProcess() },
                modifier = Modifier.weight(1f),
                enabled = isRunning,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = "停止", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("停止进程")
            }
        }
        
        // 指令输入区域
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                            TerminalSessionManager.executeCommand(commandInput)
                            commandInput = ""
                        }
                    }
                )
            )
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
