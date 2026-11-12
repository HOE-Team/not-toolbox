// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** 包管理器搜索结果中的一条记录。 */
data class PackageSearchResult(
    val name: String,
    val packageName: String,
    val version: String? = null,
    val description: String? = null
)

sealed interface PackageSearchOutcome {
    data class Success(val results: List<PackageSearchResult>) : PackageSearchOutcome
    data class Failed(val message: String) : PackageSearchOutcome
    data object Unsupported : PackageSearchOutcome
}

/**
 * 调用包管理器自身的搜索命令，而不是从内置推荐列表里过滤。
 *
 * 只负责“搜索并解析”，安装/卸载仍然走 PackageManagerUtils 的既有命令。
 */
object PackageSearch {
    private const val MAX_RESULTS = 200
    private const val SEARCH_TIMEOUT_SECONDS = 45L

    suspend fun search(manager: PackageManagerType, query: String): PackageSearchOutcome =
        withContext(Dispatchers.IO) {
            val command = PackageManagerUtils.getSearchCommand(manager, query)
            if (command == null) {
                return@withContext if (query.isBlank()) {
                    PackageSearchOutcome.Success(emptyList())
                } else {
                    PackageSearchOutcome.Unsupported
                }
            }

            val result = runSearchCommand(command)
                ?: return@withContext PackageSearchOutcome.Failed("无法启动包管理器搜索命令。")

            if (result.output.isBlank()) {
                return@withContext if (result.exitCode == 0) {
                    PackageSearchOutcome.Success(emptyList())
                } else {
                    PackageSearchOutcome.Failed("包管理器搜索失败（退出码 ${result.exitCode}）。")
                }
            }

            val results = parse(manager, result.output)
            if (results.isEmpty() && result.exitCode != 0) {
                PackageSearchOutcome.Failed("包管理器搜索失败（退出码 ${result.exitCode}）。")
            } else {
                PackageSearchOutcome.Success(results)
            }
        }

    private data class CommandResult(val exitCode: Int, val output: String)

    private fun runSearchCommand(command: String): CommandResult? {
        return try {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val cmdArray = if (isWindows) {
                arrayOf("cmd.exe", "/c", command)
            } else {
                arrayOf("sh", "-c", command)
            }
            val process = ProcessBuilder(*cmdArray)
                .redirectErrorStream(true)
                .start()

            val outputFuture = CompletableFuture<String>()
            Thread {
                try {
                    outputFuture.complete(
                        process.inputStream.bufferedReader(Charset.defaultCharset()).use { it.readText() }
                    )
                } catch (_: Exception) {
                    outputFuture.complete("")
                }
            }.apply {
                isDaemon = true
                start()
            }

            val finished = process.waitFor(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
            }
            val output = runCatching { outputFuture.get(5, TimeUnit.SECONDS) }.getOrDefault("")
            CommandResult(if (finished) process.exitValue() else -1, output)
        } catch (_: Exception) {
            null
        }
    }

    private fun parse(manager: PackageManagerType, raw: String): List<PackageSearchResult> {
        val clean = raw.replace(Regex("\\u001B\\[[;\\d]*m"), "")
        val parsed = when (manager) {
            PackageManagerType.WINGET -> parseWinget(clean)
            PackageManagerType.APT -> parseApt(clean)
            PackageManagerType.DNF -> parseColonTable(clean)
            PackageManagerType.PACMAN -> parsePacman(clean)
            PackageManagerType.ZYPPER -> parseZypper(clean)
            PackageManagerType.EMERGE -> parseEmerge(clean)
            PackageManagerType.NIX -> parseNix(clean)
            PackageManagerType.SCOOP -> parseScoop(clean)
            PackageManagerType.CHOCOLATEY -> parseChoco(clean)
            PackageManagerType.UNKNOWN -> emptyList()
        }
        return parsed
            .filter { it.packageName.isNotBlank() && it.name.isNotBlank() }
            .distinctBy { it.packageName.lowercase() }
            .take(MAX_RESULTS)
    }

    /** winget 的表格按列对齐；用表头 ID 的起始位置切分名称列。 */
    private fun parseWinget(output: String): List<PackageSearchResult> {
        val lines = output.lines()
        val headerIndex = lines.indexOfFirst {
            it.contains("Id", ignoreCase = true) &&
                (it.contains("Name", ignoreCase = true) || it.contains("名称"))
        }
        if (headerIndex < 0) return parseGeneric(output)

        val header = lines[headerIndex]
        val idStart = header.indexOf("Id", ignoreCase = true)
        if (idStart <= 0) return parseGeneric(output)

        return lines.drop(headerIndex + 1).mapNotNull { raw ->
            if (raw.isBlank() || raw.trim().startsWith("-")) return@mapNotNull null
            if (raw.length <= idStart) return@mapNotNull null

            val name = raw.take(idStart).trim()
            if (name.isBlank()) return@mapNotNull null

            val tail = raw.substring(idStart).trim()
            val parts = tail.split(Regex("\\s{2,}")).filter { it.isNotBlank() }
            val packageName = parts.firstOrNull()?.trim().orEmpty()
            if (packageName.isBlank() || !packageName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]*"))) {
                return@mapNotNull null
            }

            PackageSearchResult(
                name = name,
                packageName = packageName,
                version = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() },
                description = parts.lastOrNull()?.trim()?.let { "来源：$it" }
            )
        }
    }

    private fun parseApt(output: String): List<PackageSearchResult> {
        val results = mutableListOf<PackageSearchResult>()
        for (raw in output.lines()) {
            val line = raw.trimEnd()
            if (line.isBlank() || line.startsWith("WARNING") || line.startsWith("E:") || line.startsWith("N:")) {
                continue
            }
            if (line.startsWith(" ") || line.startsWith("\t")) {
                val last = results.lastOrNull() ?: continue
                if (line.trim().isNotEmpty()) {
                    results[results.lastIndex] = last.copy(
                        description = listOfNotNull(last.description, line.trim()).joinToString(" ")
                    )
                }
                continue
            }
            val parts = line.split(" - ", limit = 2)
            val name = parts[0].trim()
            if (name.isBlank()) continue
            results += PackageSearchResult(
                name = name,
                packageName = name,
                description = parts.getOrNull(1)?.trim()
            )
        }
        return results
    }

    /** DNF 的 `name.arch : summary`。 */
    private fun parseColonTable(output: String): List<PackageSearchResult> =
        output.lines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isBlank() || !line.contains(" : ")) return@mapNotNull null
            val parts = line.split(" : ", limit = 2)
            val packageName = parts[0]
                .trim()
                .replace(Regex("\\.(x86_64|i686|noarch|aarch64)$"), "")
            if (packageName.isBlank()) return@mapNotNull null
            PackageSearchResult(
                name = packageName,
                packageName = packageName,
                description = parts.getOrNull(1)?.trim()
            )
        }

    private fun parsePacman(output: String): List<PackageSearchResult> {
        val results = mutableListOf<PackageSearchResult>()
        for (raw in output.lines()) {
            if (raw.isBlank()) continue
            if (raw.startsWith(" ") || raw.startsWith("\t")) {
                val last = results.lastOrNull() ?: continue
                results[results.lastIndex] = last.copy(description = raw.trim())
                continue
            }
            val tokens = raw.trim().split(Regex("\\s+"))
            val repoName = tokens.getOrNull(0) ?: continue
            if (!repoName.contains("/")) continue
            val packageName = repoName.substringAfterLast('/')
            if (packageName.isBlank()) continue
            results += PackageSearchResult(
                name = packageName,
                packageName = packageName,
                version = tokens.getOrNull(1)
            )
        }
        return results
    }

    private fun parseZypper(output: String): List<PackageSearchResult> =
        output.lines().mapNotNull { raw ->
            val parts = raw.split('|').map { it.trim() }
            if (parts.size < 3) return@mapNotNull null
            val name = parts[1]
            if (name.isBlank() || name.equals("Name", true) || name == "名称") return@mapNotNull null
            PackageSearchResult(
                name = name,
                packageName = name,
                description = parts[2].takeIf { it.isNotBlank() }
            )
        }

    private fun parseEmerge(output: String): List<PackageSearchResult> {
        val results = mutableListOf<PackageSearchResult>()
        for (raw in output.lines()) {
            val line = raw.trim()
            if (line.startsWith("*")) {
                val name = line.removePrefix("*").trim()
                if (name.isNotBlank()) {
                    results += PackageSearchResult(name = name, packageName = name)
                }
            } else if (raw.startsWith(" ") || raw.startsWith("\t")) {
                val last = results.lastOrNull() ?: continue
                if (line.isNotBlank()) results[results.lastIndex] = last.copy(description = line)
            }
        }
        return results
    }

    private fun parseNix(output: String): List<PackageSearchResult> =
        output.lines().mapNotNull { raw ->
            val tokens = raw.trim().split(Regex("\\s+"))
            val packageName = tokens.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PackageSearchResult(
                name = packageName,
                packageName = packageName,
                description = tokens.drop(1).joinToString(" ").takeIf { it.isNotBlank() }
            )
        }

    private fun parseScoop(output: String): List<PackageSearchResult> =
        output.lines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("Results from")) return@mapNotNull null
            val name = line.split(Regex("\\s+")).firstOrNull()
                ?.substringBefore("(")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            PackageSearchResult(name = name, packageName = name)
        }

    private fun parseChoco(output: String): List<PackageSearchResult> =
        output.lines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("Chocolatey")) return@mapNotNull null
            val tokens = line.split(Regex("\\s+"))
            val name = tokens.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PackageSearchResult(
                name = name,
                packageName = name,
                version = tokens.getOrNull(1)
            )
        }

    private fun parseGeneric(output: String): List<PackageSearchResult> =
        output.lines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("-")) return@mapNotNull null
            val name = line.split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            PackageSearchResult(name = name, packageName = name)
        }
}
