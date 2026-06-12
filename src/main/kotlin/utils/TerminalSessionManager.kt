// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ANSI 转义序列解析后的文本片段
 */
data class AnsiStyledText(
    val text: String,
    val foregroundColor: Int? = null,  // 0-255 颜色索引，null 表示默认
    val backgroundColor: Int? = null,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false
)

/**
 * 终端会话管理器，用于管理交互式终端会话和命令执行
 */
object TerminalSessionManager {
    private var currentProcess: Process? = null
    private var processWriter: OutputStreamWriter? = null
    private val outputBuffer = ConcurrentLinkedQueue<String>()
    private val _outputFlow = MutableStateFlow("")
    val outputFlow: StateFlow<String> = _outputFlow.asStateFlow()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private var shellSessionJob: Job? = null
    
    /**
     * 获取当前操作系统名称
     */
    private val isWindows: Boolean by lazy {
        System.getProperty("os.name").lowercase().contains("windows")
    }
    
    /**
     * 获取 Windows 系统的代码页编码
     * 中文 Windows 默认使用 GBK (Cp936)
     */
    private val windowsCharset: java.nio.charset.Charset by lazy {
        try {
            // 尝试获取系统默认编码
            val encoding = System.getProperty("sun.jnu.encoding") ?: "GBK"
            java.nio.charset.Charset.forName(encoding)
        } catch (_: Exception) {
            java.nio.charset.Charset.forName("GBK")
        }
    }
    
    /**
     * 启动交互式 Shell 会话
     */
    fun startShellSession() {
        if (currentProcess?.isAlive == true) return
        
        try {
            // 根据平台启动对应的 Shell
            val shellCommand = if (isWindows) {
                arrayOf("cmd.exe")
            } else {
                arrayOf("sh")
            }
            
            val processBuilder = ProcessBuilder(*shellCommand)
            processBuilder.redirectErrorStream(true) // 合并 stdout 和 stderr
            val process = processBuilder.start()
            
            currentProcess = process
            // Windows 使用系统编码（GBK），其他平台使用 UTF-8
            val processCharset = if (isWindows) windowsCharset else Charsets.UTF_8
            processWriter = OutputStreamWriter(process.outputStream, processCharset)
            _isRunning.value = true
            
            addOutput("=== 终端会话已启动 ===\n")
            
            // 启动输出读取协程
            shellSessionJob = CoroutineScope(Dispatchers.IO).launch {
                val reader = BufferedReader(InputStreamReader(process.inputStream, processCharset))
                try {
                    val buffer = CharArray(4096)
                    var charsRead: Int
                    while (isActive && process.isAlive) {
                        charsRead = reader.read(buffer, 0, buffer.size)
                        if (charsRead > 0) {
                            val text = String(buffer, 0, charsRead)
                            addOutput(text)
                        } else if (charsRead == -1) {
                            break
                        }
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        addOutput("\n[终端输出读取结束]\n")
                    }
                } finally {
                    reader.close()
                }
            }
            
            // 等待进程结束
            CoroutineScope(Dispatchers.IO).launch {
                val exitCode = process.waitFor()
                shellSessionJob?.join()
                addOutput("\n=== 终端会话已结束，退出码: $exitCode ===\n")
                _isRunning.value = false
                currentProcess = null
                processWriter = null
            }
        } catch (e: Exception) {
            addOutput("启动终端会话时出错: ${e.message}\n")
            _isRunning.value = false
        }
    }
    
    /**
     * 执行命令（发送到交互式 Shell）
     */
    fun executeCommand(command: String): Boolean {
        return try {
            // 如果 Shell 未运行，先启动
            if (currentProcess?.isAlive != true) {
                startShellSession()
                // 等待 Shell 启动
                Thread.sleep(200)
            }
            
            // 发送命令到 Shell
            val writer = processWriter
            if (writer != null && currentProcess?.isAlive == true) {
                writer.write(command)
                writer.write("\n")
                writer.flush()
                true
            } else {
                addOutput("终端未连接\n")
                false
            }
        } catch (e: Exception) {
            addOutput("发送命令时出错: ${e.message}\n")
            false
        }
    }
    
    /**
     * 停止当前进程
     */
    fun stopCurrentProcess() {
        processWriter?.let { writer ->
            try {
                // 发送 Ctrl+C 信号
                writer.write("\u0003")
                writer.flush()
                addOutput("\n^C\n")
            } catch (_: Exception) { }
        }
        
        // 如果进程仍在运行，强制终止
        currentProcess?.let { process ->
            if (process.isAlive) {
                try {
                    process.destroy()
                    Thread.sleep(100)
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                } catch (_: Exception) { }
            }
        }
        
        shellSessionJob?.cancel()
        currentProcess = null
        processWriter = null
        _isRunning.value = false
    }
    
    /**
     * 添加输出到缓冲区
     */
    private fun addOutput(text: String) {
        outputBuffer.add(text)
        if (outputBuffer.size > 2000) { // 限制缓冲区大小
            outputBuffer.poll()
        }
        _outputFlow.value = outputBuffer.joinToString("")
    }
    
    /**
     * 清空输出
     */
    fun clearOutput() {
        outputBuffer.clear()
        _outputFlow.value = ""
    }
    
    /**
     * 获取当前输出
     */
    fun getOutput(): String = outputBuffer.joinToString("")
    
    /**
     * 获取最后N行输出
     */
    fun getLastLines(n: Int): String {
        val lines = outputBuffer.joinToString("").lines()
        return lines.takeLast(n).joinToString("\n")
    }
    
    /**
     * 解析 ANSI 转义序列，返回带样式的文本片段列表
     */
    fun parseAnsiText(text: String): List<AnsiStyledText> {
        if (text.isEmpty()) return emptyList()
        
        val result = mutableListOf<AnsiStyledText>()
        val ansiRegex = Regex("\u001B\\[(\\d+(?:;\\d+)*)m")
        val parts = ansiRegex.split(text)
        val matches = ansiRegex.findAll(text).toList()
        
        var currentFg: Int? = null
        var currentBg: Int? = null
        var currentBold = false
        var currentItalic = false
        var currentUnderline = false
        
        for (i in parts.indices) {
            val part = parts[i]
            if (part.isNotEmpty()) {
                result.add(AnsiStyledText(
                    text = part,
                    foregroundColor = currentFg,
                    backgroundColor = currentBg,
                    isBold = currentBold,
                    isItalic = currentItalic,
                    isUnderline = currentUnderline
                ))
            }
            
            if (i < matches.size) {
                val codes = matches[i].groupValues[1].split(";").mapNotNull { it.toIntOrNull() }
                var j = 0
                while (j < codes.size) {
                    val code = codes[j]
                    when {
                        code == 0 -> {
                            currentFg = null
                            currentBg = null
                            currentBold = false
                            currentItalic = false
                            currentUnderline = false
                        }
                        code == 1 -> currentBold = true
                        code == 3 -> currentItalic = true
                        code == 4 -> currentUnderline = true
                        code == 22 -> currentBold = false
                        code == 23 -> currentItalic = false
                        code == 24 -> currentUnderline = false
                        code in 30..37 -> currentFg = code - 30
                        code == 38 -> {
                            // 256 色前景
                            if (j + 2 < codes.size && codes[j + 1] == 5) {
                                currentFg = codes[j + 2]
                                j += 2
                            }
                        }
                        code == 39 -> currentFg = null
                        code in 40..47 -> currentBg = code - 40
                        code == 48 -> {
                            // 256 色背景
                            if (j + 2 < codes.size && codes[j + 1] == 5) {
                                currentBg = codes[j + 2]
                                j += 2
                            }
                        }
                        code == 49 -> currentBg = null
                        code in 90..97 -> currentFg = code - 90 + 8  // 亮色前景
                        code in 100..107 -> currentBg = code - 100 + 8  // 亮色背景
                    }
                    j++
                }
            }
        }
        
        return result
    }
    
    /**
     * 将 ANSI 颜色索引转换为 Compose Color
     */
    fun ansiColorToComposeColor(colorIndex: Int?): Color? {
        if (colorIndex == null) return null
        
        val argb: Long = when (colorIndex) {
            0 -> 0xFF000000L    // Black
            1 -> 0xFFAA0000L    // Red
            2 -> 0xFF00AA00L    // Green
            3 -> 0xFFAA5500L    // Yellow
            4 -> 0xFF0000AAL    // Blue
            5 -> 0xFFAA00AAL    // Magenta
            6 -> 0xFF00AAAAL    // Cyan
            7 -> 0xFFAAAAAAL    // White
            8 -> 0xFF555555L    // Bright Black
            9 -> 0xFFFF5555L    // Bright Red
            10 -> 0xFF55FF55L   // Bright Green
            11 -> 0xFFFFFF55L   // Bright Yellow
            12 -> 0xFF5555FFL   // Bright Blue
            13 -> 0xFFFF55FFL   // Bright Magenta
            14 -> 0xFF55FFFFL   // Bright Cyan
            15 -> 0xFFFFFFFFL   // Bright White
            else -> {
                if (colorIndex in 16..231) {
                    val n = colorIndex - 16
                    val r = (n / 36) * 51
                    val g = ((n % 36) / 6) * 51
                    val b = (n % 6) * 51
                    0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                } else if (colorIndex in 232..255) {
                    val gray = (colorIndex - 232) * 10 + 8
                    0xFF000000L or (gray.toLong() shl 16) or (gray.toLong() shl 8) or gray.toLong()
                } else {
                    return null
                }
            }
        }
        return Color(argb)
    }
}
