// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 终端编码选项
 */
enum class TerminalEncoding(val displayName: String, val charsetName: String) {
    ANSI_GBK("GBK", "GBK"),
    UTF8("UTF-8", "UTF-8"),
    UTF16("UTF-16", "UTF-16");

    companion object {
        fun fromName(name: String): TerminalEncoding = entries.find { it.charsetName == name } ?: UTF8
    }
}

/**
 * 工具页面执行本地指令时的终端会话路由模式
 */
enum class ToolCommandSessionMode(val displayName: String) {
    /** 每次执行都新建一个会话 */
    NEW("每次新建会话"),
    /** 所有非用户发起的指令都重定向到同一个"默认"会话 */
    DEFAULT("使用默认会话");

    companion object {
        fun fromName(name: String): ToolCommandSessionMode = entries.find { it.name == name } ?: NEW
    }
}

data class AppConfig(
    val dark: Boolean = false, 
    val color: String? = null, 
    val useProxy: Boolean = false,
    val proxyUrl: String = "https://gh-proxy.com",
    val terminalEncoding: String = "UTF-8",
    val displayName: String? = null,
    // 壁纸相关：不获取桌面壁纸（Linux 上此值不影响显示，始终视为开启）
    val useCustomBg: Boolean = false,
    // 用户自选的本地背景文件名（存放在程序目录 /cardbg/ 下）
    val customBgFile: String? = null,
    // 工具页面本地指令的终端会话模式（NEW=每次新建 / DEFAULT=使用默认会话）
    val toolCommandSession: String = "NEW",
    // 终端进程结束后是否立即结束会话（false=等待用户按回车关闭）
    val closeSessionOnEnd: Boolean = false
)

private val configPath: Path = Path.of("config", "conf.toml")

fun loadConfig(): AppConfig {
    return try {
        if (!Files.exists(configPath)) return AppConfig()
        val lines = Files.readAllLines(configPath)
        var dark: Boolean = false
        var color: String? = null
        var useProxy: Boolean = false
        var proxyUrl: String = "https://gh-proxy.com"
        var terminalEncoding: String = "UTF-8"
        var displayName: String? = null
        var useCustomBg: Boolean = false
        var customBgFile: String? = null
        var toolCommandSession: String = "NEW"
        var closeSessionOnEnd: Boolean = false
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("#") || line.isEmpty()) continue
            if (line.startsWith("dark")) {
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) {
                    dark = parts[1].trim().lowercase() == "true"
                }
            } else if (line.startsWith("color")) {
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) {
                    var v = parts[1].trim()
                    if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length - 1)
                    if (v.isNotBlank()) color = v
                }
            } else if (line.startsWith("use_proxy")) {
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) {
                    useProxy = parts[1].trim().lowercase() == "true"
                }
            } else if (line.startsWith("proxy_url")) {
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) {
                    var v = parts[1].trim()
                    if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length - 1)
                    if (v.isNotBlank()) proxyUrl = v
                }
            } else if (line.startsWith("terminal_encoding")) {
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) {
                    var v = parts[1].trim()
                    if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length - 1)
                    if (v.isNotBlank()) terminalEncoding = v
                }
            } else if (line.startsWith("display_name")) {
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) {
                    var v = parts[1].trim()
                    if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length - 1)
                    if (v.isNotBlank()) displayName = v
                }
            } else if (line.startsWith("use_custom_bg")) {
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) {
                    useCustomBg = parts[1].trim().lowercase() == "true"
                }
            } else if (line.startsWith("custom_bg_file")) {
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) {
                    var v = parts[1].trim()
                    if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length - 1)
                    if (v.isNotBlank()) customBgFile = v
                }
            } else if (line.startsWith("tool_command_session")) {
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) {
                    var v = parts[1].trim()
                    if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length - 1)
                    if (v.isNotBlank()) toolCommandSession = v
                }
            } else if (line.startsWith("close_session_on_end")) {
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) {
                    closeSessionOnEnd = parts[1].trim().lowercase() == "true"
                }
            }
        }
        AppConfig(dark = dark, color = color, useProxy = useProxy, proxyUrl = proxyUrl, terminalEncoding = terminalEncoding, displayName = displayName, useCustomBg = useCustomBg, customBgFile = customBgFile, toolCommandSession = toolCommandSession, closeSessionOnEnd = closeSessionOnEnd)
    } catch (e: Exception) {
        AppConfig()
    }
}

fun saveConfig(cfg: AppConfig) {
    try {
        val dir = configPath.parent
        if (dir != null) Files.createDirectories(dir)
        val tmp = dir.resolve("conf.toml.tmp")
        val sb = StringBuilder()
        sb.append("# Generated by NOT Toolbox\n")
        sb.append("dark = ").append(if (cfg.dark) "true" else "false").append('\n')
        sb.append("color = ")
        if (cfg.color != null && cfg.color.isNotBlank()) {
            sb.append('"').append(cfg.color).append('"')
        } else {
            sb.append("\"\"")
        }
        sb.append('\n')
        sb.append("use_proxy = ").append(if (cfg.useProxy) "true" else "false").append('\n')
        sb.append("proxy_url = \"").append(cfg.proxyUrl).append("\"\n")
        sb.append("terminal_encoding = \"").append(cfg.terminalEncoding).append("\"\n")
        sb.append("display_name = ")
        if (cfg.displayName != null && cfg.displayName.isNotBlank()) {
            sb.append('"').append(cfg.displayName).append('"')
        } else {
            sb.append("\"\"")
        }
        sb.append('\n')
        sb.append("use_custom_bg = ").append(if (cfg.useCustomBg) "true" else "false").append('\n')
        sb.append("custom_bg_file = ")
        if (cfg.customBgFile != null && cfg.customBgFile.isNotBlank()) {
            sb.append('"').append(cfg.customBgFile).append('"')
        } else {
            sb.append("\"\"")
        }
        sb.append('\n')
        sb.append("tool_command_session = \"").append(cfg.toolCommandSession).append("\"\n")
        sb.append("close_session_on_end = ").append(if (cfg.closeSessionOnEnd) "true" else "false").append('\n')
        Files.writeString(tmp, sb.toString())
        Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: Exception) {
        // ignore write errors for now
    }
}
