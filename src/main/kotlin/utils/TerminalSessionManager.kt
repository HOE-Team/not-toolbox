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
    
    private val _lastExitCode = MutableStateFlow<Int?>(null)
    val lastExitCode: StateFlow<Int?> = _lastExitCode.asStateFlow()
    
    private val _wasCancelled = MutableStateFlow(false)
    val wasCancelled: StateFlow<Boolean> = _wasCancelled.asStateFlow()
    
    private var shellSessionJob: Job? = null
    private var commandExecutionJob: Job? = null
    
    /**
     * 当前使用的终端编码名称，默认为 "UTF-8"
     */
    private var currentEncodingName: String = "UTF-8"
    
    /**
     * 设置终端编码
     */
    fun setEncoding(encodingName: String) {
        currentEncodingName = encodingName
    }
    
    /**
     * 获取当前编码名称
     */
    fun getEncodingName(): String = currentEncodingName
    
    /**
     * 获取当前编码的 Charset
     */
    private fun getCharset(): java.nio.charset.Charset {
        return try {
            java.nio.charset.Charset.forName(currentEncodingName)
        } catch (_: Exception) {
            java.nio.charset.Charset.forName("UTF-8")
        }
    }
    
    /**
     * 根据编码名称获取对应的 Windows 代码页命令
     */
    private fun getCodePageCommand(): String {
        return when (currentEncodingName.uppercase()) {
            "GBK" -> "chcp 936 > nul && "
            "UTF-16" -> "chcp 1200 > nul && "
            else -> "chcp 65001 > nul && " // UTF-8
        }
    }
    
    /**
     * 获取当前操作系统名称
     */
    private val isWindows: Boolean by lazy {
        System.getProperty("os.name").lowercase().contains("windows")
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
            // 使用用户设置的编码
            val processCharset = getCharset()
            processWriter = OutputStreamWriter(process.outputStream, processCharset)
            _isRunning.value = true
            
            addOutput("=== 终端会话已启动 ($currentEncodingName) ===\n")
            
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
     * 注意：此方法仅用于交互式终端，不适合需要等待命令完成的场景
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
     * 执行命令并等待完成（直接启动进程，不通过交互式 Shell）
     * 用于安装等需要等待命令执行完毕并获取退出码的场景
     */
    fun executeCommandAndWait(command: String) {
        // 取消之前的执行任务
        commandExecutionJob?.cancel()
        
        // 清空之前的输出和状态
        clearOutput()
        _wasCancelled.value = false
        _lastExitCode.value = null
        
        commandExecutionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                _isRunning.value = true
                
                // 根据平台构建命令
                val cmdArray = if (isWindows) {
                    // Windows 上根据用户选择的编码设置控制台代码页
                    arrayOf("cmd.exe", "/c", "${getCodePageCommand()}$command")
                } else {
                    arrayOf("sh", "-c", command)
                }
                
                val processBuilder = ProcessBuilder(*cmdArray)
                processBuilder.redirectErrorStream(true)
                val process = processBuilder.start()
                currentProcess = process
                
                // 使用用户设置的编码读取输出
                val processCharset = getCharset()
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
                        addOutput("\n[输出读取结束]\n")
                    }
                } finally {
                    reader.close()
                }
                
                // 等待进程结束
                val exitCode = process.waitFor()
                _lastExitCode.value = exitCode
                addOutput("\n=== 进程已结束，退出码: $exitCode ===\n")
                
            } catch (e: Exception) {
                addOutput("执行命令时出错: ${e.message}\n")
                _lastExitCode.value = -1
            } finally {
                _isRunning.value = false
                currentProcess = null
                processWriter = null
            }
        }
    }
    
    /**
     * 停止当前进程
     * 使用 taskkill（Windows）或 pkill（Linux）直接终止进程
     */
    fun stopCurrentProcess() {
        _wasCancelled.value = true
        
        // 取消执行任务
        commandExecutionJob?.cancel()
        
        // 如果进程仍在运行，使用平台原生方式终止
        currentProcess?.let { process ->
            if (process.isAlive) {
                try {
                    // 先尝试获取进程 PID 并使用 taskkill/pkill
                    val pid = process.pid()
                    if (pid > 0) {
                        try {
                            if (isWindows) {
                                // Windows: 使用 taskkill /F 强制终止进程树
                                Runtime.getRuntime().exec(arrayOf("taskkill", "/F", "/T", "/PID", pid.toString()))
                            } else {
                                // Linux: 使用 kill -9 强制终止
                                Runtime.getRuntime().exec(arrayOf("kill", "-9", pid.toString()))
                            }
                            addOutput("\n正在终止进程 (PID: $pid)...\n")
                        } catch (_: Exception) { }
                    }
                    
                    // 等待进程结束
                    Thread.sleep(500)
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
