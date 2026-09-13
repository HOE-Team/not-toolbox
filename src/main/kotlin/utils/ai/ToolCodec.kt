// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 工具调用标记的解析与流式处理（移植自 OpenDroidChat 的 McpClient 静态方法）

package utils.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 工具调用标记：模型需输出 [TOOL_CALL]{"tool":"x","arguments":{}} */
const val TOOL_CALL_MARKER = "[TOOL_CALL]"

/** 标记内部关键字，用于大小写与括号形态都兼容的宽松识别 */
private const val MARKER_KEYWORD = "TOOL_CALL"

object ToolCodec {
    private val parseJson = Json { ignoreUnknownKeys = true }

    /** 需要从可见正文中剔除的标记区域 */
    private data class Span(
        val start: Int,
        val endExclusive: Int,
        val payload: String,
        /** true 表示花括号闭合（即完整的块）；false 表示截断/无参的残留标记 */
        val complete: Boolean
    )

    /**
     * 宽松查找标记起点：兼容 `[TOOL_CALL]`、`【TOOL_CALL】`、小写与多括号（如历史上提示词写错产生的 `[[TOOL_CALL]]`）。
     * 返回标记左括号所在下标；找不到返回 -1。
     */
    private fun indexOfMarker(text: String, from: Int): Int {
        var i = from
        while (i < text.length) {
            val open = text[i]
            val close = when (open) {
                '[' -> ']'
                '【' -> '】'
                '〔' -> '〕'
                else -> null
            }
            if (close != null) {
                val inner = i + 1
                val end = inner + MARKER_KEYWORD.length
                if (end < text.length &&
                    text.regionMatches(inner, MARKER_KEYWORD, 0, MARKER_KEYWORD.length, ignoreCase = true) &&
                    text[end] == close
                ) {
                    return i
                }
            }
            i++
        }
        return -1
    }

    /** 标记右括号之后的下标（用于定位后面的 JSON） */
    private fun markerEnd(text: String, markerStart: Int): Int {
        val close = when (text[markerStart]) {
            '[' -> ']'
            '【' -> '】'
            else -> '〕'
        }
        val idx = text.indexOf(close, markerStart + 1 + MARKER_KEYWORD.length)
        return if (idx < 0) text.length else idx + 1
    }

    /** 从 braceStart 处的 `{` 找到配对的 `}`（正确处理字符串与转义）；找不到返回 -1 */
    private fun matchBrace(text: String, braceStart: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (j in braceStart until text.length) {
            val c = text[j]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                if (c == '"') {
                    inString = true
                } else if (c == '{') {
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0) return j
                }
            }
        }
        return -1
    }

    /** 扫描全部标记区域（含花括号未闭合、无参数的残留），并按需把包裹它的代码围栏一并纳入 */
    private fun scan(text: String): List<Span> {
        val raw = mutableListOf<Span>()
        var searchFrom = 0
        while (true) {
            var start = indexOfMarker(text, searchFrom)
            if (start < 0) break
            // 兼容「[[TOOL_CALL]]」这类多出来的左括号
            val openChar = text[start]
            while (start > searchFrom && text[start - 1] == openChar) start--

            var cursor = markerEnd(text, start)
            // 兼容「[[TOOL_CALL]]」这类多出来的右括号
            while (cursor < text.length && (text[cursor] == ']' || text[cursor] == '】')) cursor++

            var braceStart = cursor
            while (braceStart < text.length && text[braceStart].isWhitespace()) braceStart++

            if (braceStart >= text.length || text[braceStart] != '{') {
                // 标记后面没有 JSON：整行剔除
                val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
                raw += Span(start, lineEnd, text.substring(start, lineEnd).trim(), false)
                searchFrom = lineEnd
                continue
            }

            val end = matchBrace(text, braceStart)
            if (end < 0) {
                // 花括号未闭合（截断输出）：剔除到文末
                raw += Span(start, text.length, text.substring(start).trim(), false)
                break
            }
            raw += Span(start, end + 1, text.substring(start, end + 1).trim(), true)
            searchFrom = end + 1
        }
        return raw.map { expandFences(text, it) }
    }

    private fun lineStartIndex(text: String, index: Int): Int =
        text.lastIndexOf('\n', (index - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }

    private fun lineEndIndex(text: String, index: Int): Int =
        text.indexOf('\n', index.coerceIn(0, text.length)).let { if (it < 0) text.length else it }

    /**
     * 把标记区域收拾干净：
     * 1. 上一行/下一行是 ``` 代码围栏（模型常见错误）时，把围栏一并纳入剔除范围
     * 2. 标记独占整行时，连行尾换行一起剔除，避免正文里留下空行
     * 3. 标记夹在一行文字中间时保持原样，只剔除标记本身
     */
    private fun expandFences(text: String, span: Span): Span {
        var start = span.start
        var end = span.endExclusive

        val markerLineStart = lineStartIndex(text, span.start)
        if (markerLineStart > 0) {
            val prevLineStart = lineStartIndex(text, markerLineStart - 1)
            val prevLine = text.substring(prevLineStart, markerLineStart - 1).trim()
            if (prevLine.startsWith("```")) start = prevLineStart
        }

        val markerLineEnd = lineEndIndex(text, span.endExclusive)
        if (markerLineEnd < text.length) {
            val nextLineStart = markerLineEnd + 1
            val nextLineEnd = lineEndIndex(text, nextLineStart)
            if (text.substring(nextLineStart, nextLineEnd).trim().startsWith("```")) end = nextLineEnd
        }

        val before = text.substring(lineStartIndex(text, start), start)
        val after = text.substring(end.coerceAtMost(text.length), lineEndIndex(text, end))
        if (before.isBlank() && after.isBlank()) {
            start = lineStartIndex(text, start)
            val lineEnd = lineEndIndex(text, end)
            end = if (lineEnd < text.length) lineEnd + 1 else lineEnd
        }

        return span.copy(start = start, endExclusive = end)
    }

    /** 从模型输出中解析出工具调用列表（仅接受花括号闭合且 JSON 合法的块） */
    fun parseCalls(text: String): List<ToolCall> =
        scan(text).filter { it.complete }.mapNotNull { toCall(it.payload) }

    /**
     * 无法解析成调用的标记原文（截断、缺参数、JSON 非法等）。
     * 这些内容同样会从可见正文中剔除，交由界面放进工具调用容器展示，避免裸奔在气泡里。
     */
    fun unparsedCalls(text: String): List<String> =
        scan(text).filter { span -> !span.complete || toCall(span.payload) == null }.map { it.payload }

    /** 把一个标记块解析成工具调用；块内任意位置的第一个 JSON 对象即参数体 */
    private fun toCall(block: String): ToolCall? {
        val braceStart = block.indexOf('{')
        if (braceStart < 0) return null
        val obj = try {
            parseJson.parseToJsonElement(block.substring(braceStart)).jsonObject
        } catch (_: Exception) {
            return null
        }
        val tool = obj["tool"]?.jsonPrimitive?.contentOrNull ?: return null
        val args: Map<String, JsonElement> = (obj["arguments"] as? JsonObject)?.let { jo ->
            jo.entries.associate { entry -> entry.key to entry.value }
        } ?: emptyMap()
        return ToolCall(tool = tool, arguments = args)
    }

    /**
     * 流式期间用：列出「已经开始出现」的工具调用名（含花括号尚未闭合的块）。
     * 界面据此立刻创建工具调用容器并转圈，不必等整段输出结束。
     */
    fun pendingCallNames(text: String): List<String> {
        val names = mutableListOf<String>()
        for (span in scan(text)) {
            val name = toolNameOf(span.payload)
            if (!name.isNullOrBlank()) names += name
        }
        return names
    }

    /** 从标记块里尽力提取工具名：JSON 完整时正常解析，不完整时用正则兜底 */
    private fun toolNameOf(payload: String): String? {
        toCall(payload)?.let { return it.tool }
        return Regex("\"tool\"\\s*:\\s*\"([^\"\\\\]{1,64})\"").find(payload)?.groupValues?.get(1)
    }

    /** 去掉正文中所有标记痕迹（含未闭合、无参数、被代码围栏包裹的形态），仅保留正文 */
    fun stripCalls(text: String): String {
        val spans = scan(text)
        if (spans.isEmpty()) return text.trim()
        val sb = StringBuilder(text.length)
        var cursor = 0
        for (span in spans) {
            if (span.start > cursor) sb.append(text, cursor, span.start)
            cursor = maxOf(cursor, span.endExclusive)
        }
        if (cursor < text.length) sb.append(text, cursor, text.length)
        return sb.toString().trim()
    }

    /** 流式场景：尾部可能是标记前缀时需暂存的字符数（含 [[TOOL_CALL 等多括号形态） */
    fun holdBackLength(text: String): Int {
        val tailFrom = (text.length - MARKER_KEYWORD.length - 2).coerceAtLeast(0)
        val tail = text.substring(tailFrom)
        var best = 0
        for (i in tail.indices) {
            val c = tail[i]
            if (c == '[' || c == '【' || c == '〔') {
                val inner = tail.substring(i + 1)
                if (inner.isNotEmpty() && MARKER_KEYWORD.startsWith(inner, ignoreCase = true)) {
                    // 相邻重复的左括号（如 [[TOOL_CALL）要一起暂存，否则界面会闪出孤立的 [
                    var start = i
                    while (start > 0 && tail[start - 1] == c) start--
                    val length = tail.length - start
                    if (length > best) best = length
                }
            }
        }
        // 文本刚好以左括号结尾（标记可能才写了一半）
        if (best == 0 && tail.isNotEmpty()) {
            val last = tail.last()
            if (last == '[' || last == '【' || last == '〔') return 1
        }
        return best
    }

    /**
     * 流式场景：把累积文本切成「可安全显示的正文」与「需暂存的尾部」。
     * - 已出现但尚未闭合的标记：从标记处整段暂存
     * - 尾部只写了一半的标记前缀（如 `[TOOL`）：按前缀长度暂存
     * - 标记所在行的上一行若是 ``` 代码围栏，围栏也一并暂存，避免界面先闪出一行 ```
     * 完整闭合的标记不在此处暂存，由 [stripCalls] 直接从正文中剔除。
     */
    fun splitForStreaming(text: String): Pair<String, String> {
        val openMarker = scan(text).lastOrNull { !it.complete }
        var cut = if (openMarker != null) openMarker.start else text.length - holdBackLength(text)

        if (cut > 0 && cut < text.length) {
            val lineStart = lineStartIndex(text, cut)
            if (text.substring(lineStart, cut).isBlank() && lineStart > 0) {
                val prevLineStart = lineStartIndex(text, lineStart - 1)
                if (text.substring(prevLineStart, lineStart - 1).trim().startsWith("```")) {
                    cut = prevLineStart
                }
            }
        }

        if (cut <= 0) return "" to text
        return text.substring(0, cut) to text.substring(cut)
    }
}