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
import config.ToolCommandSessionMode

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
 * 一个终端会话，包含独立的进程、输出缓冲与运行状态。
 */
class TerminalSession internal constructor(
    val id: Long,
    var title: String
) {
    internal var process: Process? = null
    internal var writer: OutputStreamWriter? = null
    internal val outputBuffer = ConcurrentLinkedQueue<String>()
    internal val output = MutableStateFlow("")
    internal val isRunning = MutableStateFlow(false)
    internal val lastExitCode = MutableStateFlow<Int?>(null)
    internal val wasCancelled = MutableStateFlow(false)
    internal var shellJob: Job? = null
    internal var commandJob: Job? = null
}

/**
 * 终端会话管理器，用于管理多个终端会话（标签页）和命令执行。
 *
 * 每个会话拥有独立的进程、输出缓冲与运行状态。全局的 [outputFlow]、[isRunning]、
 * [lastExitCode]、[wasCancelled] 作为"当前活动会话"的别名，便于原有代码（工具页、
 * 安装对话框等）在不感知具体会话的情况下继续工作。
 */
object TerminalSessionManager {
    /** 默认会话 ID：所有非用户发起的指令（工具页/安装）在 DEFAULT 模式下统一重定向到此会话 */
    private const val DEFAULT_SESSION_ID = 0L
    private const val DEFAULT_SESSION_TITLE = "默认"

    /** 全部会话（Compose 状态列表，用于标签页 UI） */
    private val _sessions = mutableStateListOf<TerminalSession>()
    val sessions: List<TerminalSession> get() = _sessions

    /** 当前活动会话 ID */
    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    // 全局别名（指向当前活动会话）
    private val _outputFlow = MutableStateFlow("")
    val outputFlow: StateFlow<String> = _outputFlow.asStateFlow()
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private val _lastExitCode = MutableStateFlow<Int?>(null)
    val lastExitCode: StateFlow<Int?> = _lastExitCode.asStateFlow()
    private val _wasCancelled = MutableStateFlow(false)
    val wasCancelled: StateFlow<Boolean> = _wasCancelled.asStateFlow()

    private var nextId = 1L

    /** 用户会话标题计数器，用于生成 "终端 #n" */
    private var sessionTitleCounter = 0

    /** 工具页面本地指令的会话路由模式 */
    var toolCommandSessionMode: ToolCommandSessionMode = ToolCommandSessionMode.NEW
        private set

    /** 默认会话 ID（供 UI 判断是否为默认会话） */
    val defaultSessionId: Long get() = DEFAULT_SESSION_ID

    /** 判断指定会话是否为默认会话 */
    fun isDefaultSession(id: Long): Boolean = id == DEFAULT_SESSION_ID
    
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
     * 设置工具页面本地指令的会话路由模式。
     * 切换到"每次新建会话"模式时，立即释放默认会话（若存在）。
     */
    fun setToolCommandSessionMode(mode: ToolCommandSessionMode) {
        toolCommandSessionMode = mode
        if (mode == ToolCommandSessionMode.NEW) {
            releaseDefaultSession()
        }
    }

    /**
     * 释放（移除）默认会话。
     * 若默认会话是活动会话，则切换到其它会话；若这是最后一个会话则回到空状态。
     */
    private fun releaseDefaultSession() {
        val default = _sessions.find { it.id == DEFAULT_SESSION_ID } ?: return
        val wasActive = _activeSessionId.value == DEFAULT_SESSION_ID
        stopProcess(default)
        _sessions.remove(default)
        if (wasActive) {
            val next = _sessions.lastOrNull()
            if (next != null) {
                setActiveSession(next.id)
            } else {
                _activeSessionId.value = null
                _outputFlow.value = ""
                _isRunning.value = false
                _lastExitCode.value = null
                _wasCancelled.value = false
            }
        }
    }
    
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
     * 获取默认会话，若不存在则创建（默认会话不可关闭）。
     */
    fun getOrCreateDefaultSession(): TerminalSession {
        _sessions.find { it.id == DEFAULT_SESSION_ID }?.let { return it }
        val s = TerminalSession(DEFAULT_SESSION_ID, DEFAULT_SESSION_TITLE)
        _sessions.add(s)
        return s
    }

    /**
     * 新建一个会话并返回。
     * 未指定标题时自动生成 "终端 #n"（n 从 0 递增）。
     */
    fun createSession(title: String? = null): TerminalSession {
        val finalTitle = title ?: "终端 #${sessionTitleCounter++}"
        val s = TerminalSession(nextId++, finalTitle)
        _sessions.add(s)
        return s
    }

    /**
     * 新建一个会话并立即设为活动会话。
     */
    fun createAndActivateSession(title: String? = null): Long {
        val s = createSession(title)
        setActiveSession(s.id)
        return s.id
    }

    /**
     * 获取指定 ID 的会话；不存在时返回 null。
     */
    fun getSession(id: Long): TerminalSession? = _sessions.find { it.id == id }

    /**
     * 设置活动会话，并将全局别名指向该会话。
     */
    fun setActiveSession(id: Long) {
        _activeSessionId.value = id
        val s = getSession(id)
        _outputFlow.value = s?.output?.value ?: ""
        _isRunning.value = s?.isRunning?.value ?: false
        _lastExitCode.value = s?.lastExitCode?.value
        _wasCancelled.value = s?.wasCancelled?.value ?: false
    }

    /**
     * 关闭指定会话（默认会话不可关闭）。
     * 若关闭的是活动会话，则自动切换到其它会话；若这是最后一个会话则回到空状态。
     */
    fun closeSession(id: Long) {
        if (id == DEFAULT_SESSION_ID) return
        val s = getSession(id) ?: return
        val wasActive = _activeSessionId.value == id
        stopProcess(s)
        _sessions.remove(s)
        if (wasActive) {
            val next = _sessions.lastOrNull()
            if (next != null) {
                setActiveSession(next.id)
            } else {
                // 无剩余会话：回到空状态（终端页显示空状态占位）
                _activeSessionId.value = null
                _outputFlow.value = ""
                _isRunning.value = false
                _lastExitCode.value = null
                _wasCancelled.value = false
            }
        }
    }

    /**
     * 关闭全部会话并回到空状态（清空所有标签页）。
     * 标题编号同时复位，下一次新建从 "终端 #0" 重新开始。
     */
    fun clearAllSessions() {
        _sessions.toList().forEach { stopProcess(it) }
        _sessions.clear()
        _activeSessionId.value = null
        _outputFlow.value = ""
        _isRunning.value = false
        _lastExitCode.value = null
        _wasCancelled.value = false
        sessionTitleCounter = 0
    }

    /**
     * 根据工具会话路由模式，决定一次非用户指令应落在哪个会话，并确保该会话存在。
     */
    private fun routeNonUserSession(): TerminalSession {
        return when (toolCommandSessionMode) {
            ToolCommandSessionMode.NEW -> createSession()
            ToolCommandSessionMode.DEFAULT -> getOrCreateDefaultSession()
        }
    }

    /**
     * 启动指定会话的交互式 Shell
     */
    private fun startShellSession(s: TerminalSession) {
        if (s.process?.isAlive == true) return

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

            s.process = process
            // 使用用户设置的编码
            val processCharset = getCharset()
            s.writer = OutputStreamWriter(process.outputStream, processCharset)
            setRunning(s, true)

            addOutput(s, "=== 终端会话已启动 ($currentEncodingName) ===\n")

            // 启动输出读取协程
            s.shellJob = CoroutineScope(Dispatchers.IO).launch {
                val reader = BufferedReader(InputStreamReader(process.inputStream, processCharset))
                try {
                    val buffer = CharArray(4096)
                    var charsRead: Int
                    while (isActive && process.isAlive) {
                        charsRead = reader.read(buffer, 0, buffer.size)
                        if (charsRead > 0) {
                            val text = String(buffer, 0, charsRead)
                            addOutput(s, text)
                        } else if (charsRead == -1) {
                            break
                        }
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        addOutput(s, "\n[终端输出读取结束]\n")
                    }
                } finally {
                    reader.close()
                }
            }

            // 等待进程结束
            CoroutineScope(Dispatchers.IO).launch {
                val exitCode = process.waitFor()
                s.shellJob?.join()
                addOutput(s, "\n=== 终端会话已结束，退出码: $exitCode ===\n")
                setRunning(s, false)
                s.process = null
                s.writer = null
            }
        } catch (e: Exception) {
            addOutput(s, "启动终端会话时出错: ${e.message}\n")
            setRunning(s, false)
        }
    }

    /**
     * 执行命令（发送到指定会话的交互式 Shell）
     * 注意：此方法仅用于交互式终端，不适合需要等待命令完成的场景
     */
    fun executeCommand(sessionId: Long, command: String): Boolean {
        val s = getSession(sessionId) ?: return false
        return try {
            // 如果 Shell 未运行，先启动
            if (s.process?.isAlive != true) {
                startShellSession(s)
                // 等待 Shell 启动
                Thread.sleep(200)
            }

            // 发送命令到 Shell
            val writer = s.writer
            if (writer != null && s.process?.isAlive == true) {
                writer.write(command)
                writer.write("\n")
                writer.flush()
                true
            } else {
                addOutput(s, "终端未连接\n")
                false
            }
        } catch (e: Exception) {
            addOutput(s, "发送命令时出错: ${e.message}\n")
            false
        }
    }

    /**
     * 在活动会话中执行命令（兼容旧调用方）。
     */
    fun executeCommand(command: String): Boolean {
        val id = _activeSessionId.value ?: return false
        return executeCommand(id, command)
    }
    
    /**
     * 执行命令并等待完成（直接启动进程，不通过交互式 Shell）。
     * 按 [toolCommandSessionMode] 路由到新会话或默认会话，并返回所用的会话 ID。
     * @param workingDirectory 进程工作目录；null 时使用当前 JVM 工作目录
     */
    fun executeCommandAndWait(command: String, workingDirectory: String? = null): Long {
        val target = routeNonUserSession()
        executeCommandAndWait(target.id, command, workingDirectory)
        return target.id
    }

    /**
     * 在指定会话中执行命令并等待完成。
     */
    fun executeCommandAndWait(sessionId: Long, command: String, workingDirectory: String? = null) {
        val s = getSession(sessionId) ?: return
        // 使全局别名指向该会话（便于安装对话框 / 导航到终端时显示）
        setActiveSession(sessionId)

        // 取消该会话之前的执行任务
        s.commandJob?.cancel()

        // 清空该会话之前的输出和状态
        clearOutput(sessionId)
        s.wasCancelled.value = false
        s.lastExitCode.value = null

        s.commandJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                setRunning(s, true)

                // 根据平台构建命令
                val cmdArray = if (isWindows) {
                    // Windows 上根据用户选择的编码设置控制台代码页
                    arrayOf("cmd.exe", "/c", "${getCodePageCommand()}$command")
                } else {
                    arrayOf("sh", "-c", command)
                }

                val processBuilder = ProcessBuilder(*cmdArray)
                processBuilder.redirectErrorStream(true)
                // 设置工作目录，使被启动程序能正确定位其相对路径资源
                if (workingDirectory != null) {
                    processBuilder.directory(java.io.File(workingDirectory))
                }
                val process = processBuilder.start()
                s.process = process

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
                            addOutput(s, text)
                        } else if (charsRead == -1) {
                            break
                        }
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        addOutput(s, "\n[输出读取结束]\n")
                    }
                } finally {
                    reader.close()
                }

                // 等待进程结束
                val exitCode = process.waitFor()
                setLastExitCode(s, exitCode)
                addOutput(s, "\n=== 进程已结束，退出码: $exitCode ===\n")

            } catch (e: Exception) {
                addOutput(s, "执行命令时出错: ${e.message}\n")
                setLastExitCode(s, -1)
            } finally {
                setRunning(s, false)
                s.process = null
                s.writer = null
            }
        }
    }
    
    /**
     * 停止指定会话的进程
     * 使用 taskkill（Windows）或 kill（Linux）直接终止进程
     */
    private fun stopProcess(s: TerminalSession) {
        s.wasCancelled.value = true

        // 取消执行任务
        s.commandJob?.cancel()

        // 如果进程仍在运行，使用平台原生方式终止
        s.process?.let { process ->
            if (process.isAlive) {
                try {
                    // 先尝试获取进程 PID 并使用 taskkill/kill
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
                            addOutput(s, "\n正在终止进程 (PID: $pid)...\n")
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

        s.shellJob?.cancel()
        s.process = null
        s.writer = null
        setRunning(s, false)
    }

    /**
     * 停止当前活动会话的进程（兼容旧调用方，例如安装对话框）。
     */
    fun stopCurrentProcess() {
        val id = _activeSessionId.value ?: return
        getSession(id)?.let { stopProcess(it) }
    }

    /**
     * 添加输出到指定会话的缓冲区
     */
    private fun addOutput(s: TerminalSession, text: String) {
        s.outputBuffer.add(text)
        if (s.outputBuffer.size > 2000) { // 限制缓冲区大小
            s.outputBuffer.poll()
        }
        val joined = s.outputBuffer.joinToString("")
        s.output.value = joined
        if (s.id == _activeSessionId.value) {
            _outputFlow.value = joined
        }
    }

    /**
     * 清空指定会话的输出
     */
    fun clearOutput(sessionId: Long) {
        val s = getSession(sessionId) ?: return
        s.outputBuffer.clear()
        s.output.value = ""
        if (s.id == _activeSessionId.value) {
            _outputFlow.value = ""
        }
    }

    /**
     * 获取指定会话的输出
     */
    fun getOutput(sessionId: Long): String = getSession(sessionId)?.outputBuffer?.joinToString("") ?: ""

    /**
     * 获取指定会话最后N行输出
     */
    fun getLastLines(sessionId: Long, n: Int): String {
        val s = getSession(sessionId) ?: return ""
        val lines = s.outputBuffer.joinToString("").lines()
        return lines.takeLast(n).joinToString("\n")
    }

    private fun setRunning(s: TerminalSession, running: Boolean) {
        s.isRunning.value = running
        if (s.id == _activeSessionId.value) {
            _isRunning.value = running
        }
    }

    private fun setLastExitCode(s: TerminalSession, code: Int?) {
        s.lastExitCode.value = code
        if (s.id == _activeSessionId.value) {
            _lastExitCode.value = code
        }
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
